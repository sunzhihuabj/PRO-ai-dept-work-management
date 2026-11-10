package com.dept.mgmt.mdm.material.dto;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 物料导入/导出行（列口径对齐 T-003《物料归一清单》模板）
 * 导入与导出复用同一映射，保证模板下载/填报/回传闭环一致
 *
 * @author dept-mgmt
 */
@Data
@ColumnWidth(18)
@ExcelIgnoreUnannotated
public class MaterialImportRow {

    @ExcelProperty("物料编码")
    @NotBlank(message = "物料编码必填")
    private String materialCode;

    @ExcelProperty("物料名称")
    @NotBlank(message = "物料名称必填")
    private String materialName;

    @ExcelProperty("规格型号")
    private String specModel;

    @ExcelProperty("计量单位")
    @ColumnWidth(10)
    private String unit;

    @ExcelProperty("物料类别")
    private String category;

    @ExcelProperty("旧编码/来源系统")
    @ColumnWidth(20)
    private String oldCode;

    @ExcelProperty("别名1")
    private String alias1;

    @ExcelProperty("别名2")
    private String alias2;

    @ExcelProperty("查重状态")
    @ColumnWidth(12)
    private String dedupStatus;

    @ExcelProperty("一码一物核验")
    @ColumnWidth(14)
    private String verifyStatus;

    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}
