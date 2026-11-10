package com.dept.mgmt.mdm.dto;

import com.dept.mgmt.common.excel.ImportErrorRow;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 迁移导入结果（客户/项目迁移通用，与物料导入结果同构）
 * 存在任意错误行时 imported=0（整批拒绝，事务不落库——清洗校验口径）
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class MigrationImportResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 读取到的总行数 */
    private int totalRows;

    /** 成功导入行数（有错误时为 0） */
    private int imported;

    /** 行级错误清单（空=全部成功） */
    private List<ImportErrorRow> errors;
}
