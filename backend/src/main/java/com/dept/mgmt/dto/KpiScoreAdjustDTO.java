package com.dept.mgmt.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * KPI评分调整 DTO
 */
@Data
public class KpiScoreAdjustDTO {

    /** 调整分值（-10 ~ +10） */
    @NotNull(message = "调整分值不能为空")
    @DecimalMin(value = "-10", message = "单次调减不允许超过10分")
    @DecimalMax(value = "10", message = "单次调增不允许超过10分")
    private BigDecimal adjustScore;

    @NotBlank(message = "调整原因必须填写")
    private String adjustReason;
}
