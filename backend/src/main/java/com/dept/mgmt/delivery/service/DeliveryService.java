package com.dept.mgmt.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.delivery.entity.Acceptance;
import com.dept.mgmt.delivery.entity.ConstructionLog;
import com.dept.mgmt.delivery.entity.LogisticsRecord;
import com.dept.mgmt.delivery.entity.Shipment;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.AcceptanceMapper;
import com.dept.mgmt.mapper.ConstructionLogMapper;
import com.dept.mgmt.mapper.FaiReportMapper;
import com.dept.mgmt.mapper.LogisticsRecordMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.ShipmentMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.workshop.entity.FaiReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W6 交付域服务（T-206）
 * 硬卡点内嵌（合规底线：卡点前置在动作入口）：
 * BR-02 整机厂项目首次发货必须持有 PASS 的 FAI 报告，否则发货单禁止提交
 * BR-03 工程量达 30% 自动触发中期验收（triggerAcceptanceChecks，AGT-19 数据源）
 * BR-04 工程量达 85% 自动触发后期验收
 * BR-05 验收单必须三方签字（业主+监理+我方）齐备才生效（PASS）
 * 终验三方签字生效时发布 delivery.accepted 事件（联动收款提醒/档案归集）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    /** BR-03 中期验收触发阈值（工程量 30%） */
    private static final BigDecimal MID_THRESHOLD = new BigDecimal("30.00");

    /** BR-04 后期验收触发阈值（工程量 85%） */
    private static final BigDecimal LATE_THRESHOLD = new BigDecimal("85.00");

    private static final List<String> LOGISTICS_EVENTS = List.of("DISPATCH", "TRANSIT", "ARRIVED", "INSTALL");
    private static final List<String> ACCEPTANCE_STAGES = List.of("FIRST", "MID", "LATE", "FINAL");

    private final ShipmentMapper shipmentMapper;
    private final LogisticsRecordMapper logisticsMapper;
    private final ConstructionLogMapper constructionLogMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final ProjectMapper projectMapper;
    private final FaiReportMapper faiReportMapper;
    private final CodeEngineService codeEngineService;
    private final EventBusService eventBusService;

    // ===== 发货（BR-02 载体）=====

    @Transactional(rollbackFor = Exception.class)
    public Long createShipment(Long projectId, Long workorderId, String batchNo, Integer qty,
                               String carrier, String remark) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        Shipment shipment = new Shipment();
        shipment.setShipmentNo(codeEngineService.nextCode("SP", null));
        shipment.setProjectId(projectId);
        shipment.setWorkorderId(workorderId);
        shipment.setBatchNo(batchNo);
        shipment.setQty(qty == null || qty < 1 ? 1 : qty);
        shipment.setCarrier(carrier);
        shipment.setStatus("DRAFT");
        shipment.setRemark(remark);
        shipmentMapper.insert(shipment);
        log.info("发货单创建: {} projectId={} {}台", shipment.getShipmentNo(), projectId, shipment.getQty());
        return shipment.getId();
    }

    /**
     * 发货单提交（发运）。BR-02：整机厂项目（projectType=1）首次发货必须持有 PASS 的 FAI 报告
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitShipment(Long shipmentId) {
        Shipment shipment = mustExistShipment(shipmentId);
        if (!"DRAFT".equals(shipment.getStatus())) {
            throw new BusinessException(400, "仅草稿发货单可提交，当前: " + shipment.getStatus());
        }
        Project project = projectMapper.selectById(shipment.getProjectId());
        boolean firstShipment = shipmentMapper.selectCount(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getProjectId, shipment.getProjectId())
                .eq(Shipment::getStatus, "SUBMITTED")) == 0;
        if (firstShipment && project != null && project.getProjectType() != null && project.getProjectType() == 1) {
            Long faiPass = faiReportMapper.selectCount(new LambdaQueryWrapper<FaiReport>()
                    .eq(FaiReport::getProjectId, shipment.getProjectId())
                    .eq(FaiReport::getResult, "PASS"));
            if (faiPass == null || faiPass == 0) {
                throw new BusinessException(400, "硬卡点 BR-02 拦截：整机厂项目首次发货必须持有 PASS 的 FAI 首件检验报告，发货单不允许提交");
            }
        }
        shipment.setStatus("SUBMITTED");
        shipment.setShippedAt(LocalDateTime.now());
        shipmentMapper.updateById(shipment);
        log.info("发货单发运: {}（BR-02 校验通过，首次发货={}）", shipment.getShipmentNo(), firstShipment);
    }

    public PageResult<Shipment> listShipments(int pageNum, int pageSize, Long projectId, String status) {
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, Shipment::getProjectId, projectId);
        wrapper.eq(status != null && !status.isBlank(), Shipment::getStatus, status);
        wrapper.orderByDesc(Shipment::getId);
        return PageResult.of(shipmentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    // ===== 物流跟踪 =====

    @Transactional(rollbackFor = Exception.class)
    public Long addLogistics(Long shipmentId, String eventType, String location, String description) {
        mustExistShipment(shipmentId);
        if (!LOGISTICS_EVENTS.contains(eventType)) {
            throw new BusinessException(400, "物流事件仅允许 " + LOGISTICS_EVENTS);
        }
        LoginUser user = LoginUser.current();
        LogisticsRecord record = new LogisticsRecord();
        record.setShipmentId(shipmentId);
        record.setEventType(eventType);
        record.setEventTime(LocalDateTime.now());
        record.setLocation(location);
        record.setDescription(description);
        record.setOperator(user == null ? "系统" : user.getUsername());
        logisticsMapper.insert(record);
        return record.getId();
    }

    public List<LogisticsRecord> listLogistics(Long shipmentId) {
        return logisticsMapper.selectList(new LambdaQueryWrapper<LogisticsRecord>()
                .eq(shipmentId != null, LogisticsRecord::getShipmentId, shipmentId)
                .orderByDesc(LogisticsRecord::getId));
    }

    // ===== 施工日志（BR-03/04 数据源）=====

    /**
     * 录入施工日志。progress_pct 为当日累计完成工程量快照（0~100），
     * 项目当前工程量 = 历史日志最大值
     *
     * @return 当前项目累计工程量
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal addConstructionLog(Long projectId, LocalDate logDate, String weather, Integer personnelCount,
                                         String workContent, BigDecimal progressPct, String issues, String recorder) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        if (progressPct == null || progressPct.signum() < 0 || progressPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(400, "累计工程量必须在 0~100 之间");
        }
        ConstructionLog logRow = new ConstructionLog();
        logRow.setProjectId(projectId);
        logRow.setLogDate(logDate == null ? LocalDate.now() : logDate);
        logRow.setWeather(weather);
        logRow.setPersonnelCount(personnelCount);
        logRow.setWorkContent(workContent);
        logRow.setProgressPct(progressPct);
        logRow.setIssues(issues);
        logRow.setRecorder(recorder);
        constructionLogMapper.insert(logRow);
        log.info("施工日志录入: projectId={} date={} 累计工程量 {}%", projectId, logRow.getLogDate(), progressPct);
        return currentProgress(projectId);
    }

    /** 项目当前累计工程量 = 施工日志 progress_pct 最大值 */
    public BigDecimal currentProgress(Long projectId) {
        ConstructionLog latest = constructionLogMapper.selectOne(new LambdaQueryWrapper<ConstructionLog>()
                .eq(ConstructionLog::getProjectId, projectId)
                .orderByDesc(ConstructionLog::getProgressPct).last("LIMIT 1"));
        return latest == null || latest.getProgressPct() == null ? BigDecimal.ZERO : latest.getProgressPct();
    }

    public List<ConstructionLog> listConstructionLogs(Long projectId) {
        return constructionLogMapper.selectList(new LambdaQueryWrapper<ConstructionLog>()
                .eq(projectId != null, ConstructionLog::getProjectId, projectId)
                .orderByDesc(ConstructionLog::getLogDate));
    }

    // ===== 分级验收（BR-03/04/05 载体）=====

    /**
     * 工程量验收触发检查（BR-03/04，确定性规则，AGT-19 数据源）：
     * 工程量 ≥30% 且无中期验收单 → 自动触发中期验收；
     * 工程量 ≥85% 且无后期验收单 → 自动触发后期验收
     *
     * @return 触发结果（当前工程量 + 本次新触发列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> triggerAcceptanceChecks(Long projectId, String triggeredBy) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        BigDecimal progress = currentProgress(projectId);
        List<Map<String, Object>> created = new ArrayList<>();
        if (progress.compareTo(MID_THRESHOLD) >= 0 && !acceptanceExists(projectId, "MID")) {
            Long id = createAcceptance(projectId, "MID", progress, triggeredBy, null);
            created.add(Map.of("stage", "MID", "acceptanceId", id, "rule", "BR-03 工程量达 30% 自动触发中期验收"));
        }
        if (progress.compareTo(LATE_THRESHOLD) >= 0 && !acceptanceExists(projectId, "LATE")) {
            Long id = createAcceptance(projectId, "LATE", progress, triggeredBy, null);
            created.add(Map.of("stage", "LATE", "acceptanceId", id, "rule", "BR-04 工程量达 85% 自动触发后期验收"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("progress", progress);
        result.put("triggered", created);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createAcceptance(Long projectId, String stage, BigDecimal progressPct,
                                 String triggeredBy, String comment) {
        if (!ACCEPTANCE_STAGES.contains(stage)) {
            throw new BusinessException(400, "验收阶段仅允许 " + ACCEPTANCE_STAGES);
        }
        if (acceptanceExists(projectId, stage)) {
            throw new BusinessException(400, "该项目已存在 " + stage + " 验收单，不允许重复创建");
        }
        Acceptance acceptance = new Acceptance();
        acceptance.setProjectId(projectId);
        acceptance.setStage(stage);
        acceptance.setStatus("TRIGGERED");
        acceptance.setProgressPct(progressPct);
        acceptance.setTriggeredBy(triggeredBy == null ? "MANUAL" : triggeredBy);
        acceptance.setComment(comment);
        acceptance.setSignedOwner(0);
        acceptance.setSignedSupervisor(0);
        acceptance.setSignedClient(0);
        acceptanceMapper.insert(acceptance);
        log.info("验收单触发: projectId={} stage={} 工程量 {}% 方式 {}", projectId, stage, progressPct, triggeredBy);
        return acceptance.getId();
    }

    /**
     * 验收单签字（BR-05）：party ∈ OWNER(我方)/SUPERVISOR(监理)/CLIENT(业主)；
     * 三方签字齐备 → 验收单生效（PASS）；终验生效时发布 delivery.accepted 事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void signAcceptance(Long acceptanceId, String party, String acceptor, String comment) {
        Acceptance acceptance = acceptanceMapper.selectById(acceptanceId);
        if (acceptance == null) {
            throw new BusinessException(404, "验收单不存在: " + acceptanceId);
        }
        if ("PASS".equals(acceptance.getStatus())) {
            throw new BusinessException(400, "验收单已生效，不允许重复签字");
        }
        switch (party) {
            case "OWNER" -> {
                acceptance.setSignedOwner(1);
                acceptance.setAcceptorOwner(acceptor);
            }
            case "SUPERVISOR" -> {
                acceptance.setSignedSupervisor(1);
                acceptance.setAcceptorSupervisor(acceptor);
            }
            case "CLIENT" -> {
                acceptance.setSignedClient(1);
                acceptance.setAcceptorClient(acceptor);
            }
            default -> throw new BusinessException(400, "签字方仅允许 OWNER/SUPERVISOR/CLIENT");
        }
        if (comment != null && !comment.isBlank()) {
            acceptance.setComment(comment);
        }
        boolean allSigned = acceptance.getSignedOwner() == 1
                && acceptance.getSignedSupervisor() == 1
                && acceptance.getSignedClient() == 1;
        if (allSigned) {
            // BR-05：三方签字齐备，验收单生效
            acceptance.setStatus("PASS");
            acceptance.setAcceptedAt(LocalDateTime.now());
        } else {
            acceptance.setStatus("PENDING_SIGN");
        }
        acceptanceMapper.updateById(acceptance);
        if (allSigned) {
            log.info("验收单生效（BR-05 三方签字齐备）: id={} stage={}", acceptanceId, acceptance.getStage());
            // 终验通过 → delivery.accepted 事件（联动验收款提醒/档案归集，T-207 消费）
            eventBusService.publish(DomainEventType.DELIVERY_ACCEPTED, Map.of(
                    "projectId", acceptance.getProjectId(),
                    "acceptanceId", acceptance.getId(),
                    "stage", acceptance.getStage(),
                    "final", "FINAL".equals(acceptance.getStage())));
        } else {
            log.info("验收单签字: id={} party={}（BR-05 待三方齐备）", acceptanceId, party);
        }
    }

    public List<Acceptance> listAcceptances(Long projectId, String stage, String status) {
        LambdaQueryWrapper<Acceptance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, Acceptance::getProjectId, projectId);
        wrapper.eq(stage != null && !stage.isBlank(), Acceptance::getStage, stage);
        wrapper.eq(status != null && !status.isBlank(), Acceptance::getStatus, status);
        wrapper.orderByDesc(Acceptance::getId);
        return acceptanceMapper.selectList(wrapper);
    }

    private boolean acceptanceExists(Long projectId, String stage) {
        return acceptanceMapper.selectCount(new LambdaQueryWrapper<Acceptance>()
                .eq(Acceptance::getProjectId, projectId)
                .eq(Acceptance::getStage, stage)) > 0;
    }

    private Shipment mustExistShipment(Long id) {
        Shipment shipment = shipmentMapper.selectById(id);
        if (shipment == null) {
            throw new BusinessException(404, "发货单不存在: " + id);
        }
        return shipment;
    }
}
