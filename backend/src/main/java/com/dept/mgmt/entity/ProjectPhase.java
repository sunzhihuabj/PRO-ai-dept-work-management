package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 项目阶段表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_phase")
public class ProjectPhase extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 阶段名称 */
    private String phaseName;

    /** 阶段顺序 */
    private Integer phaseOrder;

    /** 阶段类型（1整机厂 2技改 3研发） */
    private Integer phaseType;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 状态（0未开始 1进行中 2已完成） */
    private Integer status;

    /** 阶段描述 */
    private String description;
}
