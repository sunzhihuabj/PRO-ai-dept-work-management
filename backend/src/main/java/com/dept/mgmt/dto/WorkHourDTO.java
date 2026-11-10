package com.dept.mgmt.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时录入DTO
 *
 * @author dept-mgmt
 */
@Data
public class WorkHourDTO {

    /** 工时数（0.5-24） */
    @NotNull(message = "工时不能为空")
    @DecimalMin(value = "0.5", message = "工时不能少于0.5小时")
    @DecimalMax(value = "24", message = "工时不能超过24小时")
    private BigDecimal hours;

    /** 工作日期 */
    @NotNull(message = "工作日期不能为空")
    private LocalDate workDate;

    /** 工作内容描述 */
    @NotBlank(message = "工作内容不能为空")
    private String description;
}
