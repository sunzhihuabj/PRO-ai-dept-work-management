package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 事件创建/编辑 DTO
 */
@Data
public class EventCreateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "事件类型不能为空")
    private Integer eventType;

    @NotBlank(message = "事件标题不能为空")
    private String eventTitle;

    /** 紧急程度（1紧急 2一般 3低） */
    private Integer urgency;

    /** 指定的处理人ID（可空，待后续指派） */
    private Long handlerId;

    /** 事件描述 */
    private String description;
}
