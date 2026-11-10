package com.dept.mgmt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 奖金配置 DTO
 */
@Data
public class BonusSaveDTO {

    @NotNull(message = "项目不能为空")
    private Long projectId;

    @NotNull(message = "用户不能为空")
    private Long userId;

    /** 奖金类型（1整机厂 2技改 3研发） */
    @NotNull(message = "奖金类型不能为空")
    @Min(value = 1, message = "奖金类型不合法")
    @Max(value = 3, message = "奖金类型不合法")
    private Integer bonusType;

    /** 项目阶段（1前期 2中期 3后期 4大后期） */
    @Min(value = 1, message = "项目阶段不合法")
    @Max(value = 4, message = "项目阶段不合法")
    private Integer projectPeriod;

    @NotNull(message = "基础金额不能为空")
    @DecimalMin(value = "0", message = "基础金额不能为负")
    private BigDecimal baseAmount;

    @DecimalMin(value = "0", message = "系数不能为负")
    private BigDecimal coefficient;
}
