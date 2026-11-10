package com.dept.mgmt.common.excel;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 导入错误行（行级错误回显）
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class ImportErrorRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Excel 行号（从表头下一行起算，与用户看到的一致） */
    private int rowIndex;

    /** 出错的列名 */
    private String column;

    /** 错误原因 */
    private String message;
}
