package com.dept.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关治理配置项
 *
 * @author dept-mgmt
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** 服务间认证密钥（backend 与网关共享） */
    private String apiKey;

    /** 通道配置加密密钥（AES-GCM 派生源） */
    private String configSecret;

    /** 出网脱敏开关（仅商用通道强制） */
    private boolean desensitizeEnabled = true;

    /** 提示词最大字符数 */
    private int maxPromptChars = 32000;

    /** 全链失败响应文案（转人工模式） */
    private String degradedMessage = "AI 服务暂不可用，请稍后重试或转人工处理";
}
