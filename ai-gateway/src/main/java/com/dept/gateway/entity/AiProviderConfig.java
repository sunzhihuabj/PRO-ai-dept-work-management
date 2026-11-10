package com.dept.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 通道配置实体（api_key 密文落库，明文只在调用时解密填充，任何接口不回显）
 *
 * @author dept-mgmt
 */
@Data
@TableName("sys_ai_provider_config")
public class AiProviderConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置编码（唯一） */
    private String providerCode;

    /** 服务商显示名 */
    private String providerName;

    /** 类型（OPENAI_COMPAT/MOCK/OLLAMA） */
    private String providerType;

    /** API 地址 */
    private String endpoint;

    /** 模型名 */
    private String model;

    /** API Key 密文（AES-GCM Base64），不序列化输出 */
    @JsonIgnore
    private String apiKeyCipher;

    /** Key 脱敏提示（sk-****1234） */
    private String apiKeyHint;

    /** 优先级（小者优先，形成降级链） */
    private Integer priority;

    /** 启用（1启用 0停用） */
    private Integer enabled;

    /** 每分钟请求上限 */
    private Integer rpmLimit;

    /** 每日请求上限 */
    private Integer dailyLimit;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（本表采用物理删除策略：唯一键 provider_code 会与逻辑删除冲突，
     * 删除时直接物理 DELETE，该字段仅为兼容表结构保留）
     */
    @JsonIgnore
    private Integer deleted;

    /** 运行时解密填充的明文 Key（非表字段：绝不入库、绝不序列化） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @JsonIgnore
    private String apiKeyPlain;

    /** 调用时取明文 Key */
    public String resolveApiKey() {
        return apiKeyPlain;
    }
}
