package com.dept.mgmt.workshop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.PrototypeAcceptanceMapper;
import com.dept.mgmt.mapper.DisclosureRecordMapper;
import com.dept.mgmt.mapper.KittingCheckMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.workshop.entity.ArrivalRecord;
import com.dept.mgmt.workshop.entity.DisclosureRecord;
import com.dept.mgmt.workshop.entity.FaiReport;
import com.dept.mgmt.workshop.entity.KittingCheck;
import com.dept.mgmt.workshop.entity.PrototypeAcceptance;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import com.dept.mgmt.workshop.entity.QualityCheck;
import com.dept.mgmt.workshop.entity.Rectification;
import com.dept.mgmt.workshop.entity.WorkOrder;
import com.dept.mgmt.mapper.ArrivalRecordMapper;
import com.dept.mgmt.mapper.FaiReportMapper;
import com.dept.mgmt.mapper.PurchaseOrderMapper;
import com.dept.mgmt.mapper.QualityCheckMapper;
import com.dept.mgmt.mapper.RectificationMapper;
import com.dept.mgmt.mapper.WorkOrderMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * W4 样机域服务（T-201）
 * 硬卡点内嵌（合规底线：卡点前置在动作入口，不是事后检查）：
 * BR-01 技改项目样机验收未通过 → 批量工单不允许创建
 * BR-08 最新齐套审查 <95% → 生产工单不允许开工
 * BR-09 工单技术交底未签字 → 生产工单不允许开工
 * （BR-02 首次发货 FAI 拦截在 W6 发货单提交入口落地，T-206）
 * 到货登记发布 material.arrived 领域事件（联动齐套复核）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkshopService {

    /** BR-08 齐套率开工阈值（规划口径 95%） */
    private static final BigDecimal KITTING_THRESHOLD = new BigDecimal("95.00");

    private final WorkOrderMapper workOrderMapper;
    private final KittingCheckMapper kittingCheckMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final ArrivalRecordMapper arrivalRecordMapper;
    private final DisclosureRecordMapper disclosureMapper;
    private final FaiReportMapper faiReportMapper;
    private final QualityCheckMapper qualityCheckMapper;
    private final RectificationMapper rectificationMapper;
    private final PrototypeAcceptanceMapper acceptanceMapper;
    private final ProjectMapper projectMapper;
    private final com.dept.mgmt.mapper.DesignSchemeMapper schemeMapper;
    private final com.dept.mgmt.mapper.DesignBomItemMapper bomItemMapper;
    private final CodeEngineService codeEngineService;
    private final EventBusService eventBusService;

    // ===== 工单（BR-01/08/09 载体）=====

    /**
     * 创建工单。BR-01：技改项目在样机验收通过前不允许创建批量工单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createWorkOrder(Long projectId, String orderType, Integer qty, String remark) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        boolean batch = "BATCH".equals(orderType);
        if (batch && project.getProjectType() != null && project.getProjectType() == 2) {
            // BR-01：技改项目须先有 PASS 的样机验收
            Long passCount = acceptanceMapper.selectCount(new LambdaQueryWrapper<PrototypeAcceptance>()
                    .eq(PrototypeAcceptance::getProjectId, projectId)
                    .eq(PrototypeAcceptance::getResult, "PASS"));
            if (passCount == null || passCount == 0) {
                throw new BusinessException(400, "硬卡点 BR-01 拦截：技改项目样机验收未通过，不允许创建批量工单");
            }
        }
        WorkOrder wo = new WorkOrder();
        wo.setWoNo(codeEngineService.nextCode("WO", null));
        wo.setProjectId(projectId);
        wo.setOrderType(orderType == null ? "PROTOTYPE" : orderType);
        wo.setQty(qty == null || qty < 1 ? 1 : qty);
        wo.setStatus("DRAFT");
        wo.setRemark(remark);
        workOrderMapper.insert(wo);
        log.info("工单创建: {} ({}) projectId={}", wo.getWoNo(), wo.getOrderType(), projectId);
        return wo.getId();
    }

    /**
     * 工单开工。BR-08 + BR-09 内嵌拦截
     */
    @Transactional(rollbackFor = Exception.class)
    public void startWorkOrder(Long workorderId) {
        WorkOrder wo = mustExistWo(workorderId);
        if (!"DRAFT".equals(wo.getStatus())) {
            throw new BusinessException(400, "仅草稿工单可开工，当前: " + wo.getStatus());
        }
        // BR-08：齐套率 <95% 禁止开工（取项目最新一次审查）
        KittingCheck latest = kittingCheckMapper.selectOne(new LambdaQueryWrapper<KittingCheck>()
                .eq(KittingCheck::getProjectId, wo.getProjectId())
                .orderByDesc(KittingCheck::getId).last("LIMIT 1"));
        if (latest == null || latest.getKitRate() == null
                || latest.getKitRate().compareTo(KITTING_THRESHOLD) < 0) {
            throw new BusinessException(400, "硬卡点 BR-08 拦截：齐套率未达 95%（"
                    + (latest == null ? "未审查" : latest.getKitRate() + "%") + "），生产工单不允许开工");
        }
        // BR-09：技术交底未签字禁止开工（语义：该工单的交底记录，或项目级未挂工单的交底记录，
        // 任一已签字即放行；Phase 2 精确到工单）
        Long signed = disclosureMapper.selectCount(new LambdaQueryWrapper<DisclosureRecord>()
                .eq(DisclosureRecord::getSigned, 1)
                .and(w -> w.eq(DisclosureRecord::getWorkorderId, workorderId)
                        .or().isNull(DisclosureRecord::getWorkorderId)));
        if (signed == null || signed == 0) {
            throw new BusinessException(400, "硬卡点 BR-09 拦截：技术交底记录未签字，生产工单不允许开工");
        }
        wo.setStatus("STARTED");
        wo.setStartedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
        log.info("工单开工（BR-08/09 校验通过）: {}", wo.getWoNo());
    }

    /**
     * 工单完工
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeWorkOrder(Long workorderId) {
        WorkOrder wo = mustExistWo(workorderId);
        if (!"STARTED".equals(wo.getStatus())) {
            throw new BusinessException(400, "仅已开工工单可完工，当前: " + wo.getStatus());
        }
        wo.setStatus("CLOSED");
        wo.setClosedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
    }

    public PageResult<WorkOrder> listWorkOrders(int pageNum, int pageSize, Long projectId, String status) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, WorkOrder::getProjectId, projectId);
        wrapper.eq(status != null && !status.isBlank(), WorkOrder::getStatus, status);
        wrapper.orderByDesc(WorkOrder::getId);
        return PageResult.of(workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    // ===== 齐套审查 =====

    /** 单行缺料明细（AGT-13/14 共用） */
    public record Shortage(String materialCode, String materialName, java.math.BigDecimal required,
                           java.math.BigDecimal arrived, java.math.BigDecimal shortage) {
    }

    /** 齐套计算结果（AGT-13/14 共用） */
    public record KittingCalc(BigDecimal kitRate, List<Shortage> shortages, int totalItems) {
    }

    /**
     * 齐套确定性计算（T-202 AGT-13/14 共用）：
     * 需求 = 项目最新设计方案的 BOM 明细；供给 = PO 已到货数量按物料汇总；
     * 齐套率 = 满足需求（到货≥需求）的行数占比；不经 LLM 保证可复现
     */
    public KittingCalc calculateKitting(Long projectId) {
        com.dept.mgmt.design.entity.DesignScheme scheme = schemeMapper.selectOne(
                new LambdaQueryWrapper<com.dept.mgmt.design.entity.DesignScheme>()
                        .eq(com.dept.mgmt.design.entity.DesignScheme::getProjectId, projectId)
                        .orderByDesc(com.dept.mgmt.design.entity.DesignScheme::getId).last("LIMIT 1"));
        if (scheme == null) {
            return new KittingCalc(BigDecimal.ZERO, List.of(new Shortage("", "项目暂无设计方案，无法核算物料需求",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)), 0);
        }
        List<com.dept.mgmt.design.entity.DesignBomItem> bom = bomItemMapper.selectList(
                new LambdaQueryWrapper<com.dept.mgmt.design.entity.DesignBomItem>()
                        .eq(com.dept.mgmt.design.entity.DesignBomItem::getSchemeId, scheme.getId()));
        if (bom.isEmpty()) {
            return new KittingCalc(BigDecimal.ZERO, List.of(new Shortage("", "设计方案 BOM 为空",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)), 0);
        }
        // 供给汇总（按物料编码）
        java.util.Map<String, BigDecimal> supply = new java.util.HashMap<>();
        for (PurchaseOrder po : purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getProjectId, projectId))) {
            supply.merge(po.getMaterialCode(), po.getArrivedQty() == null ? BigDecimal.ZERO : po.getArrivedQty(),
                    BigDecimal::add);
        }
        List<Shortage> shortages = new java.util.ArrayList<>();
        int satisfied = 0;
        for (var item : bom) {
            BigDecimal required = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
            BigDecimal arrived = supply.getOrDefault(item.getMaterialCode(), BigDecimal.ZERO);
            if (arrived.compareTo(required) >= 0) {
                satisfied++;
            } else {
                shortages.add(new Shortage(item.getMaterialCode(), item.getMaterialName(),
                        required, arrived, required.subtract(arrived)));
            }
        }
        BigDecimal rate = BigDecimal.valueOf(satisfied * 100L)
                .divide(BigDecimal.valueOf(bom.size()), 2, java.math.RoundingMode.HALF_UP);
        return new KittingCalc(rate, shortages, bom.size());
    }

    /**
     * 录入齐套审查结果（T-202 AGT-13 将自动化；阈值 95%）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long runKitting(Long projectId, BigDecimal kitRate, String detailJson, String checker) {
        KittingCheck check = new KittingCheck();
        check.setProjectId(projectId);
        check.setKitRate(kitRate);
        check.setResult(kitRate.compareTo(KITTING_THRESHOLD) >= 0 ? "PASS" : "BLOCK");
        check.setDetailJson(detailJson);
        check.setChecker(checker == null ? "MANUAL" : checker);
        kittingCheckMapper.insert(check);
        log.info("齐套审查: projectId={} rate={} → {}", projectId, kitRate, check.getResult());
        return check.getId();
    }

    public List<KittingCheck> listKitting(Long projectId) {
        return kittingCheckMapper.selectList(new LambdaQueryWrapper<KittingCheck>()
                .eq(KittingCheck::getProjectId, projectId).orderByDesc(KittingCheck::getId));
    }

    // ===== PO / 到货 =====

    @Transactional(rollbackFor = Exception.class)
    public Long createPo(Long projectId, String materialCode, String materialName, BigDecimal qty, String supplier) {
        PurchaseOrder po = buildPo(projectId, materialCode, materialName, qty, supplier);
        po.setStatus("SUBMITTED");
        purchaseOrderMapper.insert(po);
        log.info("PO 创建: {} {} x{}", po.getPoNo(), materialCode, qty);
        return po.getId();
    }

    /**
     * 请购草稿（AGT-14 产出）：DRAFT 状态，下达需人工（草稿不参与到货与齐套供给）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDraftPo(Long projectId, String materialCode, String materialName, BigDecimal qty) {
        PurchaseOrder po = buildPo(projectId, materialCode, materialName, qty, null);
        po.setStatus("DRAFT");
        purchaseOrderMapper.insert(po);
        log.info("请购草稿创建: {} {} x{}", po.getPoNo(), materialCode, qty);
        return po.getId();
    }

    private PurchaseOrder buildPo(Long projectId, String materialCode, String materialName, BigDecimal qty, String supplier) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(codeEngineService.nextCode("PO", null));
        po.setProjectId(projectId);
        po.setMaterialCode(materialCode);
        po.setMaterialName(materialName);
        po.setQty(qty);
        po.setArrivedQty(BigDecimal.ZERO);
        po.setSupplier(supplier);
        return po;
    }

    public PageResult<PurchaseOrder> listPos(int pageNum, int pageSize, Long projectId) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, PurchaseOrder::getProjectId, projectId);
        wrapper.orderByDesc(PurchaseOrder::getId);
        return PageResult.of(purchaseOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 到货登记：PO 已到数量累加，齐量自动完结；发布 material.arrived 事件
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerArrival(Long poId, BigDecimal qty, String remark) {
        PurchaseOrder po = purchaseOrderMapper.selectById(poId);
        if (po == null) {
            throw new BusinessException(404, "PO 不存在: " + poId);
        }
        if ("CLOSED".equals(po.getStatus())) {
            throw new BusinessException(400, "PO 已完结，不接受到货登记");
        }
        LoginUser user = LoginUser.current();
        ArrivalRecord record = new ArrivalRecord();
        record.setPoId(poId);
        record.setProjectId(po.getProjectId());
        record.setMaterialCode(po.getMaterialCode());
        record.setQty(qty);
        record.setOperator(user == null ? "系统" : user.getUsername());
        record.setRemark(remark);
        arrivalRecordMapper.insert(record);

        po.setArrivedQty(po.getArrivedQty().add(qty));
        if (po.getArrivedQty().compareTo(po.getQty()) >= 0) {
            po.setStatus("CLOSED");
        }
        purchaseOrderMapper.updateById(po);
        // 跨域联动：到货 → 齐套复核（W4 内）与看板
        eventBusService.publish(DomainEventType.MATERIAL_ARRIVED, java.util.Map.of(
                "poId", po.getId(),
                "projectId", po.getProjectId(),
                "materialCode", po.getMaterialCode(),
                "qty", qty));
        log.info("到货登记: {} {} +{}，PO 状态 {}", po.getPoNo(), po.getMaterialCode(), qty, po.getStatus());
        return record.getId();
    }

    public List<ArrivalRecord> listArrivals(Long poId) {
        return arrivalRecordMapper.selectList(new LambdaQueryWrapper<ArrivalRecord>()
                .eq(poId != null, ArrivalRecord::getPoId, poId).orderByDesc(ArrivalRecord::getId));
    }

    // ===== 交底 =====

    @Transactional(rollbackFor = Exception.class)
    public Long createDisclosure(Long projectId, Long workorderId, String discloser, String receivers, String content) {
        DisclosureRecord record = new DisclosureRecord();
        record.setProjectId(projectId);
        record.setWorkorderId(workorderId);
        record.setDiscloser(discloser);
        record.setReceivers(receivers);
        record.setSigned(0);
        record.setContent(content);
        disclosureMapper.insert(record);
        return record.getId();
    }

    /**
     * 交底签字确认（BR-09 放行条件）
     */
    @Transactional(rollbackFor = Exception.class)
    public void signDisclosure(Long id) {
        DisclosureRecord record = disclosureMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "交底记录不存在");
        }
        record.setSigned(1);
        record.setSignTime(LocalDateTime.now());
        disclosureMapper.updateById(record);
        log.info("交底签字确认: id={}", id);
    }

    // ===== FAI / 质检 / 整改 / 验收 =====

    @Transactional(rollbackFor = Exception.class)
    public Long createFai(Long workorderId, String result, String inspector, String content) {
        WorkOrder wo = mustExistWo(workorderId);
        FaiReport report = new FaiReport();
        report.setWorkorderId(workorderId);
        report.setProjectId(wo.getProjectId());
        report.setResult(result);
        report.setInspector(inspector);
        report.setContent(content);
        faiReportMapper.insert(report);
        if ("FAIL".equals(result)) {
            createRectification(wo.getProjectId(), "QC", report.getId(), "FAI 首件检验不合格，请整改后复检", null, null);
        }
        return report.getId();
    }

    public List<FaiReport> listFai(Long projectId) {
        return faiReportMapper.selectList(new LambdaQueryWrapper<FaiReport>()
                .eq(FaiReport::getProjectId, projectId).orderByDesc(FaiReport::getId));
    }

    /**
     * 工序质检：FAIL 必填缺陷描述并自动生成整改单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createQualityCheck(Long workorderId, String stage, String result, String checker, String remark) {
        WorkOrder wo = mustExistWo(workorderId);
        if ("FAIL".equals(result) && (remark == null || remark.isBlank())) {
            throw new BusinessException(400, "质检 FAIL 时必须填写缺陷描述");
        }
        QualityCheck qc = new QualityCheck();
        qc.setWorkorderId(workorderId);
        qc.setStage(stage);
        qc.setResult(result);
        qc.setChecker(checker);
        qc.setRemark(remark);
        qualityCheckMapper.insert(qc);
        if ("FAIL".equals(result)) {
            createRectification(wo.getProjectId(), "QC", qc.getId(), remark, null, null);
        }
        return qc.getId();
    }

    public List<QualityCheck> listQuality(Long workorderId) {
        return qualityCheckMapper.selectList(new LambdaQueryWrapper<QualityCheck>()
                .eq(QualityCheck::getWorkorderId, workorderId).orderByDesc(QualityCheck::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRectification(Long projectId, String source, Long sourceId, String issue, String owner, java.time.LocalDate dueDate) {
        Rectification rect = new Rectification();
        rect.setProjectId(projectId);
        rect.setSource(source);
        rect.setSourceId(sourceId);
        rect.setIssue(issue);
        rect.setOwner(owner);
        rect.setDueDate(dueDate);
        rect.setStatus("OPEN");
        rectificationMapper.insert(rect);
        return rect.getId();
    }

    public PageResult<Rectification> listRectifications(int pageNum, int pageSize, Long projectId, String status) {
        LambdaQueryWrapper<Rectification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, Rectification::getProjectId, projectId);
        wrapper.eq(status != null && !status.isBlank(), Rectification::getStatus, status);
        wrapper.orderByDesc(Rectification::getId);
        return PageResult.of(rectificationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public void closeRectification(Long id, String closeNote) {
        Rectification rect = rectificationMapper.selectById(id);
        if (rect == null) {
            throw new BusinessException(404, "整改单不存在");
        }
        rect.setStatus("CLOSED");
        rect.setCloseNote(closeNote);
        rectificationMapper.updateById(rect);
    }

    /**
     * 样机验收登记（BR-01 数据源）：FAIL 派生整改单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long completeAcceptance(Long workorderId, String result, String acceptor, String comment) {
        WorkOrder wo = mustExistWo(workorderId);
        if (!List.of("PASS", "FAIL", "PENDING").contains(result)) {
            throw new BusinessException(400, "验收结论仅允许 PENDING/PASS/FAIL");
        }
        PrototypeAcceptance acceptance = new PrototypeAcceptance();
        acceptance.setProjectId(wo.getProjectId());
        acceptance.setWorkorderId(workorderId);
        acceptance.setResult(result);
        acceptance.setAcceptor(acceptor);
        acceptance.setAcceptedAt("PENDING".equals(result) ? null : LocalDateTime.now());
        acceptance.setComment(comment);
        acceptanceMapper.insert(acceptance);
        if ("FAIL".equals(result)) {
            createRectification(wo.getProjectId(), "ACCEPT", acceptance.getId(),
                    "样机验收不合格: " + (comment == null ? "" : comment), null, null);
        }
        log.info("样机验收: woNo={} → {}", wo.getWoNo(), result);
        return acceptance.getId();
    }

    public List<PrototypeAcceptance> listAcceptances(Long projectId) {
        return acceptanceMapper.selectList(new LambdaQueryWrapper<PrototypeAcceptance>()
                .eq(PrototypeAcceptance::getProjectId, projectId).orderByDesc(PrototypeAcceptance::getId));
    }

    private WorkOrder mustExistWo(Long id) {
        WorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new BusinessException(404, "工单不存在: " + id);
        }
        return wo;
    }
}
