package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 任务DTO（创建/更新用）
 *
 * @author dept-mgmt
 */
@Data
public class TaskDTO {

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 阶段ID */
    private Long phaseId;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    /** 任务类型 */
    private String taskType;

    /** 价值类型（1VA 2NNVA 3NVA） */
    private Integer valueType;

    /** 浪费类型 */
    private String wasteType;

    /** 负责人ID */
    private Long assigneeId;

    /** 协作者ID列表（JSON数组字符串） */
    private String collaboratorIds;

    /** 优先级（1高 2中 3低） */
    private Integer priority;

    /** 计划工时 */
    private BigDecimal planHours;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 截止日期 */
    private LocalDate deadline;

    /** 任务描述 */
    private String description;

    /** 是否拉动模式（0否 1是） */
    private Integer pullMode;
}
