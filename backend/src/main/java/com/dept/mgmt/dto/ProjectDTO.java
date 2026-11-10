package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目DTO（创建/更新用）
 *
 * @author dept-mgmt
 */
@Data
public class ProjectDTO {

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    /** 项目类型（1整机厂 2技改 3研发） */
    @NotNull(message = "项目类型不能为空")
    private Integer projectType;

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    /** 项目负责人 */
    private String projectLeader;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 目标成本 */
    private BigDecimal targetCost;

    /** 计划开始日期 */
    @NotNull(message = "计划开始日期不能为空")
    private LocalDate planStartDate;

    /** 计划结束日期 */
    @NotNull(message = "计划结束日期不能为空")
    private LocalDate planEndDate;

    /** 项目描述 */
    private String description;
}
