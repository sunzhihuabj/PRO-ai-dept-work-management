package com.dept.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 调用审计实体（保留 2 年，网关每日定时清理）
 *
 * @author dept-mgmt
 */
@Data
@TableName("sys_ai_call_log")
public class AiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求唯一标识 */
    private String requestId;

    /** 能力点编码 */
    private String capabilityCode;

    /** 实际使用的通道类型 */
    private String providerType;

    /** 实际使用的模型 */
    private String model;

    /** 调用方类型（USER/AGENT） */
    private String callerType;

    /** 调用方标识（userId 或 agentKey） */
    private String callerId;

    /** 提示词字符数（脱敏后） */
    private Integer promptChars;

    /** 响应字符数 */
    private Integer responseChars;

    /** 提示词预览（脱敏后前500字） */
    private String promptPreview;

    /** 响应预览（前500字） */
    private String responsePreview;

    /** Token 用量 */
    private Integer totalTokens;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 状态（SUCCESS/PROVIDER_ERROR/QUOTA_EXCEEDED/CONTRACT_FAILED/DEGRADED/MOCK） */
    private String status;

    /** 错误信息（收敛后） */
    private String errorMsg;

    /** 调用时间 */
    private LocalDateTime createTime;
}
