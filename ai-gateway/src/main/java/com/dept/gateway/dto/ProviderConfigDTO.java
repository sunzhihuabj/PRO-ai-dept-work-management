package com.dept.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 通道配置 DTO（api_key 只写不读：编辑时留空表示不修改）
 *
 * @author dept-mgmt
 */
@Data
public class ProviderConfigDTO {

    private Long id;

    @NotBlank(message = "配置编码不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "配置编码仅允许字母数字下划线连字符")
    @Size(max = 50)
    private String providerCode;

    @NotBlank(message = "服务商名称不能为空")
    @Size(max = 100)
    private String providerName;

    /** OPENAI_COMPAT / MOCK / OLLAMA */
    @NotBlank(message = "通道类型不能为空")
    @Pattern(regexp = "^(OPENAI_COMPAT|MOCK|OLLAMA)$", message = "通道类型仅允许 OPENAI_COMPAT/MOCK/OLLAMA")
    private String providerType;

    @Size(max = 200, message = "endpoint 不能超过200字符")
    private String endpoint;

    @Size(max = 100)
    private String model;

    /** 明文 Key（写入时加密落库；编辑留空=不修改；永不回显） */
    @Size(max = 200, message = "API Key 不能超过200字符")
    private String apiKey;

    @Min(value = 1, message = "优先级至少为1")
    private Integer priority = 100;

    private Integer enabled = 0;

    @Min(value = 1, message = "每分钟上限至少为1")
    private Integer rpmLimit = 35;

    @Min(value = 1, message = "每日上限至少为1")
    private Integer dailyLimit = 500;

    @Size(max = 200)
    private String remark;
}
