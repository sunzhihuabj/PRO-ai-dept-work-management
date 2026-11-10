package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 阶段ID */
    private Long phaseId;

    /** 任务名称 */
    private String taskName;

    /** 任务类型 */
    private String taskType;

    /** 价值类型（1VA 2NNVA 3NVA） */
    private Integer valueType;

    /** 浪费类型 */
    private String wasteType;

    /** 负责人ID */
    private Long assigneeId;

    /** 协作者ID列表（JSON数组） */
    private String collaboratorIds;

    /** 优先级（1高 2中 3低） */
    private Integer priority;

    /** 状态（0待办 1进行中 2待审核 3已完成 4已关闭） */
    private Integer status;

    /** 进度百分比（0-100） */
    private Integer progress;

    /** 计划工时 */
    private BigDecimal planHours;

    /** 实际工时 */
    private BigDecimal actualHours;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 截止日期 */
    private LocalDate deadline;

    /** 实际开始时间 */
    private LocalDateTime actualStartTime;

    /** 实际完成时间 */
    private LocalDateTime completeTime;

    /** 任务描述 */
    private String description;

    /** 是否拉动模式（0否 1是） */
    private Integer pullMode;

    /** 创建人 */
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;
}
