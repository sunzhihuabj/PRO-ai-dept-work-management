package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice;
import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.mapper.AgentDecisionAdviceMapper;
import com.dept.mgmt.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程管控治理服务（V2.0 M4 催办/监管/风险提示）。
 *
 * <p>三项治理职责：
 * <ul>
 *   <li>超期任务催办（每 5 分钟扫描）：deadline < 今天 且状态未完成的任务 →
 *       TASK_OVERDUE_REMIND 建议（站内信通知负责人，同任务同日幂等）；</li>
 *   <li>流程健康度巡检（工作日 09:00）：汇总超期任务/在途审批停留超 SLA（72h）情况 →
 *       PROCESS_SLA_WARNING 建议（同日幂等）；</li>
 *   <li>SLA 报告查询（MCP process.sla.report 只读暴露）。</li>
 * </ul>
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessGovernanceService {

    /** 任务未完成状态（0 待办/1 进行中/2 待审核） */
    private static final List<Integer> ACTIVE_STATUS = List.of(0, 1, 2);
    /** 在途审批 SLA（小时） */
    private static final int APPROVAL_SLA_HOURS = 72;

    private final TaskMapper taskMapper;
    private final DecisionAdviceService decisionAdviceService;
    private final AgentDecisionAdviceMapper adviceMapper;

    // ==================== 超期催办 ====================

    /**
     * 超期任务催办扫描（每 5 分钟；同任务同日只提醒一次）。
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void checkOverdueTasks() {
        try {
            List<Task> overdue = overdueTasks();
            int fired = 0;
            for (Task t : overdue) {
                if (remindedToday(DecisionAdviceService.SCENE_TASK_OVERDUE_REMIND, t.getId())) {
                    continue;
                }
                long days = LocalDate.now().toEpochDay() - t.getDeadline().toEpochDay();
                decisionAdviceService.createGovernanceAdvice(
                        DecisionAdviceService.SCENE_TASK_OVERDUE_REMIND, "TASK", t.getId(),
                        "任务超期提醒：「" + t.getTaskName() + "」已超期 " + days + " 天",
                        "任务「" + t.getTaskName() + "」（ID=" + t.getId() + "）原定截止 " + t.getDeadline()
                                + "，已超期 " + days + " 天仍未完成。请尽快处理或调整计划，"
                                + "也可委托 AI Agent 员工跟进。",
                        t.getAssigneeId(),
                        Map.of("taskId", t.getId(), "taskName", t.getTaskName(),
                                "deadline", String.valueOf(t.getDeadline()), "overdueDays", days));
                fired++;
            }
            if (fired > 0) {
                log.info("超期任务催办完成: 扫描 {} 例, 生成提醒 {} 条", overdue.size(), fired);
            }
        } catch (Exception e) {
            log.warn("超期任务催办扫描失败（下轮重试）: {}", e.getMessage());
        }
    }

    // ==================== 流程健康度巡检 ====================

    /**
     * 流程健康度巡检（工作日 09:00；同日幂等）：
     * 超期任务数 + 在途审批停留超 SLA 数 → 一条汇总 WARNING 建议（targetUserId=null 广播管理员）。
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void slaPatrol() {
        try {
            if (remindedToday(DecisionAdviceService.SCENE_PROCESS_SLA_WARNING, 0L)) {
                return;
            }
            Map<String, Object> report = slaReport();
            int overdueCount = ((Number) report.get("overdueCount")).intValue();
            int approvalSlaBreach = ((Number) report.get("approvalSlaBreachCount")).intValue();
            if (overdueCount == 0 && approvalSlaBreach == 0) {
                log.info("流程健康度巡检: 无超 SLA 项，跳过建议");
                return;
            }
            decisionAdviceService.createGovernanceAdvice(
                    DecisionAdviceService.SCENE_PROCESS_SLA_WARNING, "GOVERNANCE", 0L,
                    "流程健康度预警：超期任务 " + overdueCount + " 例，审批超时 " + approvalSlaBreach + " 例",
                    "工作日巡检（" + LocalDate.now() + "）：当前超期任务 " + overdueCount
                            + " 例；在途审批停留超过 " + APPROVAL_SLA_HOURS + " 小时 " + approvalSlaBreach
                            + " 例。详情见 process.sla.report 或「AI Agent 员工」面板风险横幅。",
                    null, report);
            log.info("流程健康度巡检完成: overdue={}, approvalBreach={}", overdueCount, approvalSlaBreach);
        } catch (Exception e) {
            log.warn("流程健康度巡检失败（次日重试）: {}", e.getMessage());
        }
    }

    /**
     * SLA 报告（MCP process.sla.report / 前端健康度卡片）。
     */
    public Map<String, Object> slaReport() {
        List<Task> overdue = overdueTasks();
        List<Map<String, Object>> overdueItems = new ArrayList<>();
        for (Task t : overdue) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", t.getId());
            item.put("taskName", t.getTaskName());
            item.put("assigneeId", t.getAssigneeId());
            item.put("deadline", String.valueOf(t.getDeadline()));
            item.put("overdueDays", LocalDate.now().toEpochDay() - t.getDeadline().toEpochDay());
            overdueItems.add(item);
        }
        long approvalSlaBreach = inFlightApprovalBreachCount();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", LocalDate.now().toString());
        out.put("overdueCount", overdueItems.size());
        out.put("overdueItems", overdueItems.size() > 20 ? overdueItems.subList(0, 20) : overdueItems);
        out.put("approvalSlaBreachCount", approvalSlaBreach);
        out.put("approvalSlaHours", APPROVAL_SLA_HOURS);
        return out;
    }

    /** 在途审批（PENDING 建议中 TOOL_APPROVAL/HERMES_APPROVAL 场景）停留超 SLA 数 */
    private long inFlightApprovalBreachCount() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(APPROVAL_SLA_HOURS);
        return adviceMapper.selectCount(new LambdaQueryWrapper<AgentDecisionAdvice>()
                .eq(AgentDecisionAdvice::getStatus, "PENDING")
                .in(AgentDecisionAdvice::getScene,
                        List.of(DecisionAdviceService.SCENE_TOOL_APPROVAL, DecisionAdviceService.SCENE_HERMES_APPROVAL))
                .lt(AgentDecisionAdvice::getCreateTime, threshold));
    }

    // ==================== 内部 ====================

    private List<Task> overdueTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus, ACTIVE_STATUS)
                .isNotNull(Task::getDeadline)
                .lt(Task::getDeadline, LocalDate.now())
                .orderByAsc(Task::getDeadline)
                .last("LIMIT 100"));
    }

    /** 幂等：同 scene+sourceId 当日已有建议则跳过 */
    private boolean remindedToday(String scene, Long sourceId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        return adviceMapper.selectCount(new LambdaQueryWrapper<AgentDecisionAdvice>()
                .eq(AgentDecisionAdvice::getScene, scene)
                .eq(AgentDecisionAdvice::getSourceId, sourceId)
                .ge(AgentDecisionAdvice::getCreateTime, dayStart)) > 0;
    }
}
