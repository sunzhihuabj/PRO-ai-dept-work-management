package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 子任务表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_sub")
public class TaskSub extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long taskId;
    private String subName;
    private Long assigneeId;
    /** 状态（0待办 1进行中 2已完成） */
    private Integer status;
    private BigDecimal planHours;
    private BigDecimal actualHours;
    private LocalDate deadline;
    private LocalDateTime completeTime;
    private Integer orderNum;
}
