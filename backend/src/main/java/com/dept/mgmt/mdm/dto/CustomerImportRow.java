package com.dept.mgmt.mdm.dto;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 客户主体导入行（T-213 迁移模板列口径）
 *
 * @author dept-mgmt
 */
@Data
@ColumnWidth(20)
@ExcelIgnoreUnannotated
public class CustomerImportRow {

    @ExcelProperty("客户名称")
    private String customerName;

    @ExcelProperty("统一社会信用代码")
    private String socialCreditCode;

    @ExcelProperty("别名1")
    private String alias1;

    @ExcelProperty("别名2")
    private String alias2;

    @ExcelProperty("联系人")
    private String contactPerson;

    @ExcelProperty("联系电话")
    private String contactPhone;

    @ExcelProperty("客户分级")
    @ColumnWidth(12)
    private String level;

    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}
