package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 摘要请求 DTO
 */
@Data
public class AiSummaryDTO {

    /** 待处理文本（日报/周报/会议纪要等原始内容） */
    @NotBlank(message = "文本内容不能为空")
    private String text;

    /** 场景类型：daily/weekly/meeting 等，影响提示词 */
    private String type;
}
