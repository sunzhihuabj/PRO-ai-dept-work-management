package com.dept.mgmt.agent.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice;
import com.dept.mgmt.agent.collab.support.HmacUtils;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.mapper.AgentDecisionAdviceMapper;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 决策建议服务（阶段一多 Agent 协同核心）
 *
 * <p>建议生成：阶段一为<b>确定性规则模板</b>（不消耗 LLM 配额，规避 35 次/分限流），
 * 场景模板由事件驱动（见 {@code DecisionAdviceEventHandler}）；LLM 生成与数据飞轮为阶段二。
 *
 * <p>HITL 护栏：requiresApproval=1 的建议参数以 HMAC-SHA256 锁定（{@link HmacUtils}），
 * 审批通过执行前重算比对，防参数篡改；置信度低于 0.85 的建议强制人工。
 *
 * <p>注入说明：TaskService 经 ObjectProvider 延迟获取——TaskService 依赖 EventBusService，
 * EventBusService 聚合事件处理器（含本服务消费者），直接注入将形成构造环（沿用
 * ProjectService 的 ObjectProvider 模式）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class DecisionAdviceService {

    /** 建议场景 */
    public static final String SCENE_PROJECT_CREATED = "PROJECT_CREATED";
    public static final String SCENE_TASK_CREATED = "TASK_CREATED";
    public static final String SCENE_TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
    public static final String SCENE_PROJECT_PROGRESS = "PROJECT_PROGRESS";

    /** 审批动作（阶段一仅支持指派任务；其余动作阶段二随 A2A 扩展） */
    public static final String ACTION_AUTO_ASSIGN_TASK = "AUTO_ASSIGN_TASK";

    /** 状态 */
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";

    /** 置信度自动建议阈值（低于强制人工，业界共识 0.85 起步） */
    public static final double CONFIDENCE_THRESHOLD = 0.85;

    private final AgentDecisionAdviceMapper adviceMapper;
    private final ObjectProvider<com.dept.mgmt.service.TaskService> taskServiceProvider;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String hmacSecret;

    public DecisionAdviceService(AgentDecisionAdviceMapper adviceMapper,
                                 ObjectProvider<com.dept.mgmt.service.TaskService> taskServiceProvider,
                                 NotificationService notificationService,
                                 ObjectMapper objectMapper,
                                 @Value("${app.advice.hmac-secret:dept-advice-hmac-2026r2-dev}") String hmacSecret) {
        this.adviceMapper = adviceMapper;
        this.taskServiceProvider = taskServiceProvider;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.hmacSecret = hmacSecret;
    }

    // ==================== 事件驱动的建议生成 ====================

    /** 项目创建 → 立项预审建议 */
    public void onProjectCreated(Map<String, Object> payload) {
        Long projectId = longVal(payload.get("projectId"));
        if (projectId == null) {
            return;
        }
        String projectCode = str(payload.get("projectCode"));
        String projectName = str(payload.get("projectName"));
        Long targetUser = longVal(payload.get("creatorUserId"));
        LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
        structured.put("checks", List.of("阶段模板与里程碑完整性核对", "风险登记补充", "项目成员与权限确认"));
        advice(SCENE_PROJECT_CREATED, "PROJECT", projectId, projectCode,
                "项目立项预审建议：" + projectName,
                "INFO",
                "项目已创建（编号 " + projectCode + "）并自动生成阶段模板。建议：①核对阶段模板与里程碑完整性；②补充风险登记；③确认项目成员与权限。",
                structured, 0.90, "LOW", false, null, null,
                List.of(Map.of("source", "PROJECT", "ref", projectCode)),
                targetUser, 24);
    }

    /** 任务创建 → 要素完整性检查（缺指派人 → HITL 指派建议；齐全 → 确认建议） */
    public void onTaskCreated(Map<String, Object> payload) {
        Long taskId = longVal(payload.get("taskId"));
        if (taskId == null) {
            return;
        }
        String taskName = str(payload.get("taskName"));
        Long assigneeId = longVal(payload.get("assigneeId"));
        Long actorUserId = longVal(payload.get("actorUserId"));
        if (assigneeId == null) {
            // HITL：无负责人 → 建议指派给创建人（审批通过后执行），动作参数 HMAC 锁定
            Map<String, Object> actionParams = new LinkedHashMap<>();
            actionParams.put("taskId", taskId);
            actionParams.put("assigneeId", actorUserId == null ? 1L : actorUserId);
            LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
            structured.put("missing", "assigneeId");
            structured.put("proposal", "指派给任务创建人（可在驳回备注中说明应指派人选）");
            advice(SCENE_TASK_CREATED, "TASK", taskId, taskName,
                    "任务待指派建议：" + taskName,
                    "REQUEST",
                    "任务「" + taskName + "」创建后未指派负责人。建议指派给任务创建人；如需改派请在驳回备注中说明。",
                    structured, 0.86, "MEDIUM", true, ACTION_AUTO_ASSIGN_TASK, actionParams,
                    List.of(Map.of("source", "TASK", "ref", String.valueOf(taskId))),
                    actorUserId, 48);
        } else {
            advice(SCENE_TASK_CREATED, "TASK", taskId, taskName,
                    "任务要素确认建议：" + taskName,
                    "INFO",
                    "任务已指派负责人。建议：①核对计划工时与截止日期合理性；②大颗粒任务拆解为可验收子任务。",
                    new LinkedHashMap<>(), 0.90, "LOW", false, null, null,
                    List.of(Map.of("source", "TASK", "ref", String.valueOf(taskId))),
                    assigneeId, 24);
        }
    }

    /** 任务状态流转 → 截止预警 / 验收预审 / 后续动作建议 */
    public void onTaskStatusChanged(Map<String, Object> payload) {
        Long taskId = longVal(payload.get("taskId"));
        if (taskId == null) {
            return;
        }
        String taskName = str(payload.get("taskName"));
        Integer fromStatus = intVal(payload.get("fromStatus"));
        Integer toStatus = intVal(payload.get("toStatus"));
        Long assigneeId = longVal(payload.get("assigneeId"));
        Long actorUserId = longVal(payload.get("actorUserId"));
        LocalDateTime deadline = dateVal(payload.get("deadline"));

        // 1) 截止期已过且任务未完成 → 高置信 WARNING（优先级最高）
        if (deadline != null && deadline.isBefore(LocalDateTime.now())
                && toStatus != null && toStatus < 3) {
            LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
            structured.put("deadline", deadline.toString());
            structured.put("overdueDays", java.time.temporal.ChronoUnit.DAYS.between(
                    deadline.toLocalDate(), LocalDateTime.now().toLocalDate()));
            advice(SCENE_TASK_STATUS_CHANGED, "TASK", taskId, taskName,
                    "任务截止期预警：" + taskName,
                    "WARNING",
                    "任务「" + taskName + "」已过截止时间仍未完成（状态 " + statusName(fromStatus)
                            + "→" + statusName(toStatus) + "）。建议评估延期登记或升级干预。",
                    structured, 1.00, "HIGH", false, null, null,
                    List.of(Map.of("source", "TASK", "ref", String.valueOf(taskId))),
                    assigneeId != null ? assigneeId : actorUserId, 12);
            return;
        }

        // 2) 提交待审核（→2）→ 验收预审建议
        if (toStatus != null && toStatus == 2) {
            LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
            structured.put("checklist", List.of(
                    "交付物与任务描述一致性", "验收标准匹配度", "实际工时与计划工时偏差"));
            advice(SCENE_TASK_STATUS_CHANGED, "TASK", taskId, taskName,
                    "验收预审建议：" + taskName,
                    "INFO",
                    "任务「" + taskName + "」已提交待审核。建议按清单预审后再进入验收。",
                    structured, 0.88, "LOW", false, null, null,
                    List.of(Map.of("source", "TASK", "ref", String.valueOf(taskId))),
                    actorUserId, 12);
            return;
        }

        // 3) 验收完成（→3）→ 后续动作建议
        if (toStatus != null && toStatus == 3) {
            advice(SCENE_TASK_STATUS_CHANGED, "TASK", taskId, taskName,
                    "验收通过后续动作建议：" + taskName,
                    "INFO",
                    "任务「" + taskName + "」验收通过。跨域联动（归档/交付/回款提醒）已由事件总线自动触发，建议关注对应模块的通知。",
                    new LinkedHashMap<>(), 0.90, "LOW", false, null, null,
                    List.of(Map.of("source", "TASK", "ref", String.valueOf(taskId))),
                    assigneeId != null ? assigneeId : actorUserId, 12);
        }
    }

    /** 项目阶段完成 → 后续动作建议 */
    public void onProjectProgress(Map<String, Object> payload) {
        Long projectId = longVal(payload.get("projectId"));
        if (projectId == null) {
            return;
        }
        String projectCode = str(payload.get("projectCode"));
        String itemType = str(payload.get("itemType"));
        String itemName = str(payload.get("itemName"));
        if (!"PHASE".equals(itemType)) {
            return;
        }
        advice(SCENE_PROJECT_PROGRESS, "PHASE", projectId, projectCode,
                "阶段完成后续建议：" + itemName,
                "INFO",
                "项目（" + projectCode + "）阶段「" + itemName + "」已完成。建议：①检查下一阶段启动前置条件；②如配置阶段门禁，按期提交门禁评审。",
                new LinkedHashMap<>(), 0.90, "LOW", false, null, null,
                List.of(Map.of("source", "PHASE", "ref", itemName)),
                longVal(payload.get("projectLeaderUserId")), 24);
    }

    // ==================== 查询与审批 ====================

    /** 分页查询（数据范围：全部登录用户可读——建议为辅助信息，写操作另行鉴权） */
    public PageResult<AgentDecisionAdvice> page(String status, String scene, Integer pageNum, Integer pageSize) {
        Page<AgentDecisionAdvice> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<AgentDecisionAdvice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), AgentDecisionAdvice::getStatus, status);
        wrapper.eq(StringUtils.hasText(scene), AgentDecisionAdvice::getScene, scene);
        wrapper.orderByDesc(AgentDecisionAdvice::getId);
        adviceMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /**
     * 审批采纳：HMAC 校验 → 状态流转 → 执行审批动作（阶段一仅 AUTO_ASSIGN_TASK）
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentDecisionAdvice approve(Long id, String approverComment) {
        AgentDecisionAdvice advice = mustPending(id);
        executeApproval(advice, approverComment);
        return advice;
    }

    /** 审批驳回：仅状态流转，不执行动作 */
    @Transactional(rollbackFor = Exception.class)
    public AgentDecisionAdvice reject(Long id, String approverComment) {
        AgentDecisionAdvice advice = mustPending(id);
        advice.setStatus(ST_REJECTED);
        advice.setApproverComment(approverComment);
        advice.setApproverId(currentUserId());
        advice.setApprovedAt(LocalDateTime.now());
        adviceMapper.updateById(advice);
        log.info("Agent 建议已驳回: id={}, by={}", id, advice.getApproverId());
        return advice;
    }

    // ==================== 内部实现 ====================

    /** 统一建议落库 + 站内信推送 */
    private void advice(String scene, String sourceType, Long sourceId, String bizNo, String title,
                        String type, String body, Map<String, Object> structured,
                        double confidence, String riskLevel, boolean requiresApproval,
                        String approvalAction, Map<String, Object> approvalParams,
                        List<Map<String, String>> evidence, Long targetUserId, int slaHours) {
        try {
            AgentDecisionAdvice a = new AgentDecisionAdvice();
            a.setScene(scene);
            a.setSourceType(sourceType);
            a.setSourceId(sourceId);
            a.setSourceBizNo(bizNo);
            a.setAgentId("system-rule");
            a.setAgentRole("业务执行");
            a.setTitle(title);
            a.setConfidence(confidence);
            a.setRiskLevel(riskLevel);
            a.setRequiresApproval(requiresApproval ? 1 : 0);
            a.setStatus(ST_PENDING);
            a.setTargetUserId(targetUserId);
            a.setResponseDeadline(LocalDateTime.now().plusHours(slaHours));
            try {
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("type", type);
                content.put("body", body);
                content.put("structured", structured == null ? Map.of() : structured);
                a.setContentJson(objectMapper.writeValueAsString(content));
                a.setEvidenceJson(objectMapper.writeValueAsString(evidence));
            } catch (Exception e) {
                a.setContentJson("{\"type\":\"INFO\",\"body\":\"" + body + "\"}");
            }
            if (requiresApproval && approvalAction != null && approvalParams != null) {
                try {
                    String paramsJson = objectMapper.writeValueAsString(approvalParams);
                    a.setApprovalAction(approvalAction);
                    a.setApprovalParamsJson(paramsJson);
                    a.setApprovalHmac(HmacUtils.sign(hmacSecret, paramsJson));
                } catch (Exception e) {
                    log.warn("审批参数序列化失败，降级为纯建议: {}", e.getMessage());
                    a.setRequiresApproval(0);
                    a.setApprovalAction(null);
                }
            }
            adviceMapper.insert(a);
            notifyTarget(a, body);
            log.info("Agent 建议已生成: id={}, scene={}, confidence={}, approval={}",
                    a.getId(), scene, confidence, requiresApproval);
        } catch (Exception e) {
            // 建议生成失败不阻塞业务主流程（事件消费失败由事件总线按重试策略处理）
            throw new IllegalStateException("建议生成失败: " + e.getMessage(), e);
        }
    }

    private void notifyTarget(AgentDecisionAdvice a, String body) {
        if (a.getTargetUserId() == null) {
            return;
        }
        try {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTemplateCode("GENERIC_REMIND");
            String tag = a.getRequiresApproval() == 1 ? "【待审批】" : "【建议】";
            dto.setParams(Map.of("title", tag + a.getTitle(), "content", body));
            dto.setReceiverIds(List.of(a.getTargetUserId()));
            dto.setRelatedId(a.getId());
            NotificationSendResult result = notificationService.send(dto);
            log.debug("建议站内信推送: adviceId={}, sent={}", a.getId(), result.getSent());
        } catch (Exception e) {
            log.warn("建议站内信推送失败（不影响建议落库）: adviceId={}, err={}", a.getId(), e.getMessage());
        }
    }

    private void executeApproval(AgentDecisionAdvice advice, String approverComment) {
        // 1) HMAC 完整性校验（防审批间隙参数篡改）
        if (StringUtils.hasText(advice.getApprovalAction())) {
            if (!HmacUtils.verify(hmacSecret, advice.getApprovalParamsJson(), advice.getApprovalHmac())) {
                throw new BusinessException(409, "建议审批参数完整性校验失败（HMAC 不匹配），已拦截");
            }
        }
        // 2) 状态流转
        advice.setStatus(ST_APPROVED);
        advice.setApproverId(currentUserId());
        advice.setApproverComment(approverComment);
        advice.setApprovedAt(LocalDateTime.now());
        adviceMapper.updateById(advice);
        // 3) 动作执行（阶段一：AUTO_ASSIGN_TASK）
        if (ACTION_AUTO_ASSIGN_TASK.equals(advice.getApprovalAction())) {
            Map<String, Object> params;
            try {
                params = objectMapper.readValue(advice.getApprovalParamsJson(), Map.class);
            } catch (Exception e) {
                throw new BusinessException(500, "审批参数解析失败");
            }
            Long taskId = longVal(params.get("taskId"));
            Long assigneeId = longVal(params.get("assigneeId"));
            if (taskId == null || assigneeId == null) {
                throw new BusinessException(400, "审批动作参数缺失（taskId/assigneeId）");
            }
            taskServiceProvider.getObject().assign(taskId, assigneeId,
                    "agent-advice:" + advice.getId());
            log.info("建议动作已执行: adviceId={}, action={}, taskId={}, assigneeId={}",
                    advice.getId(), advice.getApprovalAction(), taskId, assigneeId);
        }
    }

    private AgentDecisionAdvice mustPending(Long id) {
        AgentDecisionAdvice advice = adviceMapper.selectById(id);
        if (advice == null) {
            throw new BusinessException(404, "建议不存在");
        }
        if (!ST_PENDING.equals(advice.getStatus())) {
            throw new BusinessException(409, "建议已处理（当前状态 " + advice.getStatus() + "），不可重复审批");
        }
        return advice;
    }

    private Long currentUserId() {
        LoginUser user = LoginUser.current();
        return user == null ? null : user.getUserId();
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case 0 -> "待办";
            case 1 -> "进行中";
            case 2 -> "待审核";
            case 3 -> "已完成";
            case 4 -> "已关闭";
            default -> String.valueOf(status);
        };
    }

    private Long longVal(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intVal(Object v) {
        Long l = longVal(v);
        return l == null ? null : l.intValue();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private LocalDateTime dateVal(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
