package com.dept.mgmt.common.client;

import com.dept.mgmt.aiworkspace.service.HermesAgentConfigService;
import com.dept.mgmt.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * AI 网关客户端（backend → ai-gateway 8090，服务间 X-API-Key 认证）
 * 红线落地：backend 内任何业务代码不得直连模型 SDK/URL，一律经本客户端调网关
 *
 * <p><b>AI 能力 Hermes 化迁移（M-2026-11-18）</b>：{@link #chat(String, String, Long, Map)}
 * 出口已迁移至 <b>Hermes Agent 原生 run</b>（hermes_agent_config.migrate.chatViaHermes 总开关，
 * 默认 true）——全部 18 处业务 chat 调用点（Ops 判定/Agent 能力点/记忆摘要/ChatBI/文档合规/
 * 意图分类等）零改动迁移至 Hermes 原生能力；Hermes 不可用或迁移开关关闭时自动回落
 * 网关直连通道（异常兜底，业务不断链）。embed/visionChat 为模型通道原语（Hermes 无
 * embedding API、run 文本接口暂不收图片入参），保留网关直连，属已文档化的适配边界。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class AiGatewayClient {

    private final String baseUrl;
    private final String apiKey;
    private final RestClient client;

    @Autowired
    private HermesService hermesService;

    @Autowired
    private HermesAgentConfigService hermesConfig;

    public AiGatewayClient(@Value("${ai.gateway.base-url:http://localhost:8090}") String baseUrl,
                           @Value("${ai.gateway.api-key:dept-gw-internal-2026r2-fA9kQ2xV}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        // M3 超时治理（C3 联调 F2 修正）：连接 3s / 读取 180s——网关侧单通道读超时 60s 且失败重试一次
        // （最长约 121s+），backend 读超时必须大于网关最长处理时长，否则网关仍在重试而 backend 先断连
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(3_000));
        factory.setReadTimeout(java.time.Duration.ofMillis(180_000));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 调用网关多模态对话（V2.0 E1 眼睛模块）：prompt + 图片（data URL 形状）。
     * 网关按 OpenAI vision 形状转发至多模态通道；不支持视觉的通道走降级链。
     *
     * @param capabilityCode 能力点编码（如 CAP_VISION_DESCRIBE）
     * @param prompt         提示词
     * @param imageDataUrls  图片 data URL 列表（data:image/jpeg;base64,...），≤4 张
     * @param userId         调用用户ID（审计用）
     * @return 模型输出全文
     */
    @SuppressWarnings("unchecked")
    public String visionChat(String capabilityCode, String prompt, List<String> imageDataUrls, Long userId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("capabilityCode", capabilityCode);
        body.put("prompt", prompt);
        body.put("callerType", "USER");
        body.put("callerId", userId == null ? null : String.valueOf(userId));
        body.put("images", imageDataUrls);
        try {
            Map<String, Object> resp = client.post()
                    .uri("/v1/gateway/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("content") == null) {
                throw new BusinessException(503, "AI 服务返回空内容，请稍后重试或转人工处理");
            }
            if (Boolean.TRUE.equals(resp.get("degraded"))) {
                log.warn("AI 网关降级: capability={}, requestId={}", capabilityCode, resp.get("requestId"));
                throw new BusinessException(503, "AI 服务暂不可用，请转人工处理");
            }
            return resp.get("content").toString();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("AI 网关视觉调用失败: capability={}, status={}", capabilityCode, status);
            throw new BusinessException(503, "视觉识别服务暂不可用，请稍后重试或转人工处理");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 网关连接失败: {}", baseUrl, e);
            throw new BusinessException(503, "AI 服务连接失败，请稍后重试或转人工处理");
        }
    }

    /**
     * 调用网关向量化（T-106 RAG）
     *
     * @param texts 文本列表
     * @param userId 调用用户ID（审计用）
     * @return embed.model 与 embed.embeddings（与输入顺序一致）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> embed(List<String> texts, Long userId) {
        Map<String, Object> body = Map.of(
                "texts", texts,
                "callerId", userId == null ? "" : String.valueOf(userId));
        try {
            Map<String, Object> resp = client.post()
                    .uri("/v1/gateway/embed")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("embeddings") == null) {
                throw new BusinessException(503, "向量化服务返回空内容");
            }
            return resp;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("向量化调用失败: status={}", status);
            throw new BusinessException(503, "向量化服务不可用：请检查 AI 通道配置（需 embeddings 支持）");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("向量化连接失败: {}", baseUrl, e);
            throw new BusinessException(503, "向量化服务连接失败，请检查网关是否启动");
        }
    }

    /**
     * 调用网关对话能力
     *
     * <p><b>Hermes 化迁移</b>：migrate.chatViaHermes=true（默认）时本方法经
     * <b>Hermes Agent 原生 run</b> 执行——任务书 = 能力上下文 + prompt（含 JSON 契约声明），
     * 同步等待终态取 output；Hermes 不可用/执行失败时自动回落下方网关直连通道（异常兜底）。
     *
     * @param capabilityCode 能力点编码（AGT-XX/CAP_XXX）
     * @param prompt         提示词
     * @param userId         调用用户ID（审计用）
     * @param jsonSchema     输出契约（可 null）
     * @return 模型输出全文
     */
    public String chat(String capabilityCode, String prompt, Long userId, Map<String, Object> jsonSchema) {
        // ---- Hermes 原生通道（迁移主链路） ----
        if (hermesConfig.getBool("migrate.chatViaHermes") && hermesService.available()) {
            try {
                int timeoutSec = hermesConfig.getInt("migrate.syncTimeoutSec");
                boolean autoStop = hermesConfig.getBool("run.autoStopOnTimeout");
                return hermesService.runAndWaitForText(buildTaskBook(capabilityCode, prompt, jsonSchema), timeoutSec, autoStop);
            } catch (BusinessException be) {
                log.warn("Hermes chat 通道失败（回落网关直连）: capability={}, code={}, msg={}",
                        capabilityCode, be.getCode(), be.getMessage());
            } catch (Exception e) {
                log.warn("Hermes chat 通道异常（回落网关直连）: capability={}, err={}", capabilityCode, e.getMessage());
            }
        }
        // ---- 网关直连通道（兜底/开关关闭时） ----
        return chatViaGateway(capabilityCode, prompt, userId, jsonSchema);
    }

    /** 组装 Hermes 任务书：能力上下文 + 输出契约声明 + 原提示词 */
    private String buildTaskBook(String capabilityCode, String prompt, Map<String, Object> jsonSchema) {
        StringBuilder sb = new StringBuilder();
        sb.append("【能力上下文】请在部门工作管理平台内完成以下 AI 能力调用，能力编码：")
          .append(capabilityCode).append("。\n");
        if (jsonSchema != null && !jsonSchema.isEmpty()) {
            sb.append("【输出契约】你的最终回复必须是且仅是一个符合以下结构的 JSON 对象，")
              .append("不要调用任何工具，不要输出 JSON 之外的任何文字：\n")
              .append(jsonSchema).append("\n");
        }
        String lang = hermesConfig.get("output.language");
        if (StringUtils.hasText(lang)) {
            sb.append("【输出规则】使用").append(lang).append("输出。\n");
        }
        sb.append("\n【任务】\n").append(prompt);
        return sb.toString();
    }

    /** 网关直连通道（迁移前原实现，作为 Hermes 不可用时的兜底） */
    @SuppressWarnings("unchecked")
    private String chatViaGateway(String capabilityCode, String prompt, Long userId, Map<String, Object> jsonSchema) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("capabilityCode", capabilityCode);
        body.put("prompt", prompt);
        body.put("callerType", "USER");
        body.put("callerId", userId == null ? null : String.valueOf(userId));
        if (jsonSchema != null) {
            body.put("jsonSchema", jsonSchema);
        }
        try {
            Map<String, Object> resp = client.post()
                    .uri("/v1/gateway/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("content") == null) {
                throw new BusinessException(503, "AI 服务返回空内容，请稍后重试或转人工处理");
            }
            // 降级响应（网关全链失败转人工）：content 为人工提示文案
            if (Boolean.TRUE.equals(resp.get("degraded"))) {
                log.warn("AI 网关降级: capability={}, requestId={}", capabilityCode, resp.get("requestId"));
                throw new BusinessException(503, "AI 服务暂不可用，请转人工处理");
            }
            return resp.get("content").toString();
        } catch (RestClientResponseException e) {
            // 网关错误码收敛：不透出上游细节
            int status = e.getStatusCode().value();
            log.warn("AI 网关调用失败: capability={}, status={}", capabilityCode, status);
            if (status == 403) {
                throw new BusinessException(403, "该能力未开通或禁止 AI 执行");
            }
            if (status == 429) {
                throw new BusinessException(429, "AI 调用达到限流阈值，请约10分钟后再试");
            }
            throw new BusinessException(503, "AI 服务暂不可用，请稍后重试或转人工处理");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 网关连接失败: {}", baseUrl, e);
            throw new BusinessException(503, "AI 服务连接失败，请稍后重试或转人工处理");
        }
    }
}
