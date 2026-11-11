package com.dept.mgmt.chnchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.chnchange.entity.EcnChange;
import com.dept.mgmt.chnchange.entity.EcnRecallTask;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.design.entity.DesignScheme;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.DesignSchemeMapper;
import com.dept.mgmt.mapper.EcnChangeMapper;
import com.dept.mgmt.mapper.EcnRecallTaskMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.workshop.service.WorkshopService;
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
 * W5 变更域服务（T-302）
 * BR-07 内嵌（合规底线）：技术变更必须走 ECN 流程（DRAFT→待审批→批准→实施）；旧版资料必须回收
 * ECN 实施（IMPLEMENTED）联动：
 * 1. 自动生成旧版资料回收任务（SCHEME/旧版方案置 ARCHIVED 受控回收）
 * 2. 发布 ecn.changed 事件（通知域推送批量准备提醒）
 * 3. 批量齐套复检（AGT-13 口径按新方案重算，checker=ECN）+ 缺料自动生成请购草稿（AGT-14 复用）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcnService {

    private static final List<String> CHANGE_TYPES = List.of("DESIGN", "PROCESS", "MATERIAL");
    private static final List<String> IMPACT_LEVELS = List.of("A", "B", "C");

    private final EcnChangeMapper ecnMapper;
    private final EcnRecallTaskMapper recallMapper;
    private final DesignSchemeMapper schemeMapper;
    private final CodeEngineService codeEngineService;
    private final EventBusService eventBusService;
    private final WorkshopService workshopService;

    // ===== ECN 生命周期 =====

    @Transactional(rollbackFor = Exception.class)
    public Long createEcn(Long projectId, String title, String reason, String changeType,
                          Long originSchemeId, String impactLevel) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "变更标题不能为空");
        }
        if (!CHANGE_TYPES.contains(changeType)) {
            throw new BusinessException(400, "变更类型仅允许 " + CHANGE_TYPES);
        }
        if (!IMPACT_LEVELS.contains(impactLevel)) {
            throw new BusinessException(400, "影响级别仅允许 A/B/C");
        }
        if (originSchemeId != null && schemeMapper.selectById(originSchemeId) == null) {
            throw new BusinessException(404, "原方案不存在: " + originSchemeId);
        }
        LoginUser user = LoginUser.current();
        EcnChange ecn = new EcnChange();
        ecn.setEcnNo(codeEngineService.nextCode("ECN", null));
        ecn.setProjectId(projectId);
        ecn.setTitle(title);
        ecn.setReason(reason);
        ecn.setChangeType(changeType);
        ecn.setOriginSchemeId(originSchemeId);
        ecn.setImpactLevel(impactLevel);
        ecn.setStatus("DRAFT");
        ecn.setApplicant(user == null ? "系统" : user.getUsername());
        ecnMapper.insert(ecn);
        log.info("ECN 创建: {} {} ({})", ecn.getEcnNo(), title, changeType);
        return ecn.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitEcn(Long ecnId) {
        EcnChange ecn = mustExistEcn(ecnId);
        if (!"DRAFT".equals(ecn.getStatus())) {
            throw new BusinessException(400, "仅草稿 ECN 可提交审批，当前: " + ecn.getStatus());
        }
        ecn.setStatus("PENDING_APPROVAL");
        ecnMapper.updateById(ecn);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveEcn(Long ecnId, String approver, boolean approved, String remark) {
        EcnChange ecn = mustExistEcn(ecnId);
        if (!"PENDING_APPROVAL".equals(ecn.getStatus())) {
            throw new BusinessException(400, "仅待审批 ECN 可审批，当前: " + ecn.getStatus());
        }
        ecn.setStatus(approved ? "APPROVED" : "REJECTED");
        ecn.setApprover(approver);
        ecn.setApprovedAt(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            ecn.setRemark(remark);
        }
        ecnMapper.updateById(ecn);
        log.info("ECN 审批: {} → {}", ecn.getEcnNo(), ecn.getStatus());
    }

    /**
     * ECN 实施（BR-07 核心动作）：绑定新方案 + 生成旧版回收任务 + 旧方案受控回收 +
     * 发布 ecn.changed 事件 + 批量齐套复检 + 缺料自动请购草稿
     *
     * @return {recallTasks, kitRate, shortageCount, draftPoCount}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> implementEcn(Long ecnId, Long newSchemeId, String recallOwner, Integer recallDueDays) {
        EcnChange ecn = mustExistEcn(ecnId);
        if (!"APPROVED".equals(ecn.getStatus())) {
            throw new BusinessException(400, "仅已批准 ECN 可实施（BR-07：变更必须走完审批），当前: " + ecn.getStatus());
        }
        if (newSchemeId == null || schemeMapper.selectById(newSchemeId) == null) {
            throw new BusinessException(404, "新方案不存在: " + newSchemeId);
        }
        // 1. 旧版资料回收任务（SCHEME 类型自动生成；B-07 旧版资料必须回收）
        List<EcnRecallTask> recallTasks = new ArrayList<>();
        if (ecn.getOriginSchemeId() != null) {
            DesignScheme origin = schemeMapper.selectById(ecn.getOriginSchemeId());
            if (origin != null) {
                EcnRecallTask task = new EcnRecallTask();
                task.setEcnId(ecn.getId());
                task.setProjectId(ecn.getProjectId());
                task.setItemType("SCHEME");
                task.setItemId(origin.getId());
                task.setItemDesc("原设计方案 v" + origin.getVersionNo() + "（" + origin.getTitle() + "）");
                task.setOwner(recallOwner);
                task.setStatus("PENDING");
                task.setDueDate(LocalDate.now().plusDays(recallDueDays == null || recallDueDays < 1 ? 3 : recallDueDays));
                recallMapper.insert(task);
                recallTasks.add(task);
                // 旧方案受控回收（ARCHIVED）
                origin.setStatus("ARCHIVED");
                schemeMapper.updateById(origin);
            }
        }
        // 2. 绑定新方案 + 状态推进
        ecn.setNewSchemeId(newSchemeId);
        ecn.setStatus("IMPLEMENTED");
        ecn.setImplementedAt(LocalDateTime.now());
        ecnMapper.updateById(ecn);
        // 3. 发布 ecn.changed 事件（通知域推送批量准备提醒）
        eventBusService.publish(DomainEventType.ECN_CHANGED, Map.of(
                "ecnId", ecn.getId(),
                "ecnNo", ecn.getEcnNo(),
                "projectId", ecn.getProjectId(),
                "newSchemeId", newSchemeId,
                "impactLevel", ecn.getImpactLevel()));
        // 4. 批量齐套复检（AGT-13 口径）+ 缺料自动请购草稿（AGT-14 复用）
        WorkshopService.KittingCalc calc = workshopService.calculateKitting(ecn.getProjectId());
        workshopService.runKitting(ecn.getProjectId(), calc.kitRate(), toJson(calc), "ECN");
        int draftPoCount = 0;
        for (WorkshopService.Shortage shortage : calc.shortages()) {
            if (shortage.materialCode() == null || shortage.materialCode().isBlank()) {
                continue;
            }
            workshopService.createDraftPo(ecn.getProjectId(), shortage.materialCode(),
                    shortage.materialName(), shortage.shortage());
            draftPoCount++;
        }
        log.info("ECN 实施: {} 新方案 {} 回收任务 {} 齐套 {}% 请购草稿 {}",
                ecn.getEcnNo(), newSchemeId, recallTasks.size(), calc.kitRate(), draftPoCount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ecnId", ecn.getId());
        out.put("ecnNo", ecn.getEcnNo());
        out.put("recallTasks", recallTasks);
        out.put("kitRate", calc.kitRate());
        out.put("shortageCount", calc.shortages().size());
        out.put("draftPoCount", draftPoCount);
        return out;
    }

    /** 回收任务确认完成（RECALLED） */
    @Transactional(rollbackFor = Exception.class)
    public void confirmRecall(Long taskId) {
        EcnRecallTask task = recallMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "回收任务不存在: " + taskId);
        }
        if ("CONFIRMED".equals(task.getStatus())) {
            throw new BusinessException(400, "回收任务已确认，不允许重复确认");
        }
        task.setStatus("CONFIRMED");
        task.setRecalledAt(LocalDateTime.now());
        recallMapper.updateById(task);
        log.info("旧版资料回收确认: #{} {}", taskId, task.getItemDesc());
    }

    public PageResult<EcnChange> listEcns(int pageNum, int pageSize, Long projectId, String status) {
        LambdaQueryWrapper<EcnChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, EcnChange::getProjectId, projectId);
        wrapper.eq(status != null && !status.isBlank(), EcnChange::getStatus, status);
        wrapper.orderByDesc(EcnChange::getId);
        return PageResult.of(ecnMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public List<EcnRecallTask> listRecallTasks(Long ecnId, String status) {
        LambdaQueryWrapper<EcnRecallTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ecnId != null, EcnRecallTask::getEcnId, ecnId);
        wrapper.eq(status != null && !status.isBlank(), EcnRecallTask::getStatus, status);
        wrapper.orderByDesc(EcnRecallTask::getId);
        return recallMapper.selectList(wrapper);
    }

    private String toJson(WorkshopService.KittingCalc calc) {
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.writeValueAsString(Map.of(
                    "totalItems", calc.totalItems(),
                    "shortages", calc.shortages()));
        } catch (Exception e) {
            return "[]";
        }
    }

    private EcnChange mustExistEcn(Long id) {
        EcnChange ecn = ecnMapper.selectById(id);
        if (ecn == null) {
            throw new BusinessException(404, "ECN 不存在: " + id);
        }
        return ecn;
    }
}
