package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目编号 */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 项目类型（1整机厂 2技改 3研发） */
    private Integer projectType;

    /** 客户名称 */
    private String customerName;

    /** 项目负责人 */
    private String projectLeader;

    /** 状态（0待启动 1进行中 2已完成 3已关闭 4暂停） */
    private Integer status;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 目标成本 */
    private BigDecimal targetCost;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 项目描述 */
    private String description;

    /** 创建人 */
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;
}
