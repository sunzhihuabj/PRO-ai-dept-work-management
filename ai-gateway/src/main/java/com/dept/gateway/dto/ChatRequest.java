package com.dept.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 网关对话请求
 *
 * @author dept-mgmt
 */
@Data
public class ChatRequest {

    /** 能力点编码（AGT-XX / CAP_XXX） */
    @NotBlank(message = "能力点编码不能为空")
    private String capabilityCode;

    /** 提示词全文 */
    @NotBlank(message = "提示词不能为空")
    @Size(max = 32000, message = "提示词超过 32000 字符上限")
    private String prompt;

    /** 输出 JSON 契约（可选；声明后网关强制校验，失败重生成一次） */
    private Map<String, Object> jsonSchema;

    /** 调用方类型（USER/AGENT） */
    private String callerType = "USER";

    /** 调用方标识（userId 或 agentKey） */
    private String callerId;

    /** 最大生成 Token 数（0=默认） */
    private Integer maxTokens = 0;

    /**
     * 图片列表（V2.0 E1 眼睛模块，可选）：data URL 形状（data:image/jpeg;base64,...），
     * 最多 4 张，单张 base64 ≤5MB；仅支持多模态通道（如 LM Studio gemma-4 视觉版）
     */
    @Size(max = 4, message = "单次最多 4 张图片")
    private List<String> images;
}
