package com.dept.mgmt.workshop.service;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.workshop.entity.TplusReconciliation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T+ 文件适配器（T-211）
 * 每日 T+ 凭证导出（Excel .xlsx / CSV）解析 → 归一为标准报文 → 落 inbound 目录
 * → 复用 InventoryService.reconcile() 逐单核对 → 差异清单
 * T+ 导出格式约定（上线时按真实模板列序微调，列头匹配优先于列序）：
 * 列：单据编号 | 物料编码 | 收发方向（采购入库/材料出库）| 数量 | 单据日期
 * 方向映射：采购入库 → IN；材料出库 → OUT
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TplusFileAdapterService {

    /** 归一报文字段（与 T-204 推送报文对齐） */
    private static final Map<String, String> HEADER_MAP = Map.of(
            "单据编号", "recordNo",
            "物料编码", "materialCode",
            "收发方向", "directionText",
            "数量", "qty",
            "单据日期", "vdate");

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    /**
     * 导入 T+ 每日导出文件（.xlsx/.csv），归一落 inbound 后自动对账
     *
     * @param filePath T+ 导出文件绝对路径
     * @return {imported 新导入单数, skipped 已存在单数, reconcile 对账批次}
     */
    public Map<String, Object> importAndReconcile(String filePath) {
        Path path = Paths.get(filePath == null ? "" : filePath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new BusinessException(404, "T+ 导出文件不存在: " + path);
        }
        String name = path.getFileName().toString().toLowerCase();
        List<Map<String, Object>> rows;
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            rows = parseExcel(path);
        } else if (name.endsWith(".csv")) {
            rows = parseCsv(path);
        } else {
            throw new BusinessException(400, "仅支持 .xlsx/.xls/.csv 导出文件: " + name);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parsedRows", rows.size());
        result.putAll(mergeToInbound(rows));
        TplusReconciliation batch = inventoryService.reconcile();
        Map<String, Object> reconcile = new LinkedHashMap<>();
        reconcile.put("batchNo", batch.getBatchNo());
        reconcile.put("localCount", batch.getLocalCount());
        reconcile.put("tplusCount", batch.getTplusCount());
        reconcile.put("matchedCount", batch.getMatchedCount());
        reconcile.put("diffCount", batch.getDiffCount());
        reconcile.put("diffs", safeParse(batch.getDiffJson()));
        result.put("reconcile", reconcile);
        log.info("T+ 文件适配器导入+对账完成: file={} 解析 {} 行 差异 {} 单", path.getFileName(), rows.size(), batch.getDiffCount());
        return result;
    }

    /** 解析 Excel（列头匹配优先于列序） */
    private List<Map<String, Object>> parseExcel(Path path) {
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean headerFound = false;
        try (InputStream in = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<Integer, String> colIndex = new LinkedHashMap<>();
            for (Row row : sheet) {
                if (!headerFound) {
                    for (Cell cell : row) {
                        String text = cellText(cell);
                        if (HEADER_MAP.containsKey(text)) {
                            colIndex.put(cell.getColumnIndex(), HEADER_MAP.get(text));
                        }
                    }
                    if (colIndex.keySet().size() >= 3) {
                        headerFound = true;
                    }
                    continue;
                }
                Map<String, Object> item = readRowByIndex(row, colIndex);
                if (item != null) {
                    rows.add(item);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "T+ Excel 解析失败: " + e.getMessage());
        }
        if (!headerFound || rows.isEmpty()) {
            throw new BusinessException(400, "T+ 导出文件未识别到表头（需含：单据编号/物料编码/收发方向/数量）或数据为空");
        }
        return rows;
    }

    /** 解析 CSV（首行表头，逗号分隔，UTF-8） */
    private List<Map<String, Object>> parseCsv(Path path) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                throw new BusinessException(400, "T+ CSV 文件为空");
            }
            String[] headers = lines.get(0).split("[,，]");
            Map<Integer, String> colIndex = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim();
                if (HEADER_MAP.containsKey(h)) {
                    colIndex.put(i, HEADER_MAP.get(h));
                }
            }
            if (colIndex.keySet().size() < 3) {
                throw new BusinessException(400, "T+ CSV 表头未识别（需含：单据编号/物料编码/收发方向/数量）");
            }
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split("[,，]");
                Map<String, Object> item = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : colIndex.entrySet()) {
                    item.put(entry.getValue(), entry.getKey() < cols.length ? cols[entry.getKey()].trim() : "");
                }
                Map<String, Object> normalized = normalize(item);
                if (normalized != null) {
                    rows.add(normalized);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "T+ CSV 解析失败: " + e.getMessage());
        }
        return rows;
    }

    private Map<String, Object> readRowByIndex(Row row, Map<Integer, String> colIndex) {
        Map<String, Object> item = new LinkedHashMap<>();
        boolean allEmpty = true;
        for (Map.Entry<Integer, String> entry : colIndex.entrySet()) {
            Cell cell = row.getCell(entry.getKey());
            String text = cellText(cell);
            if (!text.isBlank()) {
                allEmpty = false;
            }
            item.put(entry.getValue(), text);
        }
        if (allEmpty) {
            return null;
        }
        return normalize(item);
    }

    /** 归一一行：方向文本 → IN/OUT，数量 → BigDecimal；非法行抛错（准确率 100% 口径：坏行不静默吞掉） */
    private Map<String, Object> normalize(Map<String, Object> item) {
        String recordNo = str(item.get("recordNo"));
        String materialCode = str(item.get("materialCode"));
        String directionText = str(item.get("directionText"));
        String qtyText = str(item.get("qty"));
        if (recordNo.isBlank() && materialCode.isBlank()) {
            return null;
        }
        if (recordNo.isBlank() || materialCode.isBlank() || qtyText.isBlank()) {
            throw new BusinessException(400, "T+ 导出行数据不完整: " + item);
        }
        String direction;
        if (directionText.contains("采购入库") || directionText.contains("入库")) {
            direction = "IN";
        } else if (directionText.contains("材料出库") || directionText.contains("出库")) {
            direction = "OUT";
        } else {
            throw new BusinessException(400, "T+ 导出行方向无法识别（采购入库/材料出库）: " + directionText + " 单号 " + recordNo);
        }
        BigDecimal qty;
        try {
            qty = new BigDecimal(qtyText);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "T+ 导出行数量非法: " + qtyText + " 单号 " + recordNo);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordNo", recordNo);
        payload.put("voucherType", directionText);
        payload.put("materialCode", materialCode);
        payload.put("materialName", str(item.getOrDefault("materialName", "")));
        payload.put("direction", direction);
        payload.put("qty", qty);
        payload.put("source", "TPLUS_EXPORT");
        if (item.get("vdate") != null && !str(item.get("vdate")).isBlank()) {
            payload.put("vdate", str(item.get("vdate")));
        }
        return payload;
    }

    /** 归一报文按单号去重合并进 inbound/tplus_export.jsonl（reconcile 复用同一文件） */
    private Map<String, Object> mergeToInbound(List<Map<String, Object>> rows) {
        Path inbound = Paths.get("./data/tplus", "inbound", "tplus_export.jsonl").toAbsolutePath();
        Set<String> known = new LinkedHashSet<>();
        try {
            if (Files.exists(inbound)) {
                for (String line : Files.readAllLines(inbound, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        known.add(objectMapper.readValue(line, Map.class).get("recordNo").toString());
                    }
                }
            }
            int imported = 0, skipped = 0;
            for (Map<String, Object> row : rows) {
                String recordNo = String.valueOf(row.get("recordNo"));
                if (known.contains(recordNo)) {
                    skipped++;
                    continue;
                }
                known.add(recordNo);
                Files.createDirectories(inbound.getParent());
                Files.write(inbound, (objectMapper.writeValueAsString(row) + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                imported++;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imported", imported);
            result.put("skipped", skipped);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "T+ 导出报文合并失败: " + e.getMessage());
        }
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
        }
        return cell.toString().trim();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<Map<String, Object>> safeParse(String json) {
        try {
            return json == null || "[]".equals(json) ? List.of()
                    : objectMapper.readValue(json, objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
