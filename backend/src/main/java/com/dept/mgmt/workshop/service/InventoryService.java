package com.dept.mgmt.workshop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.mapper.InventoryRecordMapper;
import com.dept.mgmt.mapper.PurchaseOrderMapper;
import com.dept.mgmt.mapper.TplusPushQueueMapper;
import com.dept.mgmt.mapper.TplusReconciliationMapper;
import com.dept.mgmt.mapper.WorkOrderMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.workshop.entity.InventoryRecord;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import com.dept.mgmt.workshop.entity.TplusPushQueue;
import com.dept.mgmt.workshop.entity.TplusReconciliation;
import com.dept.mgmt.workshop.entity.WorkOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扫码过账服务（T-204）
 * 链路：扫码 → 出入库记录（确定性校验，准确率 100% 的根基）→ T+ 推送队列 → 文件适配器落盘 → 对账
 * 本机无 T+ 系统：推送 = outbox 文件落盘（预留 API 通道），T+ 回执 = inbound 目录模拟导入（T-211 替换为真实 Excel 解析）
 * AGT-16 扫码过账对账 Auto 级：排队推送 → 导入回执 → 逐单核对 → 差异清单落库
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InventoryRecordMapper recordMapper;
    private final TplusPushQueueMapper queueMapper;
    private final TplusReconciliationMapper reconMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkshopService workshopService;
    private final CodeEngineService codeEngineService;
    private final ObjectMapper objectMapper;

    /** T+ 文件适配器目录（推送 outbox / 回执 inbound） */
    @Value("${tplus.adapter.dir:./data/tplus}")
    private String adapterDir;

    // ===== 扫码过账 =====

    /**
     * 扫码过账：出入库记录落库 + 入 T+ 推送队列
     * 确定性校验（入口拦截非法数据）：
     * direction 仅 IN/OUT；数量>0；物料编码必填；
     * IN 必须关联 PO 且物料一致、累计不超收；OUT 必须关联工单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long scanPosting(Long poId, Long workorderId, String materialCode, String materialName,
                            String direction, BigDecimal qty, String barcode, String operator, String remark) {
        if (!"IN".equals(direction) && !"OUT".equals(direction)) {
            throw new BusinessException(400, "direction 仅允许 IN(采购入库)/OUT(生产出库)");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException(400, "过账数量必须大于 0");
        }
        if (materialCode == null || materialCode.isBlank()) {
            throw new BusinessException(400, "物料编码不能为空");
        }
        LoginUser user = LoginUser.current();
        String op = operator != null && !operator.isBlank() ? operator
                : (user == null ? "SCAN" : user.getUsername());

        InventoryRecord record = new InventoryRecord();
        record.setRecordNo(codeEngineService.nextCode("IO", null));
        record.setMaterialCode(materialCode);
        record.setDirection(direction);
        record.setQty(qty);
        record.setBarcode(barcode);
        record.setSource("SCAN");
        record.setOperator(op);
        record.setScannedAt(LocalDateTime.now());
        record.setTplusStatus("PENDING");
        record.setRemark(remark);
        record.setWorkorderId(workorderId);
        record.setPoId(poId);

        if ("IN".equals(direction)) {
            if (poId == null) {
                throw new BusinessException(400, "采购入库必须关联采购订单 poId");
            }
            PurchaseOrder po = purchaseOrderMapper.selectById(poId);
            if (po == null) {
                throw new BusinessException(404, "PO 不存在: " + poId);
            }
            if (!po.getMaterialCode().equals(materialCode)) {
                throw new BusinessException(400, "扫码物料 " + materialCode + " 与 PO 物料 "
                        + po.getMaterialCode() + " 不一致，入库拦截");
            }
            BigDecimal after = po.getArrivedQty().add(qty);
            if (after.compareTo(po.getQty()) > 0) {
                throw new BusinessException(400, "超收拦截：PO " + po.getPoNo() + " 采购 " + po.getQty()
                        + "，已收 " + po.getArrivedQty() + "，本次 " + qty + " 将超收");
            }
            record.setProjectId(po.getProjectId());
            if (record.getMaterialName() == null || record.getMaterialName().isBlank()) {
                record.setMaterialName(po.getMaterialName());
            }
            // 复用到货登记：arrivedQty 累加、齐量自动完结、发布 material.arrived 事件
            workshopService.registerArrival(poId, qty, "扫码入库 " + record.getRecordNo());
        } else {
            if (workorderId == null) {
                throw new BusinessException(400, "生产出库必须关联生产工单 workorderId");
            }
            WorkOrder wo = workOrderMapper.selectById(workorderId);
            if (wo == null) {
                throw new BusinessException(404, "工单不存在: " + workorderId);
            }
            record.setProjectId(wo.getProjectId());
        }
        recordMapper.insert(record);
        enqueueTplus(record);
        log.info("扫码过账: {} {} {} x{} 操作人 {}", record.getRecordNo(),
                "IN".equals(direction) ? "入库" : "出库", materialCode, qty, op);
        return record.getId();
    }

    /** 入 T+ 推送队列（报文按 T+ 凭证语义组装） */
    private void enqueueTplus(InventoryRecord record) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("recordNo", record.getRecordNo());
            payload.put("voucherType", "IN".equals(record.getDirection()) ? "采购入库单" : "材料出库单");
            payload.put("projectId", record.getProjectId());
            payload.put("workorderId", record.getWorkorderId());
            payload.put("poId", record.getPoId());
            payload.put("materialCode", record.getMaterialCode());
            payload.put("materialName", record.getMaterialName());
            payload.put("direction", record.getDirection());
            payload.put("qty", record.getQty());
            payload.put("scannedAt", String.valueOf(record.getScannedAt()));
            payload.put("operator", record.getOperator());
            TplusPushQueue queue = new TplusPushQueue();
            queue.setRecordId(record.getId());
            queue.setRecordNo(record.getRecordNo());
            queue.setPayloadJson(objectMapper.writeValueAsString(payload));
            queue.setStatus("PENDING");
            queue.setRetryCount(0);
            queueMapper.insert(queue);
        } catch (Exception e) {
            throw new BusinessException(500, "T+ 推送报文组装失败: " + e.getMessage());
        }
    }

    // ===== T+ 推送队列 =====

    /**
     * 处理推送队列：PENDING → outbox 文件落盘（本机无 T+ 的文件适配器通道）→ PUSHED
     * 失败单留 FAILED（retry_count+1），可再次调用重试
     *
     * @return 本次成功推送单数
     */
    public int processPushQueue() {
        List<TplusPushQueue> pending = queueMapper.selectList(new LambdaQueryWrapper<TplusPushQueue>()
                .eq(TplusPushQueue::getStatus, "PENDING").orderByAsc(TplusPushQueue::getId));
        int ok = 0;
        for (TplusPushQueue item : pending) {
            try {
                appendLine(outboxFile(), item.getPayloadJson());
                item.setStatus("PUSHED");
                item.setPushedAt(LocalDateTime.now());
                InventoryRecord record = recordMapper.selectById(item.getRecordId());
                if (record != null) {
                    record.setTplusStatus("PUSHED");
                    record.setTplusPushTime(item.getPushedAt());
                    record.setTplusVoucherNo(tplusVoucherNo(record.getRecordNo()));
                    recordMapper.updateById(record);
                }
                ok++;
            } catch (Exception e) {
                log.warn("T+ 推送失败: recordNo={} err={}", item.getRecordNo(), e.getMessage());
                item.setStatus("FAILED");
                item.setErrorMsg(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                item.setRetryCount((item.getRetryCount() == null ? 0 : item.getRetryCount()) + 1);
            }
            queueMapper.updateById(item);
        }
        log.info("T+ 推送队列处理完成: 成功 {} / 共 {}", ok, pending.size());
        return ok;
    }

    /**
     * 模拟 T+ 每日导出回执（T-211 替换为真实 T+ Excel 导出解析）：
     * 读取 outbox 全量推送报文，按单号去重后落入 inbound/tplus_export.jsonl
     *
     * @return 本次新导入单数
     */
    public int simulateTplusImport() {
        Path outbox = outboxFile();
        Path inbound = inboundFile();
        if (!Files.exists(outbox)) {
            return 0;
        }
        try {
            Set<String> known = new LinkedHashSet<>();
            if (Files.exists(inbound)) {
                for (String line : Files.readAllLines(inbound, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        known.add(objectMapper.readValue(line, Map.class).get("recordNo").toString());
                    }
                }
            }
            int imported = 0;
            for (String line : Files.readAllLines(outbox, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String recordNo = objectMapper.readValue(line, Map.class).get("recordNo").toString();
                if (known.add(recordNo)) {
                    appendLine(inbound, line);
                    imported++;
                }
            }
            log.info("T+ 回执导入完成: 新增 {} 单", imported);
            return imported;
        } catch (Exception e) {
            throw new BusinessException(500, "T+ 回执导入失败: " + e.getMessage());
        }
    }

    // ===== 对账 =====

    /**
     * 对账：本地已推送单 ↔ T+ 回执逐单核对（单号 + 物料 + 方向 + 数量）
     * 差异类型：T+缺失 / 字段差异 / T+多出；结果落 tplus_reconciliation 批次
     */
    public TplusReconciliation reconcile() {
        List<InventoryRecord> pushed = recordMapper.selectList(new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTplusStatus, "PUSHED").orderByAsc(InventoryRecord::getId));
        Map<String, Map<String, Object>> tplus = readInboundMap();
        List<Map<String, Object>> diffs = new ArrayList<>();
        int matched = 0;
        Set<String> localNos = new LinkedHashSet<>();
        for (InventoryRecord record : pushed) {
            localNos.add(record.getRecordNo());
            Map<String, Object> remote = tplus.get(record.getRecordNo());
            if (remote == null) {
                diffs.add(diffRow("T+缺失", record.getRecordNo(), record.getMaterialCode(),
                        record.getQty(), null));
                continue;
            }
            List<String> fields = new ArrayList<>();
            if (!record.getMaterialCode().equals(str(remote.get("materialCode")))) {
                fields.add("materialCode");
            }
            if (!record.getDirection().equals(str(remote.get("direction")))) {
                fields.add("direction");
            }
            if (record.getQty().compareTo(num(remote.get("qty"))) != 0) {
                fields.add("qty");
            }
            if (fields.isEmpty()) {
                matched++;
            } else {
                diffs.add(diffRow("字段差异:" + String.join(",", fields), record.getRecordNo(),
                        record.getMaterialCode(), record.getQty(), remote.get("qty")));
            }
        }
        for (String recordNo : tplus.keySet()) {
            if (!localNos.contains(recordNo)) {
                diffs.add(diffRow("T+多出", recordNo, str(tplus.get(recordNo).get("materialCode")),
                        null, tplus.get(recordNo).get("qty")));
            }
        }
        TplusReconciliation batch = new TplusReconciliation();
        batch.setBatchNo("REC-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
        batch.setReconcileDate(LocalDate.now());
        batch.setLocalCount(pushed.size());
        batch.setTplusCount(tplus.size());
        batch.setMatchedCount(matched);
        batch.setDiffCount(diffs.size());
        batch.setStatus("DONE");
        try {
            batch.setDiffJson(diffs.isEmpty() ? "[]" : objectMapper.writeValueAsString(diffs));
        } catch (Exception e) {
            batch.setDiffJson("[]");
        }
        reconMapper.insert(batch);
        log.info("T+ 对账完成: 批次 {} 本地 {} T+ {} 一致 {} 差异 {}",
                batch.getBatchNo(), pushed.size(), tplus.size(), matched, diffs.size());
        return batch;
    }

    /** T+ 推送成功率（已推送 / 已推送+失败），无记录时按 100% */
    public BigDecimal tplusSuccessRate() {
        Long pushed = queueMapper.selectCount(new LambdaQueryWrapper<TplusPushQueue>()
                .eq(TplusPushQueue::getStatus, "PUSHED"));
        Long failed = queueMapper.selectCount(new LambdaQueryWrapper<TplusPushQueue>()
                .eq(TplusPushQueue::getStatus, "FAILED"));
        int total = pushed.intValue() + failed.intValue();
        return total == 0 ? BigDecimal.valueOf(100) : BigDecimal.valueOf(pushed * 100L)
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }

    public PageResult<InventoryRecord> listRecords(int pageNum, int pageSize, Long projectId,
                                                   String direction, String tplusStatus) {
        LambdaQueryWrapper<InventoryRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, InventoryRecord::getProjectId, projectId);
        wrapper.eq(direction != null && !direction.isBlank(), InventoryRecord::getDirection, direction);
        wrapper.eq(tplusStatus != null && !tplusStatus.isBlank(), InventoryRecord::getTplusStatus, tplusStatus);
        wrapper.orderByDesc(InventoryRecord::getId);
        return PageResult.of(recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public PageResult<TplusPushQueue> listQueue(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<TplusPushQueue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), TplusPushQueue::getStatus, status);
        wrapper.orderByDesc(TplusPushQueue::getId);
        return PageResult.of(queueMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public List<TplusReconciliation> listReconciliations() {
        return reconMapper.selectList(new LambdaQueryWrapper<TplusReconciliation>()
                .orderByDesc(TplusReconciliation::getId).last("LIMIT 50"));
    }

    // ===== 内部工具 =====

    /** T+ 凭证号：出入库单号 IO-yyyymm-seq → TV-yyyymm-seq（确定性 1:1） */
    private String tplusVoucherNo(String recordNo) {
        return "TV" + recordNo.substring(2);
    }

    private Path outboxFile() {
        return Paths.get(adapterDir, "outbox", "push_tplus.jsonl").toAbsolutePath();
    }

    private Path inboundFile() {
        return Paths.get(adapterDir, "inbound", "tplus_export.jsonl").toAbsolutePath();
    }

    private void appendLine(Path file, String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new BusinessException(500, "T+ 文件适配器写入失败: " + e.getMessage());
        }
    }

    private Map<String, Map<String, Object>> readInboundMap() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        Path inbound = inboundFile();
        if (!Files.exists(inbound)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(inbound, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> row = objectMapper.readValue(line, Map.class);
                map.put(str(row.get("recordNo")), row);
            }
        } catch (Exception e) {
            throw new BusinessException(500, "T+ 回执文件解析失败: " + e.getMessage());
        }
        return map;
    }

    private Map<String, Object> diffRow(String type, String recordNo, String materialCode,
                                        Object localQty, Object remoteQty) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("recordNo", recordNo);
        row.put("materialCode", materialCode);
        row.put("localQty", localQty);
        row.put("tplusQty", remoteQty);
        return row;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal num(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
