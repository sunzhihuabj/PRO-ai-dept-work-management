package com.dept.mgmt.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.cost.entity.CostLedger;
import com.dept.mgmt.cost.entity.CostSettlement;
import com.dept.mgmt.cost.entity.PriceModel;
import com.dept.mgmt.mapper.CostLedgerMapper;
import com.dept.mgmt.mapper.CostSettlementMapper;
import com.dept.mgmt.mapper.InventoryRecordMapper;
import com.dept.mgmt.mapper.MaterialMapper;
import com.dept.mgmt.mapper.PriceModelMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.PurchaseOrderMapper;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.mdm.material.entity.Material;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.workshop.entity.InventoryRecord;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * W7 成本域服务（T-303）
 * AGT-21 成本自动归集（Auto）：物料成本 = 入库数量 × 物料标准价，按出入库记录幂等归集；
 * AGT-22 毛利三口径分析（Ask）：
 * 三口径定义（透明披露，V2 F-07 原文未给计算式）：
 *   完全成本 = 成本台账全量（物料实际归集 + 人工 + 费用）；
 *   标准成本 = Σ(入库数量 × 物料标准价)，不含人工费用；
 *   现金支出 = 已完结 PO 部分入库数量 × 标准价（现金支出口径）；
 * 价差 = 完全成本物料部分 − 标准成本（正数为超标准支出）
 * F-08 报价模型更新：定稿核算单按项目类型聚合平均/最低毛利率
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostService {

    private final CostLedgerMapper ledgerMapper;
    private final CostSettlementMapper settlementMapper;
    private final PriceModelMapper priceModelMapper;
    private final InventoryRecordMapper inventoryRecordMapper;
    private final MaterialMapper materialMapper;
    private final ProjectMapper projectMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final CodeEngineService codeEngineService;

    // ===== AGT-21 成本自动归集 =====

    /**
     * 物料成本自动归集（幂等）：项目 IN 方向出入库记录 × 物料标准价 → cost_ledger
     * 无标准价物料跳过并列入 warnings（不编造金额）
     *
     * @return {collected 本次新归集行数, totalMaterial 物料成本合计, warnings}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> collectMaterialCost(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        List<InventoryRecord> records = inventoryRecordMapper.selectList(
                new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getProjectId, projectId)
                        .eq(InventoryRecord::getDirection, "IN")
                        .orderByAsc(InventoryRecord::getId));
        List<String> warnings = new ArrayList<>();
        int collected = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryRecord record : records) {
            Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                    .eq(Material::getMaterialCode, record.getMaterialCode()).last("LIMIT 1"));
            if (material == null || material.getStandardPrice() == null) {
                warnings.add("物料 " + record.getMaterialCode() + " 未设标准成本价，已跳过（单号 " + record.getRecordNo() + "）");
                continue;
            }
            CostLedger ledger = new CostLedger();
            ledger.setProjectId(projectId);
            ledger.setCostType("MATERIAL");
            ledger.setSourceType("AGENT");
            ledger.setSourceId(record.getId());
            ledger.setAmount(record.getQty().multiply(material.getStandardPrice()).setScale(2, RoundingMode.HALF_UP));
            ledger.setOccurredDate(record.getScannedAt() == null ? LocalDate.now() : record.getScannedAt().toLocalDate());
            ledger.setRemark("AGT-21 自动归集：" + record.getRecordNo() + " " + record.getMaterialCode()
                    + " × " + record.getQty() + " × " + material.getStandardPrice());
            try {
                ledgerMapper.insert(ledger);
                collected++;
                total = total.add(ledger.getAmount());
            } catch (DuplicateKeyException e) {
                // 幂等：已归集过的入库记录跳过
            }
        }
        BigDecimal ledgerTotal = sumLedger(projectId, "MATERIAL");
        log.info("AGT-21 物料成本归集: projectId={} 新归集 {} 行 物料成本合计 {}", projectId, collected, ledgerTotal);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("collected", collected);
        out.put("totalMaterial", ledgerTotal);
        out.put("warnings", warnings);
        return out;
    }

    /** 手工录入人工/费用成本 */
    @Transactional(rollbackFor = Exception.class)
    public Long addManualCost(Long projectId, String costType, BigDecimal amount, String remark) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        if (!List.of("MATERIAL", "LABOR", "EXPENSE").contains(costType)) {
            throw new BusinessException(400, "成本类型仅允许 MATERIAL/LABOR/EXPENSE");
        }
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException(400, "金额必须大于等于 0");
        }
        CostLedger ledger = new CostLedger();
        ledger.setProjectId(projectId);
        ledger.setCostType(costType);
        ledger.setSourceType("MANUAL");
        ledger.setAmount(amount);
        ledger.setOccurredDate(LocalDate.now());
        ledger.setRemark(remark);
        ledgerMapper.insert(ledger);
        return ledger.getId();
    }

    public List<CostLedger> listLedger(Long projectId) {
        return ledgerMapper.selectList(new LambdaQueryWrapper<CostLedger>()
                .eq(projectId != null, CostLedger::getProjectId, projectId)
                .orderByAsc(CostLedger::getId));
    }

    // ===== AGT-22 毛利三口径 =====

    /**
     * 完工成本核算 + 毛利三口径（每次核算生成新单，DRAFT 可反复核算，定稿后参与报价模型）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settleProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        BigDecimal material = sumLedger(projectId, "MATERIAL");
        BigDecimal labor = sumLedger(projectId, "LABOR");
        BigDecimal expense = sumLedger(projectId, "EXPENSE");
        BigDecimal full = material.add(labor).add(expense);

        // 标准成本口径：Σ(入库数量 × 标准价)
        BigDecimal standard = BigDecimal.ZERO;
        List<InventoryRecord> inRecords = inventoryRecordMapper.selectList(
                new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getProjectId, projectId)
                        .eq(InventoryRecord::getDirection, "IN"));
        for (InventoryRecord record : inRecords) {
            Material material2 = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                    .eq(Material::getMaterialCode, record.getMaterialCode()).last("LIMIT 1"));
            if (material2 != null && material2.getStandardPrice() != null) {
                standard = standard.add(record.getQty().multiply(material2.getStandardPrice()));
            }
        }
        standard = standard.setScale(2, RoundingMode.HALF_UP);
        // 现金支出口径：已完结 PO 部分入库 × 标准价
        BigDecimal cash = BigDecimal.ZERO;
        Map<String, PurchaseOrder> closedPos = purchaseOrderMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrder>()
                                .eq(PurchaseOrder::getProjectId, projectId)
                                .eq(PurchaseOrder::getStatus, "CLOSED"))
                .stream().collect(Collectors.toMap(PurchaseOrder::getMaterialCode, p -> p, (a, b) -> a));
        for (InventoryRecord record : inRecords) {
            PurchaseOrder po = closedPos.get(record.getMaterialCode());
            if (po != null) {
                Material material2 = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, record.getMaterialCode()).last("LIMIT 1"));
                if (material2 != null && material2.getStandardPrice() != null) {
                    cash = cash.add(record.getQty().multiply(material2.getStandardPrice()));
                }
            }
        }
        cash = cash.setScale(2, RoundingMode.HALF_UP);

        BigDecimal contractAmount = project.getContractAmount() == null ? BigDecimal.ZERO : project.getContractAmount();
        BigDecimal margin = contractAmount.compareTo(BigDecimal.ZERO) > 0
                ? contractAmount.subtract(full).divide(contractAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;
        BigDecimal priceDiff = material.subtract(standard); // 价差：实际物料 − 标准
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("fullCost", full);
        diff.put("standardCost", standard);
        diff.put("cashCost", cash);
        diff.put("priceDiff", priceDiff.setScale(2, RoundingMode.HALF_UP));
        diff.put("priceDiffNote", priceDiff.signum() > 0 ? "实际物料支出超标准 " + priceDiff + " 元"
                : (priceDiff.signum() < 0 ? "低于标准 " + priceDiff.abs() + " 元" : "与标准一致"));

        CostSettlement settlement = new CostSettlement();
        settlement.setSettleNo(codeEngineService.nextCode("CST", null));
        settlement.setProjectId(projectId);
        settlement.setMaterialCost(material);
        settlement.setLaborCost(labor);
        settlement.setExpenseCost(expense);
        settlement.setTotalCost(full);
        settlement.setStandardCost(standard);
        settlement.setCashCost(cash);
        settlement.setContractAmount(contractAmount);
        settlement.setGrossMargin(margin);
        try {
            settlement.setMarginDiffJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(diff));
        } catch (Exception e) {
            settlement.setMarginDiffJson("{}");
        }
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
        log.info("AGT-22 完工核算: projectId={} 完全 {} 标准 {} 现金 {} 毛利 {}",
                projectId, full, standard, cash, margin);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settleId", settlement.getId());
        out.put("settleNo", settlement.getSettleNo());
        out.put("projectId", projectId);
        out.put("contractAmount", contractAmount);
        out.putAll(diff);
        out.put("grossMargin", margin);
        out.put("status", settlement.getStatus());
        return out;
    }

    /** 定稿（CONFIRMED，参与报价模型） */
    @Transactional(rollbackFor = Exception.class)
    public void confirmSettlement(Long settleId) {
        CostSettlement settlement = settlementMapper.selectById(settleId);
        if (settlement == null) {
            throw new BusinessException(404, "核算单不存在: " + settleId);
        }
        if ("CONFIRMED".equals(settlement.getStatus())) {
            throw new BusinessException(400, "核算单已定稿");
        }
        settlement.setStatus("CONFIRMED");
        settlement.setSettledAt(LocalDateTime.now());
        settlementMapper.updateById(settlement);
    }

    // ===== F-08 报价模型更新 =====

    /** 报价模型更新：按项目类型聚合定稿核算单毛利率（每次全量重算 version+1） */
    @Transactional(rollbackFor = Exception.class)
    public List<PriceModel> refreshPriceModel() {
        List<CostSettlement> confirmed = settlementMapper.selectList(
                new LambdaQueryWrapper<CostSettlement>().eq(CostSettlement::getStatus, "CONFIRMED"));
        Map<Integer, List<CostSettlement>> byType = new LinkedHashMap<>();
        for (CostSettlement s : confirmed) {
            Project project = projectMapper.selectById(s.getProjectId());
            if (project == null || project.getProjectType() == null
                    || s.getGrossMargin() == null || s.getContractAmount() == null
                    || s.getContractAmount().signum() <= 0) {
                continue;
            }
            byType.computeIfAbsent(project.getProjectType(), k -> new ArrayList<>()).add(s);
        }
        List<PriceModel> result = new ArrayList<>();
        for (Map.Entry<Integer, List<CostSettlement>> entry : byType.entrySet()) {
            List<BigDecimal> margins = entry.getValue().stream()
                    .map(CostSettlement::getGrossMargin).sorted().collect(Collectors.toList());
            BigDecimal avg = margins.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(margins.size()), 2, RoundingMode.HALF_UP);
            PriceModel existing = priceModelMapper.selectOne(new LambdaQueryWrapper<PriceModel>()
                    .eq(PriceModel::getProjectType, entry.getKey()).last("LIMIT 1"));
            PriceModel model = existing == null ? new PriceModel() : existing;
            model.setProjectType(entry.getKey());
            model.setAvgMargin(avg);
            model.setMinMargin(margins.get(0));
            model.setSampleCount(margins.size());
            model.setVersion(existing == null ? 1 : existing.getVersion() + 1);
            model.setUpdatedAt(LocalDateTime.now());
            if (existing == null) {
                priceModelMapper.insert(model);
            } else {
                priceModelMapper.updateById(model);
            }
            result.add(model);
        }
        log.info("报价模型更新: 覆盖 {} 个项目类型", result.size());
        return result;
    }

    public List<PriceModel> listPriceModels() {
        return priceModelMapper.selectList(new LambdaQueryWrapper<PriceModel>()
                .orderByAsc(PriceModel::getProjectType));
    }

    private BigDecimal sumLedger(Long projectId, String costType) {
        return ledgerMapper.selectList(new LambdaQueryWrapper<CostLedger>()
                        .eq(CostLedger::getProjectId, projectId)
                        .eq(CostLedger::getCostType, costType))
                .stream().map(CostLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
