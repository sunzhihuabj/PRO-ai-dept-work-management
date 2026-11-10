package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目奖金配置表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_bonus")
public class ProjectBonus extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long userId;
    /** 奖金类型（1整机厂 2技改 3研发） */
    private Integer bonusType;
    /** 项目阶段（1前期 2中期 3后期 4大后期） */
    private Integer projectPeriod;
    private BigDecimal baseAmount;
    private BigDecimal coefficient;
    private BigDecimal monthlyAmount;
    private BigDecimal yearlyAmount;
    private BigDecimal paidMonthly;
    private BigDecimal paidYearly;
    private Integer status;
}
