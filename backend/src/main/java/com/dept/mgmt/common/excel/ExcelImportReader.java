package com.dept.mgmt.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.dept.mgmt.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入读取器（EasyExcel 流式读封装）
 * 防大文件风险：行数上限校验，超限整批拒绝
 *
 * @author dept-mgmt
 */
public final class ExcelImportReader {

    /** 导入行数上限（含安全余量，防 zip 炸弹/超大文件） */
    public static final int MAX_ROWS = 20000;

    private ExcelImportReader() {
    }

    /**
     * 单行读取结果
     *
     * @param <T> 行映射类型（EasyExcel 注解类）
     */
    @Data
    @AllArgsConstructor
    public static class RowRecord<T> {
        /** Excel 行号（表头为第1行，数据从第2行起） */
        private int rowIndex;
        private T data;
    }

    /**
     * 读取首个 sheet 全部数据行（表头 1 行）
     *
     * @param in    输入流（调用方负责关闭）
     * @param clazz 行映射类型
     */
    public static <T> List<RowRecord<T>> read(InputStream in, Class<T> clazz) {
        List<RowRecord<T>> rows = new ArrayList<>();
        final boolean[] overLimit = {false};
        EasyExcel.read(in, clazz, new AnalysisEventListener<T>() {
            private int rowIndex = 1;

            @Override
            public void invoke(T data, AnalysisContext context) {
                rowIndex++;
                if (rows.size() >= MAX_ROWS) {
                    overLimit[0] = true;
                    // 停止继续解析，读完外层统一抛错
                    return;
                }
                rows.add(new RowRecord<>(rowIndex, data));
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 流结束
            }
        }).sheet().headRowNumber(1).doRead();

        if (overLimit[0]) {
            throw new BusinessException(400, "导入行数超过上限: " + MAX_ROWS + " 行，请拆分文件");
        }
        return rows;
    }
}
