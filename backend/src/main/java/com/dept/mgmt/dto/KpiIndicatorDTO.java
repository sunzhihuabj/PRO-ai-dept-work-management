package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * KPI指标 DTO
 */
@Data
public class KpiIndicatorDTO {

    @NotBlank(message = "指标名称不能为空")
    @Size(min = 2, max = 50, message = "指标名称长度在2-50之间")
    private String indicatorName;

    @NotBlank(message = "指标编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,19}$", message = "编码格式：大写字母开头，2-20位")
    private String indicatorCode;

    /** 适用项目类型（1整机厂 2技改 3研发），可为空表示通用 */
    private Integer projectType;

    /** 指标类型：进度/成本/质量/满意度 */
    private String indicatorType;

    @NotNull(message = "基准分值不能为空")
    private BigDecimal baseScore;

    private String unit;

    @Size(max = 500, message = "描述最长500字符")
    private String description;

    private Integer status;
}
