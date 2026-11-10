package com.dept.mgmt.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.board.entity.Redcard;
import com.dept.mgmt.board.entity.RedcardRule;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.design.entity.DesignScheme;
import com.dept.mgmt.delivery.entity.Acceptance;
import com.dept.mgmt.delivery.entity.ConstructionLog;
import com.dept.mgmt.delivery.entity.Shipment;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.AcceptanceMapper;
import com.dept.mgmt.mapper.ConstructionLogMapper;
import com.dept.mgmt.mapper.ContractMapper;
import com.dept.mgmt.mapper.DesignSchemeMapper;
import com.dept.mgmt.mapper.DisclosureRecordMapper;
import com.dept.mgmt.mapper.FaiReportMapper;
import com.dept.mgmt.mapper.InventoryRecordMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.PurchaseOrderMapper;
import com.dept.mgmt.mapper.QualityCheckMapper;
import com.dept.mgmt.mapper.RectificationMapper;
import com.dept.mgmt.mapper.RedcardMapper;
import com.dept.mgmt.mapper.RedcardRuleMapper;
import com.dept.mgmt.mapper.ShipmentMapper;
import com.dept.mgmt.mapper.TplusReconciliationMapper;
import com.dept.mgmt.mapper.WorkOrderMapper;
import com.dept.mgmt.workshop.entity.DisclosureRecord;
import com.dept.mgmt.workshop.entity.FaiReport;
import com.dept.mgmt.workshop.entity.KittingCheck;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import com.dept.mgmt.workshop.entity.QualityCheck;
import com.dept.mgmt.workshop.entity.Rectification;
import com.dept.mgmt.workshop.entity.WorkOrder;
import com.dept.mgmt.workshop.entity.InventoryRecord;
import com.dept.mgmt.workshop.entity.TplusReconciliation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M1 看板服务（T-209）：九板数据集 + 三级视图 + 红灯引擎（规则配置化）+ 会议模式
 * 九板口径自主制定并披露（V2 仅定义"九板+三级视图+红灯引擎+会议模式"框架）：
 * CONTRACT 投标签约 / DESIGN 设计交付 / KITTING 齐套采购 / PRODUCE 生产质检 / SHIP 发货物流 /
 * ACCEPT 验收收款 / TPLUS 扫码过账 / RECTIFY 整改风险 / OVERVIEW 总览驾驶舱
 * 红灯引擎：全部指标确定性 SQL 统计，不经 LLM；红灯 OPEN 超 escalate_days 未处理 → ESCALATED
 * 并发布 redcard.escalated 事件（升级推送）；指标恢复自动 RESOLVED
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardsService {

    /** 九板定义（编码 → 中文名） */
    public static final Map<String, String> BOARDS = new LinkedHashMap<>();

    static {
        BOARDS.put("CONTRACT", "投标签约板");
        BOARDS.put("DESIGN", "设计交付板");
        BOARDS.put("KITTING", "齐套采购板");
        BOARDS.put("PRODUCE", "生产质检板");
        BOARDS.put("SHIP", "发货物流板");
        BOARDS.put("ACCEPT", "验收收款板");
        BOARDS.put("TPLUS", "扫码过账板");
        BOARDS.put("RECTIFY", "整改风险板");
        BOARDS.put("OVERVIEW", "总览驾驶舱");
    }

    /** 指标中文名（看板展示） */
    private static final Map<String, String> METRIC_LABELS = new LinkedHashMap<>();

    static {
        METRIC_LABELS.put("contract.pending_count", "待会签/审批/用印合同");
        METRIC_LABELS.put("contract.effective_count", "生效合同");
        METRIC_LABELS.put("design.draft_count", "方案草稿待发布");
        METRIC_LABELS.put("design.released_count", "已受控发布方案");
        METRIC_LABELS.put("kitting.block_count", "齐套率未达标项目");
        METRIC_LABELS.put("po.pending_count", "在途 PO");
        METRIC_LABELS.put("po.overdue_count", "逾期 PO");
        METRIC_LABELS.put("wo.started_count", "在产工单");
        METRIC_LABELS.put("disclosure.unsigned_count", "交底未签字");
        METRIC_LABELS.put("qc.fail_count", "质检/FAI 不合格");
        METRIC_LABELS.put("shipment.submitted_count", "已发运批次");
        METRIC_LABELS.put("acceptance.pending_sign", "验收单待签字");
        METRIC_LABELS.put("acceptance.pass_count", "已生效验收单");
        METRIC_LABELS.put("inventory.pending_push", "T+ 待推送单");
        METRIC_LABELS.put("inventory.push_failed", "T+ 推送失败单");
        METRIC_LABELS.put("recon.diff_count", "最近对账差异单");
        METRIC_LABELS.put("rectification.open_count", "整改单未闭环");
        METRIC_LABELS.put("rectification.overdue_count", "整改单逾期");
        METRIC_LABELS.put("project.total_count", "在库项目总数");
        METRIC_LABELS.put("redcard.open_count", "红灯未消除");
        METRIC_LABELS.put("redcard.escalated_count", "红灯已升级");
    }

    private final RedcardRuleMapper ruleMapper;
    private final RedcardMapper redcardMapper;
    private final ContractMapper contractMapper;
    private final DesignSchemeMapper schemeMapper;
    private final com.dept.mgmt.mapper.KittingCheckMapper kittingCheckMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final WorkOrderMapper workOrderMapper;
    private final DisclosureRecordMapper disclosureMapper;
    private final QualityCheckMapper qualityCheckMapper;
    private final FaiReportMapper faiReportMapper;
    private final ShipmentMapper shipmentMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final InventoryRecordMapper inventoryRecordMapper;
    private final TplusReconciliationMapper reconMapper;
    private final RectificationMapper rectificationMapper;
    private final ProjectMapper projectMapper;
    private final ConstructionLogMapper constructionLogMapper;
    private final EventBusService eventBusService;

    /** 会议模式开关（早会过堂场景，内存态，重启复位） */
    private final AtomicBoolean meetingMode = new AtomicBoolean(false);

    // ===== 指标字典（确定性 SQL 统计）=====

    /** 全量指标当前值 */
    public Map<String, BigDecimal> computeMetrics() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        // 投标签约板
        m.put("contract.pending_count", cnt(contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .in(Contract::getStatus, List.of("COUNTERSIGN", "APPROVING", "SEALING")))));
        m.put("contract.effective_count", cnt(contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "EFFECTIVE"))));
        // 设计交付板
        m.put("design.draft_count", cnt(schemeMapper.selectCount(new LambdaQueryWrapper<DesignScheme>()
                .eq(DesignScheme::getStatus, "DRAFT"))));
        m.put("design.released_count", cnt(schemeMapper.selectCount(new LambdaQueryWrapper<DesignScheme>()
                .eq(DesignScheme::getStatus, "RELEASED"))));
        // 齐套采购板
        m.put("kitting.block_count", BigDecimal.valueOf(latestKittingBlockedProjects()));
        m.put("po.pending_count", cnt(purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, "SUBMITTED"))));
        m.put("po.overdue_count", cnt(purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, "SUBMITTED")
                .isNotNull(PurchaseOrder::getExpectedDate)
                .lt(PurchaseOrder::getExpectedDate, LocalDate.now()))));
        // 生产质检板
        m.put("wo.started_count", cnt(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, "STARTED"))));
        m.put("disclosure.unsigned_count", cnt(disclosureMapper.selectCount(new LambdaQueryWrapper<DisclosureRecord>()
                .eq(DisclosureRecord::getSigned, 0))));
        m.put("qc.fail_count", cnt(qualityCheckMapper.selectCount(new LambdaQueryWrapper<QualityCheck>()
                .eq(QualityCheck::getResult, "FAIL")))
                .add(cnt(faiReportMapper.selectCount(new LambdaQueryWrapper<FaiReport>()
                        .eq(FaiReport::getResult, "FAIL")))));
        // 发货物流板
        m.put("shipment.submitted_count", cnt(shipmentMapper.selectCount(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getStatus, "SUBMITTED"))));
        // 验收收款板
        m.put("acceptance.pending_sign", cnt(acceptanceMapper.selectCount(new LambdaQueryWrapper<Acceptance>()
                .eq(Acceptance::getStatus, "PENDING_SIGN"))));
        m.put("acceptance.pass_count", cnt(acceptanceMapper.selectCount(new LambdaQueryWrapper<Acceptance>()
                .eq(Acceptance::getStatus, "PASS"))));
        // 扫码过账板
        m.put("inventory.pending_push", cnt(inventoryRecordMapper.selectCount(new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTplusStatus, "PENDING"))));
        m.put("inventory.push_failed", cnt(inventoryRecordMapper.selectCount(new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTplusStatus, "FAILED"))));
        TplusReconciliation latestRecon = reconMapper.selectOne(new LambdaQueryWrapper<TplusReconciliation>()
                .orderByDesc(TplusReconciliation::getId).last("LIMIT 1"));
        m.put("recon.diff_count", latestRecon == null || latestRecon.getDiffCount() == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(latestRecon.getDiffCount()));
        // 整改风险板
        m.put("rectification.open_count", cnt(rectificationMapper.selectCount(new LambdaQueryWrapper<Rectification>()
                .eq(Rectification::getStatus, "OPEN"))));
        m.put("rectification.overdue_count", cnt(rectificationMapper.selectCount(new LambdaQueryWrapper<Rectification>()
                .eq(Rectification::getStatus, "OPEN")
                .isNotNull(Rectification::getDueDate)
                .lt(Rectification::getDueDate, LocalDate.now()))));
        // 总览驾驶舱
        m.put("project.total_count", cnt(projectMapper.selectCount(null)));
        m.put("redcard.open_count", cnt(redcardMapper.selectCount(new LambdaQueryWrapper<Redcard>()
                .in(Redcard::getStatus, List.of("OPEN", "ASSIGNED")))));
        m.put("redcard.escalated_count", cnt(redcardMapper.selectCount(new LambdaQueryWrapper<Redcard>()
                .eq(Redcard::getStatus, "ESCALATED"))));
        return m;
    }

    /** 每项目取最新齐套审查，统计 BLOCK 项目数 */
    private int latestKittingBlockedProjects() {
        Map<Long, KittingCheck> latestByProject = new LinkedHashMap<>();
        for (KittingCheck check : kittingCheckMapper.selectList(new LambdaQueryWrapper<KittingCheck>()
                .orderByAsc(KittingCheck::getId))) {
            latestByProject.put(check.getProjectId(), check);
        }
        return (int) latestByProject.values().stream()
                .filter(k -> "BLOCK".equals(k.getResult())).count();
    }

    private BigDecimal cnt(Long count) {
        return BigDecimal.valueOf(count == null ? 0 : count);
    }

    // ===== 九板数据集（三级视图）=====

    /**
     * 九板数据集（三级视图）：
     * viewLevel=company 全部九板指标汇总；viewLevel=board 单板指标 + 活跃红灯；
     * viewLevel=project 单项目核心指标（齐套/工程量/验收/发货/整改）
     */
    public Map<String, Object> boards(String viewLevel, String board, Long projectId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if ("project".equals(viewLevel)) {
            if (projectId == null) {
                throw new BusinessException(400, "项目级视图必须传 projectId");
            }
            out.put("viewLevel", "project");
            out.put("projectId", projectId);
            out.put("metrics", projectMetrics(projectId));
            return out;
        }
        if ("board".equals(viewLevel)) {
            if (board == null || !BOARDS.containsKey(board)) {
                throw new BusinessException(400, "板块级视图必须传有效 board，可选: " + BOARDS.keySet());
            }
            Map<String, BigDecimal> metrics = computeMetrics();
            out.put("viewLevel", "board");
            out.put("board", board);
            out.put("boardName", BOARDS.get(board));
            out.put("metrics", metricsOfBoard(board, metrics));
            out.put("redcards", redcardMapper.selectList(new LambdaQueryWrapper<Redcard>()
                    .eq(Redcard::getBoard, board)
                    .in(Redcard::getStatus, List.of("OPEN", "ASSIGNED", "ESCALATED"))
                    .orderByDesc(Redcard::getId)));
            return out;
        }
        // company：九板全量
        Map<String, BigDecimal> metrics = computeMetrics();
        List<Map<String, Object>> boards = new ArrayList<>();
        for (Map.Entry<String, String> entry : BOARDS.entrySet()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("board", entry.getKey());
            b.put("name", entry.getValue());
            b.put("metrics", metricsOfBoard(entry.getKey(), metrics));
            b.put("openRedcards", cnt(redcardMapper.selectCount(new LambdaQueryWrapper<Redcard>()
                    .eq(Redcard::getBoard, entry.getKey())
                    .in(Redcard::getStatus, List.of("OPEN", "ASSIGNED", "ESCALATED")))));
            boards.add(b);
        }
        out.put("viewLevel", "company");
        out.put("boards", boards);
        out.put("meetingMode", meetingMode.get());
        return out;
    }

    private List<Map<String, Object>> metricsOfBoard(String board, Map<String, BigDecimal> metrics) {
        Map<String, String> boardMetrics = Map.of(
                "CONTRACT", "contract.pending_count,contract.effective_count",
                "DESIGN", "design.draft_count,design.released_count",
                "KITTING", "kitting.block_count,po.pending_count,po.overdue_count",
                "PRODUCE", "wo.started_count,disclosure.unsigned_count,qc.fail_count",
                "SHIP", "shipment.submitted_count",
                "ACCEPT", "acceptance.pending_sign,acceptance.pass_count",
                "TPLUS", "inventory.pending_push,inventory.push_failed,recon.diff_count",
                "RECTIFY", "rectification.open_count,rectification.overdue_count",
                "OVERVIEW", "project.total_count,redcard.open_count,redcard.escalated_count");
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : boardMetrics.getOrDefault(board, "").split(",")) {
            if (key.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("label", METRIC_LABELS.getOrDefault(key, key));
            row.put("value", metrics.getOrDefault(key, BigDecimal.ZERO));
            result.add(row);
        }
        return result;
    }

    /** 项目级视图核心指标 */
    private Map<String, Object> projectMetrics(Long projectId) {
        Map<String, Object> m = new LinkedHashMap<>();
        KittingCheck latestKit = kittingCheckMapper.selectOne(new LambdaQueryWrapper<KittingCheck>()
                .eq(KittingCheck::getProjectId, projectId)
                .orderByDesc(KittingCheck::getId).last("LIMIT 1"));
        m.put("kitRate", latestKit == null ? null : latestKit.getKitRate());
        m.put("kitResult", latestKit == null ? null : latestKit.getResult());
        m.put("overduePo", cnt(purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getProjectId, projectId)
                .eq(PurchaseOrder::getStatus, "SUBMITTED")
                .isNotNull(PurchaseOrder::getExpectedDate)
                .lt(PurchaseOrder::getExpectedDate, LocalDate.now()))));
        ConstructionLog latestLog = constructionLogMapper.selectOne(new LambdaQueryWrapper<ConstructionLog>()
                .eq(ConstructionLog::getProjectId, projectId)
                .orderByDesc(ConstructionLog::getProgressPct).last("LIMIT 1"));
        m.put("progress", latestLog == null ? BigDecimal.ZERO : latestLog.getProgressPct());
        m.put("acceptancePendingSign", cnt(acceptanceMapper.selectCount(new LambdaQueryWrapper<Acceptance>()
                .eq(Acceptance::getProjectId, projectId)
                .eq(Acceptance::getStatus, "PENDING_SIGN"))));
        m.put("shipments", cnt(shipmentMapper.selectCount(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getProjectId, projectId))));
        m.put("openRectifications", cnt(rectificationMapper.selectCount(new LambdaQueryWrapper<Rectification>()
                .eq(Rectification::getProjectId, projectId)
                .eq(Rectification::getStatus, "OPEN"))));
        return m;
    }

    // ===== 红灯引擎（规则配置化）=====

    /**
     * 红灯引擎评估：遍历启用规则 → 指标比较 → 触发/升级/自动消除
     * 升级：OPEN 超过规则 escalate_days 未处理，或 ASSIGNED 后 due_date 逾期 → ESCALATED + redcard.escalated 事件
     *
     * @return {created, escalated, resolved, checked}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> evaluateRedcards() {
        Map<String, BigDecimal> metrics = computeMetrics();
        List<RedcardRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<RedcardRule>()
                .eq(RedcardRule::getEnabled, 1));
        int created = 0, escalated = 0, resolved = 0;
        for (RedcardRule rule : rules) {
            BigDecimal value = metrics.getOrDefault(rule.getMetric(), BigDecimal.ZERO);
            boolean hit = compare(value, rule.getComparator(), rule.getThreshold());
            Redcard active = redcardMapper.selectOne(new LambdaQueryWrapper<Redcard>()
                    .eq(Redcard::getRuleCode, rule.getRuleCode())
                    .in(Redcard::getStatus, List.of("OPEN", "ASSIGNED", "ESCALATED"))
                    .orderByDesc(Redcard::getId).last("LIMIT 1"));
            if (hit) {
                if (active == null) {
                    Redcard card = new Redcard();
                    card.setRuleCode(rule.getRuleCode());
                    card.setRuleName(rule.getRuleName());
                    card.setBoard(rule.getBoard());
                    card.setMetricValue(value);
                    card.setLevel(rule.getLevel());
                    card.setStatus("OPEN");
                    redcardMapper.insert(card);
                    created++;
                    log.info("红灯触发: {} ({}) 值={} 阈值={}", rule.getRuleCode(), rule.getLevel(), value, rule.getThreshold());
                } else if (shouldEscalate(active, rule)) {
                    active.setStatus("ESCALATED");
                    active.setEscalatedAt(LocalDateTime.now());
                    active.setMetricValue(value);
                    redcardMapper.updateById(active);
                    escalated++;
                    // 红灯升级 → 跨域推送（通知域经事件处理器消费）
                    eventBusService.publish(DomainEventType.REDCARD_ESCALATED, Map.of(
                            "redcardId", active.getId(),
                            "ruleCode", rule.getRuleCode(),
                            "ruleName", rule.getRuleName(),
                            "level", rule.getLevel(),
                            "metricValue", String.valueOf(value)));
                    log.info("红灯升级: {} 已 {} 未处理", rule.getRuleCode(), rule.getEscalateDays() + " 天");
                } else {
                    active.setMetricValue(value);
                    redcardMapper.updateById(active);
                }
            } else if (active != null && !"ESCALATED".equals(active.getStatus())) {
                // 指标恢复 → 自动消除（ESCALATED 红灯保留人工闭环）
                active.setStatus("RESOLVED");
                active.setResolvedAt(LocalDateTime.now());
                active.setResolvedNote("指标恢复自动消除（当前值 " + value + "）");
                redcardMapper.updateById(active);
                resolved++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checked", rules.size());
        result.put("created", created);
        result.put("escalated", escalated);
        result.put("resolved", resolved);
        return result;
    }

    private boolean shouldEscalate(Redcard active, RedcardRule rule) {
        if ("OPEN".equals(active.getStatus())) {
            long days = ChronoUnit.DAYS.between(active.getCreateTime(), LocalDateTime.now());
            return days >= (rule.getEscalateDays() == null ? 3 : rule.getEscalateDays());
        }
        if ("ASSIGNED".equals(active.getStatus()) && active.getDueDate() != null) {
            return active.getDueDate().isBefore(LocalDate.now());
        }
        return false;
    }

    private boolean compare(BigDecimal value, String comparator, BigDecimal threshold) {
        int cmp = value.compareTo(threshold);
        return switch (comparator == null ? "GT" : comparator) {
            case "LT" -> cmp < 0;
            case "EQ" -> cmp == 0;
            default -> cmp > 0;
        };
    }

    // ===== 红灯过堂 / 消除 / 会议模式 =====

    /** 会议过堂：指定责任人与期限（OPEN/ESCALATED → ASSIGNED） */
    @Transactional(rollbackFor = Exception.class)
    public void attendRedcard(Long redcardId, String owner, LocalDate dueDate) {
        Redcard card = redcardMapper.selectById(redcardId);
        if (card == null) {
            throw new BusinessException(404, "红灯不存在: " + redcardId);
        }
        if ("RESOLVED".equals(card.getStatus())) {
            throw new BusinessException(400, "红灯已消除，不允许过堂");
        }
        if (owner == null || owner.isBlank()) {
            throw new BusinessException(400, "过堂必须指定责任人");
        }
        card.setOwner(owner);
        card.setDueDate(dueDate);
        card.setStatus("ASSIGNED");
        redcardMapper.updateById(card);
        log.info("红灯过堂: #{} {} → 责任人 {} 期限 {}", redcardId, card.getRuleCode(), owner, dueDate);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolveRedcard(Long redcardId, String note) {
        Redcard card = redcardMapper.selectById(redcardId);
        if (card == null) {
            throw new BusinessException(404, "红灯不存在: " + redcardId);
        }
        card.setStatus("RESOLVED");
        card.setResolvedAt(LocalDateTime.now());
        card.setResolvedNote(note);
        redcardMapper.updateById(card);
    }

    public List<Redcard> listRedcards(String status, String board) {
        LambdaQueryWrapper<Redcard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), Redcard::getStatus, status);
        wrapper.eq(board != null && !board.isBlank(), Redcard::getBoard, board);
        wrapper.orderByDesc(Redcard::getId);
        return redcardMapper.selectList(wrapper);
    }

    public List<RedcardRule> listRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<RedcardRule>().orderByAsc(RedcardRule::getId));
    }

    public boolean isMeetingMode() {
        return meetingMode.get();
    }

    public void setMeetingMode(boolean enabled) {
        meetingMode.set(enabled);
        log.info("会议模式: {}", enabled ? "开启（早会红灯过堂）" : "关闭");
    }
}
