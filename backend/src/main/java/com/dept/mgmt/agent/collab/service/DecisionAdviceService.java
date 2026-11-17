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
    public static final String SCENE_TOOL_APPROVAL = "TOOL_APPROVAL";
    /** B5：Hermes run 审批中断（waiting_for_approval）映射建议 */
    public static final String SCENE_HERMES_APPROVAL = "HERMES_APPROVAL";

    /** 审批动作：AUTO_ASSIGN_TASK = 内置指派；OPS_* = 审批动作注册表（A5）；HERMES_RESOLVE = Hermes 审批回调（B5） */
    public static final String ACTION_AUTO_ASSIGN_TASK = "AUTO_ASSIGN_TASK";
    public static final String ACTION_OPS_PREFIX = "OPS_";
    public static final String ACTION_HERMES_RESOLVE = "HERMES_RESOLVE";

    /** 状态 */
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";

    /** 置信度自动建议阈值（低于强制人工，业界共识 0.85 起步） */
    public static final double CONFIDENCE_THRESHOLD = 0.85;

    private final AgentDecisionAdviceMapper adviceMapper;
    private final ObjectProvider<com.dept.mgmt.service.TaskService> taskServiceProvider;
    private final ObjectProvider<com.dept.mgmt.aiworkspace.ops.SystemOpsService> systemOpsServiceProvider;
    private final com.dept.mgmt.agent.collab.support.ApprovalActionRegistry actionRegistry;
    private final com.dept.mgmt.mapper.SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String hmacSecret;
    private final com.dept.mgmt.common.client.HermesService hermesService;

    public DecisionAdviceService(AgentDecisionAdviceMapper adviceMapper,
                                 ObjectProvider<com.dept.mgmt.service.TaskService> taskServiceProvider,
                                 ObjectProvider<com.dept.mgmt.aiworkspace.ops.SystemOpsService> systemOpsServiceProvider,
                                 com.dept.mgmt.agent.collab.support.ApprovalActionRegistry actionRegistry,
                                 com.dept.mgmt.mapper.SysUserMapper userMapper,
                                 NotificationService notificationService,
                                 ObjectMapper objectMapper,
                                 com.dept.mgmt.common.client.HermesService hermesService,
                                 @Value("${app.advice.hmac-secret:dept-advice-hmac-2026r2-dev}") String hmacSecret) {
        this.adviceMapper = adviceMapper;
        this.taskServiceProvider = taskServiceProvider;
        this.systemOpsServiceProvider = systemOpsServiceProvider;
        this.actionRegistry = actionRegistry;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.hermesService = hermesService;
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

    /**
     * B5：Hermes run 审批中断映射建议（幂等）——run 轮询检测到 waiting_for_approval 时调用：
     * 以 (runId, requestId) 查重，未生成过才落 HERMES_APPROVAL 场景 PENDING 建议
     * （approvalAction=HERMES_RESOLVE，params={runId, requestId, actingUserId}，HMAC 锁定）。
     * 采纳 → resolveApproval(runId, requestId, true)；驳回 → deny（见 approve/reject 分支）。
     *
     * @return 建议 ID；已存在（幂等跳过）返回已有一条的 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createHermesApproval(String runId, String requestId, String summary, Long actingUserId) {
        LambdaQueryWrapper<AgentDecisionAdvice> dup = new LambdaQueryWrapper<>();
        dup.eq(AgentDecisionAdvice::getScene, SCENE_HERMES_APPROVAL)
                .eq(AgentDecisionAdvice::getStatus, ST_PENDING)
                .like(AgentDecisionAdvice::getApprovalParamsJson, requestId)
                .orderByDesc(AgentDecisionAdvice::getId).last("LIMIT 1");
        AgentDecisionAdvice existing = adviceMapper.selectOne(dup);
        if (existing != null) {
            log.debug("Hermes 审批建议已存在（幂等跳过）: runId={}, requestId={}, adviceId={}",
                    runId, requestId, existing.getId());
            return existing.getId();
        }
        LinkedHashMap<String, Object> actionParams = new LinkedHashMap<>();
        actionParams.put("runId", runId);
        actionParams.put("requestId", requestId);
        actionParams.put("actingUserId", actingUserId);
        LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
        structured.put("runId", runId);
        structured.put("requestId", requestId);
        structured.put("summary", summary == null ? "" : summary);
        advice(SCENE_HERMES_APPROVAL, "HERMES_RUN", 0L, runId,
                "AI Agent 员工执行请求授权",
                "REQUEST",
                "Hermes run（" + runId + "）执行中请求敏感操作授权："
                        + (summary == null ? "（无摘要）" : summary)
                        + "。采纳=放行一次（once），驳回=拒绝执行（deny）；处理后回调 Hermes 恢复/终止该 run。",
                structured, 1.00, "HIGH", true, ACTION_HERMES_RESOLVE, actionParams,
                List.of(Map.of("source", "HERMES_RUN", "ref", runId + "/" + requestId)),
                actingUserId, 24);
        LambdaQueryWrapper<AgentDecisionAdvice> qw = new LambdaQueryWrapper<>();
        qw.eq(AgentDecisionAdvice::getScene, SCENE_HERMES_APPROVAL)
                .eq(AgentDecisionAdvice::getStatus, ST_PENDING)
                .orderByDesc(AgentDecisionAdvice::getId).last("LIMIT 1");
        AgentDecisionAdvice created = adviceMapper.selectOne(qw);
        if (created == null) {
            throw new BusinessException(500, "Hermes 审批建议落库失败");
        }
        log.info("Hermes 审批建议已生成: adviceId={}, runId={}, requestId={}", created.getId(), runId, requestId);
        return created.getId();
    }


    /**
     * 工具审批请求（A5 公开入口，供 B4 MCP 写工具 / B5 Hermes auth-required 中断映射调用）：
     * 白名单校验 → 委托人权限预校验 → 生成 requires_approval 建议（approval_params_json 含
     * actingUserId/toolName/params，HMAC-SHA256 锁定）→ 返回建议（含 ID，供回调关联）。
     *
     * @param toolName      Ops 写工具名（须在 ApprovalActionRegistry 白名单）
     * @param toolParams    工具执行参数（与 OpsParam 声明对齐）
     * @param actingUserId  委托发起人（执行身份，HMAC 锁定防篡改）
     * @param reason        委托理由（进入建议正文）
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentDecisionAdvice requestToolApproval(String toolName, Map<String, Object> toolParams,
                                                   Long actingUserId, String reason) {
        String action = actionRegistry.actionOf(toolName);
        if (action == null) {
            throw new BusinessException(400, "工具未登记审批动作（白名单外或不存在）: " + toolName);
        }
        com.dept.mgmt.aiworkspace.ops.OpsTool tool = actionRegistry.toolOf(action);
        if (tool == null) {
            throw new BusinessException(400, "审批动作对应的工具不存在: " + action);
        }
        // 委托人生成时预校验（审批执行时二次校验，防待确认期间权限变化）
        com.dept.mgmt.security.LoginUser actingUser = mustActingUser(actingUserId);
        String denied = systemOpsServiceProvider.getObject().checkPermission(tool, actingUser);
        if (denied != null) {
            throw new BusinessException(403, denied);
        }
        // 审批参数锁定（HMAC）
        LinkedHashMap<String, Object> actionParams = new LinkedHashMap<>();
        actionParams.put("actingUserId", actingUserId);
        actionParams.put("toolName", toolName);
        actionParams.put("params", toolParams == null ? Map.of() : toolParams);
        String preview = describeToolParams(tool, toolParams);
        LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
        structured.put("toolName", toolName);
        structured.put("action", action);
        structured.put("actingUser", actingUser.getUsername());
        structured.put("params", toolParams == null ? Map.of() : toolParams);
        advice(SCENE_TOOL_APPROVAL, "OPS_TOOL", 0L, toolName,
                "写操作待确认：" + tool.getTitle(),
                "REQUEST",
                "AI 委托执行「" + tool.getTitle() + "」" + preview
                        + "（委托人：" + actingUser.getUsername()
                        + (StringUtils.hasText(reason) ? "；理由：" + reason : "") + "）。请审批后执行。",
                structured, 1.00, "MEDIUM", true, action, actionParams,
                List.of(Map.of("source", "OPS_TOOL", "ref", toolName)),
                actingUserId, 24);
        // 取回刚生成的建议（advice() 内部 insert；同事务内最新一条同场景 PENDING）
        LambdaQueryWrapper<AgentDecisionAdvice> qw = new LambdaQueryWrapper<>();
        qw.eq(AgentDecisionAdvice::getScene, SCENE_TOOL_APPROVAL)
                .eq(AgentDecisionAdvice::getStatus, ST_PENDING)
                .orderByDesc(AgentDecisionAdvice::getId).last("LIMIT 1");
        AgentDecisionAdvice created = adviceMapper.selectOne(qw);
        if (created == null) {
            throw new BusinessException(500, "审批建议落库失败");
        }
        return created;
    }

    /** 工具参数摘要（进入建议正文） */
    private String describeToolParams(com.dept.mgmt.aiworkspace.ops.OpsTool tool, Map<String, Object> toolParams) {
        if (toolParams == null || toolParams.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("，参数：");
        for (com.dept.mgmt.aiworkspace.ops.OpsParam p : tool.getParams()) {
            Object v = toolParams.get(p.getName());
            if (v != null) {
                sb.append(p.getDesc()).append("=").append(v).append("；");
            }
        }
        return sb.toString();
    }

    /** 委托人身份解析（存在且未禁用） */
    private com.dept.mgmt.security.LoginUser mustActingUser(Long actingUserId) {
        if (actingUserId == null) {
            throw new BusinessException(400, "缺少委托发起人 actingUserId");
        }
        com.dept.mgmt.entity.SysUser su = userMapper.selectById(actingUserId);
        if (su == null || (su.getStatus() != null && su.getStatus() == 0)) {
            throw new BusinessException(401, "委托发起人无效或已禁用: " + actingUserId);
        }
        return new com.dept.mgmt.security.LoginUser(su.getId(), su.getUsername(), su.getRoleCodes(), List.of());
    }

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

    /** 审批驳回：仅状态流转，不执行动作；HERMES_RESOLVE 动作例外——驳回即回调 Hermes deny 终止 run（B5 双向闭环） */
    @Transactional(rollbackFor = Exception.class)
    public AgentDecisionAdvice reject(Long id, String approverComment) {
        AgentDecisionAdvice advice = mustPending(id);
        advice.setStatus(ST_REJECTED);
        advice.setApproverComment(approverComment);
        advice.setApproverId(currentUserId());
        advice.setApprovedAt(LocalDateTime.now());
        adviceMapper.updateById(advice);
        if (ACTION_HERMES_RESOLVE.equals(advice.getApprovalAction())) {
            resolveHermesRun(advice, false);
        }
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
        // 3) 动作执行：AUTO_ASSIGN_TASK（内置指派） / OPS_*（A5 审批动作注册表） / HERMES_RESOLVE（B5 回调）
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
        } else if (ACTION_HERMES_RESOLVE.equals(advice.getApprovalAction())) {
            resolveHermesRun(advice, true);
        } else if (advice.getApprovalAction() != null
                && advice.getApprovalAction().startsWith(ACTION_OPS_PREFIX)) {
            executeOpsToolAction(advice);
        }
    }

    /**
     * B5：HERMES_RESOLVE 动作执行——解析锁定的 runId/requestId（HMAC 已在 executeApproval 统一校验）
     * 回调 Hermes POST /v1/runs/{id}/approval 恢复（allow=true → once）/终止（false → deny）该 run。
     * 回调失败抛出异常回滚审批状态（建议保持 PENDING 可重试）。
     */
    private void resolveHermesRun(AgentDecisionAdvice advice, boolean allow) {
        Map<String, Object> params;
        try {
            params = objectMapper.readValue(advice.getApprovalParamsJson(), Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "审批参数解析失败");
        }
        String runId = str(params.get("runId"));
        String requestId = str(params.get("requestId"));
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(requestId)) {
            throw new BusinessException(400, "审批动作参数缺失（runId/requestId）");
        }
        if (!hermesService.resolveApproval(runId, requestId, allow)) {
            // 回调失败 → 异常回滚审批状态流转，建议保持 PENDING 可重试
            throw new BusinessException(503, "Hermes 审批回调失败，请稍后重试");
        }
        log.info("Hermes 审批回调完成: adviceId={}, runId={}, requestId={}, allow={}",
                advice.getId(), runId, requestId, allow);
    }

    /**
     * A5：OPS_* 审批动作执行——解析锁定的 actingUserId/toolName/params，
     * 二次校验（动作一致性 + 委托人有效 + 权限复核）后经注册表执行（precheck 复核 + executor）。
     */
    @SuppressWarnings("unchecked")
    private void executeOpsToolAction(AgentDecisionAdvice advice) {
        Map<String, Object> actionParams;
        try {
            actionParams = objectMapper.readValue(advice.getApprovalParamsJson(), Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "审批参数解析失败");
        }
        Long actingUserId = longVal(actionParams.get("actingUserId"));
        String toolName = str(actionParams.get("toolName"));
        Map<String, Object> toolParams = actionParams.get("params") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        // 一致性防错配/防伪造：工具名必须还原出与建议登记完全一致的动作编码
        if (!advice.getApprovalAction().equals(actionRegistry.actionOf(toolName))) {
            throw new BusinessException(409, "审批动作与工具不一致（可能被篡改），已拦截");
        }
        com.dept.mgmt.aiworkspace.ops.OpsTool tool = actionRegistry.toolOf(advice.getApprovalAction());
        if (tool == null) {
            throw new BusinessException(400, "审批动作未登记或不可执行: " + advice.getApprovalAction());
        }
        com.dept.mgmt.security.LoginUser actingUser = mustActingUser(actingUserId);
        // 执行前权限复核（H2：待确认期间权限可能已变化）
        String denied = systemOpsServiceProvider.getObject().checkPermission(tool, actingUser);
        if (denied != null) {
            throw new BusinessException(403, denied);
        }
        Object result = actionRegistry.execute(advice.getApprovalAction(), toolParams, actingUser);
        String resultText = result == null ? "执行完成" : String.valueOf(result);
        // 执行结果回写建议（evidence_json 追加，保持原 List<Map> 结构可追溯）
        try {
            List<Object> evidence = StringUtils.hasText(advice.getEvidenceJson())
                    ? objectMapper.readValue(advice.getEvidenceJson(), List.class)
                    : new java.util.ArrayList<>();
            evidence.add(Map.of("executedAt", LocalDateTime.now().toString(),
                    "executedAs", actingUser.getUsername(),
                    "resultPreview", resultText.length() > 200 ? resultText.substring(0, 200) + "…" : resultText));
            advice.setEvidenceJson(objectMapper.writeValueAsString(evidence));
            adviceMapper.updateById(advice);
        } catch (Exception e) {
            log.warn("审批执行结果回写失败（不影响执行）: adviceId={}, err={}", advice.getId(), e.getMessage());
        }
        log.info("OPS 审批动作已执行: adviceId={}, action={}, tool={}, actingUser={}",
                advice.getId(), advice.getApprovalAction(), toolName, actingUser.getUsername());
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
