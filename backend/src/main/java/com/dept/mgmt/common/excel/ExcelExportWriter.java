package com.dept.mgmt.common.excel;

import com.alibaba.excel.EasyExcel;
import com.dept.mgmt.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 导出写出器（EasyExcel 流式写封装）
 * 统一处理下载响应头（文件名 RFC 5987 编码，防响应头注入，对应审计 L2 修复口径）
 *
 * @author dept-mgmt
 */
public final class ExcelExportWriter {

    private ExcelExportWriter() {
    }

    /**
     * 写出下载响应
     *
     * @param response  HTTP 响应
     * @param fileName  文件名（不含扩展名，自动追加 .xlsx）
     * @param sheetName sheet 名称
     * @param clazz     列映射类型（@ExcelProperty）
     * @param data      数据行（模板下载传示例行或空列表）
     */
    public static <T> void write(HttpServletResponse response, String fileName,
                                 String sheetName, Class<T> clazz, List<T> data) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String encoded = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);
            EasyExcel.write(response.getOutputStream(), clazz)
                    .sheet(sheetName)
                    .doWrite(data == null ? List.of() : data);
        } catch (IOException e) {
            throw new BusinessException(500, "Excel 导出失败: " + e.getMessage());
        }
    }
}
