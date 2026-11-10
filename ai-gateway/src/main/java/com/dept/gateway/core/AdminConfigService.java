package com.dept.gateway.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.gateway.crypto.AesGcmCipher;
import com.dept.gateway.dto.ProviderConfigDTO;
import com.dept.gateway.entity.AiProviderConfig;
import com.dept.gateway.exception.GatewayException;
import com.dept.gateway.mapper.AiProviderConfigMapper;
import com.dept.gateway.provider.AiProvider;
import com.dept.gateway.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * AI 通道配置管理服务（前端"AI 服务配置"页面的后端支撑）
 * api_key 只写不读：写入加密落库，任何接口不回显明文
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminConfigService {

    private final AiProviderConfigMapper configMapper;
    private final AesGcmCipher cipher;
    private final ProviderRegistry providerRegistry;

    /**
     * 配置列表（api_key_cipher 不输出，仅 hint 脱敏提示）
     */
    public List<AiProviderConfig> list() {
        return configMapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                .orderByAsc(AiProviderConfig::getPriority));
    }

    /**
     * 创建配置
     */
    public Long create(ProviderConfigDTO dto) {
        // 物理清理同编码的历史逻辑删除残留行（唯一键 provider_code 会被残留行占用）
        configMapper.delete(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getProviderCode, dto.getProviderCode())
                .eq(AiProviderConfig::getDeleted, 1));
        if (configMapper.selectCount(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getProviderCode, dto.getProviderCode())) > 0) {
            throw new GatewayException(409, "配置编码已存在: " + dto.getProviderCode());
        }
        validateByType(dto);
        AiProviderConfig config = new AiProviderConfig();
        applyDto(config, dto);
        if (StringUtils.hasText(dto.getApiKey())) {
            config.setApiKeyCipher(cipher.encrypt(dto.getApiKey()));
            config.setApiKeyHint(mask(dto.getApiKey()));
        }
        configMapper.insert(config);
        log.info("创建AI通道配置: {} ({})", dto.getProviderCode(), dto.getProviderType());
        return config.getId();
    }

    /**
     * 更新配置（apiKey 留空 = 不修改 Key）
     */
    public void update(Long id, ProviderConfigDTO dto) {
        AiProviderConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new GatewayException(404, "配置不存在");
        }
        validateByType(dto);
        existing.setProviderName(dto.getProviderName());
        existing.setProviderType(dto.getProviderType());
        existing.setEndpoint(dto.getEndpoint());
        existing.setModel(dto.getModel());
        existing.setPriority(dto.getPriority());
        existing.setEnabled(dto.getEnabled());
        existing.setRpmLimit(dto.getRpmLimit());
        existing.setDailyLimit(dto.getDailyLimit());
        existing.setRemark(dto.getRemark());
        if (StringUtils.hasText(dto.getApiKey())) {
            existing.setApiKeyCipher(cipher.encrypt(dto.getApiKey()));
            existing.setApiKeyHint(mask(dto.getApiKey()));
        }
        configMapper.updateById(existing);
        log.info("更新AI通道配置: {}", existing.getProviderCode());
    }

    /**
     * 删除配置
     */
    public void delete(Long id) {
        AiProviderConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new GatewayException(404, "配置不存在");
        }
        configMapper.deleteById(id);
        log.info("删除AI通道配置: {}", existing.getProviderCode());
    }

    /**
     * 测试连接：向该通道发一条最小请求，返回可达性与耗时（不落审计）
     */
    public Map<String, Object> test(Long id) {
        AiProviderConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new GatewayException(404, "配置不存在");
        }
        if (StringUtils.hasText(config.getApiKeyCipher())) {
            try {
                config.setApiKeyPlain(cipher.decrypt(config.getApiKeyCipher()));
            } catch (Exception e) {
                return Map.of("success", false, "message", "API Key 解密失败（加密密钥可能已变更），请重新保存 Key");
            }
        }
        AiProvider provider = providerRegistry.get(config.getProviderType());
        long start = System.currentTimeMillis();
        try {
            AiProvider.ProviderResult result = provider.chat(config, "ping，请回复 pong。", 16);
            return Map.of("success", true,
                    "message", "连接成功",
                    "replyPreview", result.content() == null ? "" :
                            result.content().substring(0, Math.min(80, result.content().length())),
                    "durationMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 收敛上游错误细节，只给调用方可理解的失败信息
            log.warn("通道测试连接失败: {}", config.getProviderCode(), e);
            return Map.of("success", false,
                    "message", "连接失败：endpoint 不可达 / Key 无效 / 模型名错误，请检查配置",
                    "durationMs", System.currentTimeMillis() - start);
        }
    }

    // ===== 私有辅助 =====

    /** 按类型校验必填项 */
    private void validateByType(ProviderConfigDTO dto) {
        if ("OPENAI_COMPAT".equals(dto.getProviderType())) {
            if (!StringUtils.hasText(dto.getEndpoint())) {
                throw new GatewayException(400, "商用通道必须配置 endpoint");
            }
            if (!StringUtils.hasText(dto.getModel())) {
                throw new GatewayException(400, "商用通道必须配置模型名");
            }
        }
        if ("OLLAMA".equals(dto.getProviderType()) && !StringUtils.hasText(dto.getEndpoint())) {
            throw new GatewayException(400, "Ollama 通道必须配置 endpoint");
        }
    }

    private void applyDto(AiProviderConfig config, ProviderConfigDTO dto) {
        config.setProviderCode(dto.getProviderCode());
        config.setProviderName(dto.getProviderName());
        config.setProviderType(dto.getProviderType());
        config.setEndpoint(dto.getEndpoint());
        config.setModel(dto.getModel());
        config.setPriority(dto.getPriority());
        config.setEnabled(dto.getEnabled());
        config.setRpmLimit(dto.getRpmLimit());
        config.setDailyLimit(dto.getDailyLimit());
        config.setRemark(dto.getRemark());
    }

    /** Key 脱敏：保留前 3 后 4 位 */
    private String mask(String key) {
        String trimmed = key.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
