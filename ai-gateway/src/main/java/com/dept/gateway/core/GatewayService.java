package com.dept.gateway.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.gateway.crypto.AesGcmCipher;
import com.dept.gateway.config.GatewayProperties;
import com.dept.gateway.dto.ChatRequest;
import com.dept.gateway.dto.ChatResponse;
import com.dept.gateway.entity.AiCallLog;
import com.dept.gateway.entity.AiProviderConfig;
import com.dept.gateway.entity.SysCapability;
import com.dept.gateway.exception.GatewayException;
import com.dept.gateway.mapper.AiCallLogMapper;
import com.dept.gateway.mapper.AiProviderConfigMapper;
import com.dept.gateway.mapper.SysCapabilityMapper;
import com.dept.gateway.provider.AiProvider;
import com.dept.gateway.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 网关治理流水线编排
 * API-Key(过滤器) → 能力点启用+is_never 兜底 → 分钟限流 → 日配额 → 脱敏(仅商用)
 * → Provider 调用(重试1次→降级链→全链失败转人工) → JSON契约(重生成1次→CONTRACT_FAILED)
 * → 审计落库 → aiGenerated 标注
 *
 * <p>深度权限（身份/代理授权）由 backend 的 T-102 引擎在调用前校验，网关只兜底。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    private final AiProviderConfigMapper configMapper;
    private final AiCallLogMapper callLogMapper;
    private final SysCapabilityMapper capabilityMapper;
    private final ProviderRegistry providerRegistry;
    private final RateLimiter rateLimiter;
    private final DesensitizeService desensitizeService;
    private final ContractValidator contractValidator;
    private final AesGcmCipher cipher;
    private final GatewayProperties properties;

    /**
     * 对话主流程
     */
    public ChatResponse chat(ChatRequest request) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();

        // 1. 能力点校验（启用 + is_never 兜底）
        SysCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getCapabilityCode, request.getCapabilityCode())
                .eq(SysCapability::getStatus, 1));
        if (capability == null) {
            audit(requestId, request, null, null, 0, "PROVIDER_ERROR", "能力点未登记或已停用: " + request.getCapabilityCode());
            throw new GatewayException(403, "能力点未登记或已停用: " + request.getCapabilityCode());
        }
        if (capability.getIsNever() != null && capability.getIsNever() == 1) {
            audit(requestId, request, null, null, 0, "PROVIDER_ERROR", "永久禁止类操作（is_never=1）");
            throw new GatewayException(403, "永久禁止类操作，不可经 AI 执行");
        }

        // 2. 输入限长
        if (request.getPrompt().length() > properties.getMaxPromptChars()) {
            audit(requestId, request, null, null, 0, "PROVIDER_ERROR", "提示词超长");
            throw new GatewayException(400, "提示词超过 " + properties.getMaxPromptChars() + " 字符上限");
        }

        // 3. 启用通道按优先级排序（降级链）
        List<AiProviderConfig> chain = configMapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getEnabled, 1)
                .orderByAsc(AiProviderConfig::getPriority));
        if (chain.isEmpty()) {
            audit(requestId, request, null, null, 0, "DEGRADED", "无启用的 AI 通道");
            return degraded(requestId, request, "未配置启用的 AI 通道");
        }

        // 4. 逐通道尝试（限流/配额/调用/契约）
        String lastError = null;
        for (AiProviderConfig config : chain) {
            // 分钟限流（商用通道 35/min 用户硬性规则；触发熔断 10 分钟）
            Long waitMs = rateLimiter.tryAcquire(config.getProviderCode(), config.getRpmLimit());
            if (waitMs != null) {
                lastError = "通道 " + config.getProviderCode() + " 触发分钟限流(熔断10分钟)";
                log.warn("限流熔断: {}", lastError);
                audit(requestId, request, config, null, 0, "QUOTA_EXCEEDED", lastError);
                continue;
            }
            // 日配额
            if (config.getDailyLimit() != null && callLogMapper.countToday(config.getProviderType()) >= config.getDailyLimit()) {
                lastError = "通道 " + config.getProviderCode() + " 日配额已用尽";
                audit(requestId, request, config, null, 0, "QUOTA_EXCEEDED", lastError);
                continue;
            }
            // 脱敏（仅商用通道强制；本地/mock 不脱敏）
            String prompt = request.getPrompt();
            boolean commercial = "OPENAI_COMPAT".equals(config.getProviderType());
            if (commercial && properties.isDesensitizeEnabled()) {
                prompt = desensitizeService.desensitize(prompt);
            }
            // 解密 Key 填充（仅运行时内存，绝不落库/序列化）
            if (StringUtils.hasText(config.getApiKeyCipher())) {
                try {
                    config.setApiKeyPlain(cipher.decrypt(config.getApiKeyCipher()));
                } catch (Exception e) {
                    lastError = "通道 " + config.getProviderCode() + " API Key 解密失败";
                    audit(requestId, request, config, null, 0, "PROVIDER_ERROR", lastError);
                    continue;
                }
            }
            // 调用（失败自动重试 1 次）
            AiProvider provider = providerRegistry.get(config.getProviderType());
            AiProvider.ProviderResult result = null;
            String providerError = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    result = provider.chat(config, prompt, request.getMaxTokens() == null ? 0 : request.getMaxTokens());
                    providerError = null;
                    break;
                } catch (Exception e) {
                    providerError = "调用失败" + (attempt == 1 ? "（已自动重试）" : "");
                    log.warn("Provider 调用异常: config={}, attempt={}", config.getProviderCode(), attempt, e);
                }
            }
            if (result == null) {
                lastError = "通道 " + config.getProviderCode() + " " + providerError;
                audit(requestId, request, config, prompt, 0, "PROVIDER_ERROR", lastError);
                continue;
            }
            // JSON 契约校验（失败带错误重生成一次，再失败转人工）
            String content = result.content();
            int tokens = result.totalTokens();
            String contractError = contractValidator.validate(content, request.getJsonSchema());
            if (contractError != null) {
                log.warn("契约校验失败，重生成一次: {}", contractError);
                String retryPrompt = contractValidator.buildRetryPrompt(prompt, request.getJsonSchema(), contractError);
                try {
                    AiProvider.ProviderResult retry = provider.chat(config, retryPrompt, request.getMaxTokens() == null ? 0 : request.getMaxTokens());
                    content = retry.content();
                    tokens = retry.totalTokens();
                    contractError = contractValidator.validate(content, request.getJsonSchema());
                } catch (Exception e) {
                    contractError = "重生成调用失败";
                }
                if (contractError != null) {
                    audit(requestId, request, config, prompt, tokens, "CONTRACT_FAILED", "输出两次未通过契约校验: " + contractError);
                    throw new GatewayException(502, "AI 输出未通过契约校验，请转人工处理");
                }
            }
            // 成功审计
            audit(requestId, request, config, prompt, tokens,
                    "MOCK".equals(config.getProviderType()) ? "MOCK" : "SUCCESS", null);
            return new ChatResponse(requestId, request.getCapabilityCode(), true,
                    Map.of("type", config.getProviderType(),
                            "model", StringUtils.hasText(config.getModel()) ? config.getModel() : ""),
                    content,
                    Map.of("totalTokens", tokens, "durationMs", System.currentTimeMillis() - start),
                    false);
        }

        // 5. 全链失败 → 人工模式（不阻塞业务）
        audit(requestId, request, null, null, 0, "DEGRADED", lastError);
        return degraded(requestId, request, lastError);
    }

    private ChatResponse degraded(String requestId, ChatRequest request, String reason) {
        log.warn("AI 全链降级转人工: requestId={}, capability={}, reason={}", requestId, request.getCapabilityCode(), reason);
        return new ChatResponse(requestId, request.getCapabilityCode(), true,
                Map.of("type", "DEGRADED", "model", ""),
                properties.getDegradedMessage(),
                Map.of("totalTokens", 0, "durationMs", 0),
                true);
    }

    private void audit(String requestId, ChatRequest request, AiProviderConfig config,
                       String prompt, int tokens, String status, String error) {
        try {
            AiCallLog entry = new AiCallLog();
            // 同一请求会逐通道尝试，每条审计记录用 请求ID+通道 定位，避免唯一键冲突
            entry.setRequestId(config == null ? requestId + "-sys" : requestId + "-" + config.getProviderCode());
            entry.setCapabilityCode(request.getCapabilityCode());
            entry.setProviderType(config == null ? null : config.getProviderType());
            entry.setModel(config == null ? null : config.getModel());
            entry.setCallerType(StringUtils.hasText(request.getCallerType()) ? request.getCallerType() : "USER");
            entry.setCallerId(request.getCallerId());
            if (prompt != null) {
                entry.setPromptChars(prompt.length());
                entry.setPromptPreview(truncate(prompt, 500));
            }
            entry.setTotalTokens(tokens);
            entry.setStatus(status);
            entry.setErrorMsg(truncate(error, 500));
            callLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("审计落库失败（不影响主流程）", e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
