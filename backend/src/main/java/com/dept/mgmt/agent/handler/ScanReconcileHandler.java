package com.dept.mgmt.agent.handler;

import com.dept.mgmt.workshop.entity.TplusReconciliation;
import com.dept.mgmt.workshop.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫码过账对账处理器（AGT-16，T-204，Auto）
 * 全链路自动执行：T+ 推送队列处理 → 回执导入（T-211 前文件模拟）→ 逐单对账 → 差异清单
 * 只读盘点 + 队列推进，不修改业务单据本身，可自动执行
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanReconcileHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-16";
    }

    /** 队列推进 + 对账为幂等只增操作，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long projectId = null;
        if (instruction != null) {
            Matcher m = PROJECT_ID.matcher(instruction);
            if (m.find()) {
                projectId = Long.valueOf(m.group(1));
            }
        }
        int processed = inventoryService.processPushQueue();
        int imported = inventoryService.simulateTplusImport();
        TplusReconciliation batch = inventoryService.reconcile();

        List<Map<String, Object>> diffs = new ArrayList<>();
        if (batch.getDiffJson() != null && !"[]".equals(batch.getDiffJson())) {
            try {
                diffs = objectMapper.readValue(batch.getDiffJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                log.warn("AGT-16 差异明细解析失败: {}", e.getMessage());
            }
        }
        BigDecimal successRate = inventoryService.tplusSuccessRate();

        Map<String, Object> reconcile = new LinkedHashMap<>();
        reconcile.put("batchNo", batch.getBatchNo());
        reconcile.put("localCount", batch.getLocalCount());
        reconcile.put("tplusCount", batch.getTplusCount());
        reconcile.put("matchedCount", batch.getMatchedCount());
        reconcile.put("diffCount", batch.getDiffCount());
        reconcile.put("diffs", diffs);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-16 扫码过账对账");
        out.put("projectId", projectId);
        out.put("executedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        out.put("pushedCount", processed);
        out.put("importedCount", imported);
        out.put("tplusSuccessRate", successRate + "%");
        out.put("reconcile", reconcile);
        log.info("AGT-16 对账完成: 推送 {} 导入 {} 差异 {}", processed, imported, batch.getDiffCount());
        return objectMapper.writeValueAsString(out);
    }
}
