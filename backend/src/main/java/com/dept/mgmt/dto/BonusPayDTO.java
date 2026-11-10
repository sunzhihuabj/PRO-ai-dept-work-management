package com.dept.mgmt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 奖金发放 DTO
 */
@Data
public class BonusPayDTO {

    /** 发放类型（1月度 2年度） */
    @NotNull(message = "发放类型不能为空")
    @Min(value = 1, message = "发放类型不合法")
    @Max(value = 2, message = "发放类型不合法")
    private Integer payType;

    /** 发放周期（YYYY-MM） */
    @NotBlank(message = "发放周期不能为空")
    private String payPeriod;

    private String remark;
}
