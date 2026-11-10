package com.dept.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 网关对话响应（统一携带 ai_generated 标注与元数据）
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class ChatResponse {

    /** 请求唯一标识 */
    private String requestId;

    /** 能力点编码 */
    private String capabilityCode;

    /** AI 生成内容标注（固定 true，前端/落库必须保留该标注） */
    private Boolean aiGenerated;

    /** 实际使用的通道信息 */
    private Map<String, Object> provider;

    /** 模型输出全文 */
    private String content;

    /** 用量 */
    private Map<String, Object> usage;

    /** 是否降级（true=全链失败，content 为人工模式提示） */
    private Boolean degraded;
}
