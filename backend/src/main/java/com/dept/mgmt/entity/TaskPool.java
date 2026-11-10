package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 任务池表（标准任务模板库）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_pool")
public class TaskPool extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String taskName;
    private String taskType;
    /** 适用项目类型（1整机厂 2技改 3研发） */
    private Integer projectType;
    private Integer phaseOrder;
    /** 价值类型（1VA 2NNVA 3NVA） */
    private Integer valueType;
    private BigDecimal planHours;
    private String description;
    private Integer status;
}
