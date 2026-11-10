package com.dept.gateway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 网关向量化请求（T-106 RAG）
 *
 * @author dept-mgmt
 */
@Data
public class EmbedRequest {

    /** 待向量化文本列表（批量，单批上限 64 条防超大请求） */
    @NotEmpty(message = "待向量化文本不能为空")
    @Size(max = 64, message = "单批向量化不能超过 64 条")
    private List<String> texts;

    /** 调用方标识（审计用） */
    private String callerId;
}
