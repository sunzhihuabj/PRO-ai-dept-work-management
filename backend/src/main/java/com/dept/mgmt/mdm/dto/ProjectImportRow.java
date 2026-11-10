package com.dept.mgmt.mdm.dto;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目档案导入行（T-213 迁移模板列口径，核心必填字段）
 *
 * @author dept-mgmt
 */
@Data
@ColumnWidth(18)
@ExcelIgnoreUnannotated
public class ProjectImportRow {

    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("项目类型")
    @ColumnWidth(12)
    private String projectType;

    @ExcelProperty("客户名称")
    private String customerName;

    @ExcelProperty("项目负责人")
    private String projectLeader;

    @ExcelProperty("合同金额")
    private BigDecimal contractAmount;

    @ExcelProperty("计划开始日期")
    private String planStartDate;

    @ExcelProperty("计划结束日期")
    private String planEndDate;

    @ExcelProperty("项目描述")
    @ColumnWidth(30)
    private String description;
}
