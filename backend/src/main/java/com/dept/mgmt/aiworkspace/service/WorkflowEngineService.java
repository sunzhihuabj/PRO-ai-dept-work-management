package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.WfInstance;
import com.dept.mgmt.aiworkspace.entity.WfInstanceNode;
import com.dept.mgmt.aiworkspace.entity.WfWorkflow;
import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.agent.service.MasterAgentService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.fineperm.annotation.RequirePerm;
import com.dept.mgmt.mapper.AgentTaskMapper;
import com.dept.mgmt.mapper.UserMessageMapper;
import com.dept.mgmt.mapper.WfInstanceMapper;
import com.dept.mgmt.mapper.WfInstanceNodeMapper;
import com.dept.mgmt.mapper.WfWorkflowMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务流程生命周期引擎（T-408C）
 *
 * <p>流转模型：DAG。节点完成（done/skipped）后激活「所有前驱已完成」的后继节点；
 * agent 节点自动提交 Agent 任务并由 {@link #pollAgentNodes()} 定时跟进终态；
 * approval 节点等待指定审批人通过/驳回；notification 节点落站内信即完成。
 * 异常：agent 任务失败 → 节点 failed + 实例 failed + 通知发起人，支持单节点重试。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WfWorkflowMapper workflowMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfInstanceNodeMapper nodeMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final UserMessageMapper userMessageMapper;
    private final com.dept.mgmt.mapper.SysUserMapper userMapper;
    private final MasterAgentService masterAgentService;
    private final CodeEngineService codeEngineService;
    private final ObjectMapper objectMapper;

    // ==================== 启动实例 ====================

    /**
     * 启动流程实例（仅 published 流程；快照隔离：后续流程定义变更不影响运行中实例）
     */
    @RequirePerm("api:wf:start")
    public WfInstance start(Long workflowId) {
        LoginUser user = current();
        WfWorkflow wf = workflowMapper.selectById(workflowId);
        if (wf == null) {
            throw new BusinessException(404, "流程不存在");
        }
        if (!"published".equals(wf.getStatus())) {
            throw new BusinessException(400, "仅已发布流程可启动");
        }
        JsonNode graph = parse(wf.getGraph());

        WfInstance inst = new WfInstance();
        inst.setInstanceNo(codeEngineService.nextCode("WFI", null));
        inst.setWorkflowId(wf.getId());
        inst.setWorkflowName(wf.getName());
        inst.setGraphSnapshot(wf.getGraph());
        inst.setStatus("running");
        inst.setStartedBy(user.getUsername());
        inst.setStartTime(LocalDateTime.now());
        instanceMapper.insert(inst);

        // 全节点落运行记录（pending），随后从 start 开始推进
        for (JsonNode n : graph.path("nodes")) {
            WfInstanceNode node = new WfInstanceNode();
            node.setInstanceId(inst.getId());
            node.setNodeKey(n.path("key").asText());
            node.setNodeName(n.path("name").asText(""));
            node.setNodeType(n.path("type").asText());
            node.setConfig(n.path("config").toString());
            node.setStatus("pending");
            node.setCreateTime(LocalDateTime.now());
            if ("approval".equals(node.getNodeType())) {
                node.setAssigneeUserId(resolveAssignee(n.path("config"), inst));
            }
            nodeMapper.insert(node);
        }
        // start 节点直接完成并激活后继
        WfInstanceNode start = nodeByKey(inst.getId(), "start");
        finishNode(start);
        advanceFrom(inst.getId(), "start");
        log.info("流程实例已启动: {} (workflow={}) by {}", inst.getInstanceNo(), wf.getWfCode(), user.getUsername());
        return inst;
    }

    // ==================== 定时跟进 agent 节点 ====================

    /**
     * 每 5 秒轮询运行中的 agent 节点，同步 Agent 任务终态并推进流程
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAgentNodes() {
        List<WfInstanceNode> running = nodeMapper.selectList(new LambdaQueryWrapper<WfInstanceNode>()
                .eq(WfInstanceNode::getNodeType, "agent")
                .eq(WfInstanceNode::getStatus, "running")
                .last("LIMIT 50"));
        for (WfInstanceNode node : running) {
            try {
                AgentTask task = agentTaskMapper.selectById(node.getAgentTaskId());
                if (task == null) {
                    failNode(node, "关联任务不存在: " + node.getAgentTaskId());
                    continue;
                }
                switch (task.getStatus()) {
                    case "done" -> {
                        finishNode(node);
                        advanceFrom(node.getInstanceId(), node.getNodeKey());
                    }
                    case "failed", "cancelled" -> failNode(node, StringUtils.hasText(task.getErrorMsg())
                            ? task.getErrorMsg() : "任务" + task.getStatus());
                    default -> { /* queued/running/pending_confirm 继续等待 */ }
                }
            } catch (Exception e) {
                log.error("轮询 agent 节点异常: nodeId={}", node.getId(), e);
            }
        }
    }

    // ==================== 人工介入 ====================

    /** 审批（通过/驳回）：仅指定审批人或部门领导可操作 */
    @RequirePerm("api:wf:approve")
    public void approve(Long instanceId, String nodeKey, boolean pass, String comment) {
        LoginUser user = current();
        WfInstanceNode node = mustNode(instanceId, nodeKey);
        if (!"waiting_approval".equals(node.getStatus())) {
            throw new BusinessException(400, "节点当前不在待审批状态");
        }
        boolean isAssignee = user.getUserId().equals(node.getAssigneeUserId());
        boolean isLeader = user.getRoles() != null && user.getRoles().contains("ROLE_DEPT_LEADER");
        if (!isAssignee && !isLeader) {
            throw new BusinessException(403, "仅节点审批人或部门领导可审批");
        }
        node.setApprovalComment(comment);
        if (pass) {
            finishNode(node);
            advanceFrom(instanceId, nodeKey);
        } else {
            failNode(node, "审批驳回" + (StringUtils.hasText(comment) ? "：" + comment : ""));
        }
    }

    /** 重试失败节点（agent 节点重新提交任务；实例回到 running） */
    @RequirePerm("api:wf:start")
    public void retry(Long instanceId, String nodeKey) {
        WfInstanceNode node = mustNode(instanceId, nodeKey);
        if (!"failed".equals(node.getStatus())) {
            throw new BusinessException(400, "仅失败节点可重试");
        }
        WfInstance inst = mustInstance(instanceId);
        if (!"failed".equals(inst.getStatus()) && !"running".equals(inst.getStatus())) {
            throw new BusinessException(400, "实例当前状态不可重试: " + inst.getStatus());
        }
        node.setStatus("pending");
        node.setErrorMsg(null);
        node.setStartTime(null);
        node.setFinishTime(null);
        nodeMapper.updateById(node);
        inst.setStatus("running");
        inst.setEndTime(null);
        instanceMapper.updateById(inst);
        activate(node);
        log.info("节点重试: instance={}, node={} by user:{}", instanceId, nodeKey,
                LoginUser.current() == null ? "?" : LoginUser.current().getUserId());
    }

    /** 取消实例（发起人或部门领导可操作；未完成节点全部置 skipped） */
    public void cancel(Long instanceId) {
        LoginUser user = current();
        WfInstance inst = mustInstance(instanceId);
        boolean isStarter = user.getUsername() != null && user.getUsername().equals(inst.getStartedBy());
        boolean isLeader = user.getRoles() != null && user.getRoles().contains("ROLE_DEPT_LEADER");
        if (!isStarter && !isLeader) {
            throw new BusinessException(403, "仅发起人或部门领导可取消实例");
        }
        if (!"running".equals(inst.getStatus()) && !"failed".equals(inst.getStatus())) {
            throw new BusinessException(400, "实例当前状态不可取消: " + inst.getStatus());
        }
        List<WfInstanceNode> open = nodeMapper.selectList(new LambdaQueryWrapper<WfInstanceNode>()
                .eq(WfInstanceNode::getInstanceId, instanceId)
                .in(WfInstanceNode::getStatus, List.of("pending", "running", "waiting_approval")));
        for (WfInstanceNode n : open) {
            n.setStatus("skipped");
            n.setFinishTime(LocalDateTime.now());
            nodeMapper.updateById(n);
        }
        inst.setStatus("cancelled");
        inst.setEndTime(LocalDateTime.now());
        instanceMapper.updateById(inst);
        log.info("流程实例取消: {} by {}", inst.getInstanceNo(), user.getUsername());
    }

    // ==================== 查询 ====================

    /** 实例列表 */
    public List<WfInstance> instances(Long workflowId, String status) {
        LambdaQueryWrapper<WfInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(workflowId != null, WfInstance::getWorkflowId, workflowId);
        wrapper.eq(StringUtils.hasText(status), WfInstance::getStatus, status);
        wrapper.orderByDesc(WfInstance::getId);
        wrapper.last("LIMIT 100");
        return instanceMapper.selectList(wrapper);
    }

    /** 实例详情（含节点运行记录，按流程图顺序） */
    public Map<String, Object> instanceDetail(Long instanceId) {
        WfInstance inst = mustInstance(instanceId);
        List<WfInstanceNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfInstanceNode>()
                .eq(WfInstanceNode::getInstanceId, instanceId)
                .orderByAsc(WfInstanceNode::getId));
        return Map.of("instance", inst, "nodes", nodes);
    }

    // ==================== 流转核心 ====================

    /**
     * 节点激活：按类型分发（agent 提交任务 / approval 等待审批 / notification 直接完成并继续推进）
     */
    private void activate(WfInstanceNode node) {
        WfInstance inst = mustInstance(node.getInstanceId());
        JsonNode config = parse(node.getConfig() == null ? "{}" : node.getConfig());
        node.setStartTime(LocalDateTime.now());
        switch (node.getNodeType()) {
            case "agent" -> {
                String capability = config.path("capabilityCode").asText("");
                String instruction = config.path("instruction").asText(node.getNodeName());
                try {
                    AgentTask task = masterAgentService.submitAs(instruction, capability, null,
                            resolveStarterUserId(inst));
                    node.setAgentTaskId(task.getId());
                    node.setStatus("running");
                } catch (BusinessException e) {
                    // 意图不明等提交失败 → 节点失败（可人工修正配置后重试）
                    failNode(node, "任务提交失败: " + e.getMessage());
                    return;
                }
            }
            case "approval" -> node.setStatus("waiting_approval");
            case "notification" -> {
                sendInstanceNotification(inst, config);
                finishNode(node);
                advanceFrom(node.getInstanceId(), node.getNodeKey());
                return;
            }
            default -> {
                finishNode(node);
                advanceFrom(node.getInstanceId(), node.getNodeKey());
                return;
            }
        }
        nodeMapper.updateById(node);
    }

    /**
     * 从指定节点推进：激活「前驱全部完成」的后继节点；无活跃后继且无进行中节点 → 实例完结
     */
    private void advanceFrom(Long instanceId, String fromKey) {
        WfInstance inst = mustInstance(instanceId);
        JsonNode graph = parse(inst.getGraphSnapshot());
        List<String> successors = new ArrayList<>();
        for (JsonNode e : graph.path("edges")) {
            if (fromKey.equals(e.path("from").asText())) {
                successors.add(e.path("to").asText());
            }
        }
        for (String key : successors) {
            WfInstanceNode target = nodeByKey(instanceId, key);
            if (target == null || !"pending".equals(target.getStatus())) {
                continue;
            }
            if (predecessorsDone(instanceId, graph, key)) {
                activate(target);
            }
        }
        checkInstanceCompleted(instanceId);
    }

    /** 节点所有前驱是否已完成/跳过 */
    private boolean predecessorsDone(Long instanceId, JsonNode graph, String nodeKey) {
        for (JsonNode e : graph.path("edges")) {
            if (nodeKey.equals(e.path("to").asText())) {
                WfInstanceNode pre = nodeByKey(instanceId, e.path("from").asText());
                if (pre == null || !("done".equals(pre.getStatus()) || "skipped".equals(pre.getStatus()))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 实例完结判定：无 pending/running/waiting_approval 节点 → completed；存在 failed 节点由 failNode 负责 */
    private void checkInstanceCompleted(Long instanceId) {
        WfInstance inst = mustInstance(instanceId);
        if (!"running".equals(inst.getStatus())) {
            return;
        }
        Long open = nodeMapper.selectCount(new LambdaQueryWrapper<WfInstanceNode>()
                .eq(WfInstanceNode::getInstanceId, instanceId)
                .in(WfInstanceNode::getStatus, List.of("pending", "running", "waiting_approval")));
        if (open == 0) {
            inst.setStatus("completed");
            inst.setEndTime(LocalDateTime.now());
            instanceMapper.updateById(inst);
            log.info("流程实例完成: {}", inst.getInstanceNo());
        }
    }

    private void finishNode(WfInstanceNode node) {
        node.setStatus("done");
        node.setFinishTime(LocalDateTime.now());
        nodeMapper.updateById(node);
    }

    /** 节点失败：实例置 failed + 站内信通知发起人（异常告警与干预入口） */
    private void failNode(WfInstanceNode node, String reason) {
        node.setStatus("failed");
        node.setErrorMsg(abbreviate(reason, 480));
        node.setFinishTime(LocalDateTime.now());
        nodeMapper.updateById(node);
        WfInstance inst = mustInstance(node.getInstanceId());
        if ("running".equals(inst.getStatus())) {
            inst.setStatus("failed");
            inst.setEndTime(LocalDateTime.now());
            instanceMapper.updateById(inst);
        }
        UserMessage msg = new UserMessage();
        msg.setUserId(resolveStarterUserId(inst));
        msg.setMessageType("流程告警");
        msg.setTitle("流程实例异常：" + inst.getInstanceNo());
        msg.setContent("节点「" + node.getNodeName() + "」执行失败：" + reason + "，请到 AI 助手-流程面板介入处理（重试/取消）。");
        msg.setIsRead(0);
        userMessageMapper.insert(msg);
        log.warn("流程节点失败: instance={}, node={}, reason={}", inst.getInstanceNo(), node.getNodeKey(), reason);
    }

    // ==================== 内部工具 ====================

    private void sendInstanceNotification(WfInstance inst, JsonNode config) {
        UserMessage msg = new UserMessage();
        Long targetId = "starter".equalsIgnoreCase(config.path("target").asText("starter"))
                ? resolveStarterUserId(inst)
                : config.path("targetUserId").asLong(0L);
        msg.setUserId(targetId);
        msg.setMessageType("流程通知");
        msg.setTitle(StringUtils.hasText(config.path("title").asText())
                ? config.path("title").asText() : "流程通知：" + inst.getInstanceNo());
        msg.setContent(config.path("content").asText("流程 " + inst.getWorkflowName() + " 有新进展。"));
        msg.setIsRead(0);
        userMessageMapper.insert(msg);
    }

    /** 审批人解析：config.assignee=starter → 发起人；数字字符串 → 指定用户ID */
    private Long resolveAssignee(JsonNode config, WfInstance inst) {
        String assignee = config.path("assignee").asText("starter");
        if ("starter".equalsIgnoreCase(assignee)) {
            return resolveStarterUserId(inst);
        }
        try {
            return Long.parseLong(assignee);
        } catch (NumberFormatException e) {
            return resolveStarterUserId(inst);
        }
    }

    /** 发起人ID解析：startedBy 存用户名，查 sys_user 反查 ID（失败回落 1 号管理员） */
    private Long resolveStarterUserId(WfInstance inst) {
        try {
            return userLookupCache.computeIfAbsent(
                    inst.getStartedBy() == null ? "" : inst.getStartedBy(), this::doUserLookup);
        } catch (Exception e) {
            log.warn("发起人ID解析失败，回落管理员: username={}", inst.getStartedBy());
            return 1L;
        }
    }

    private Long doUserLookup(String username) {
        if (!StringUtils.hasText(username)) {
            return 1L;
        }
        com.dept.mgmt.entity.SysUser user = userMapper.selectOne(new LambdaQueryWrapper<com.dept.mgmt.entity.SysUser>()
                .eq(com.dept.mgmt.entity.SysUser::getUsername, username).last("LIMIT 1"));
        return user == null ? 1L : user.getId();
    }

    /** 用户名→ID 本地缓存（流程运行期稳定，避免每节点查库） */
    private final java.util.Map<String, Long> userLookupCache = new java.util.concurrent.ConcurrentHashMap<>();

    private WfInstanceNode mustNode(Long instanceId, String nodeKey) {
        WfInstanceNode node = nodeMapper.selectOne(new LambdaQueryWrapper<WfInstanceNode>()
                .eq(WfInstanceNode::getInstanceId, instanceId)
                .eq(WfInstanceNode::getNodeKey, nodeKey));
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }
        return node;
    }

    private WfInstanceNode nodeByKey(Long instanceId, String key) {
        return nodeMapper.selectOne(new LambdaQueryWrapper<WfInstanceNode>()
                .eq(WfInstanceNode::getInstanceId, instanceId)
                .eq(WfInstanceNode::getNodeKey, key));
    }

    private WfInstance mustInstance(Long id) {
        WfInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            throw new BusinessException(404, "流程实例不存在");
        }
        return inst;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            throw new BusinessException(500, "流程图 JSON 解析失败");
        }
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private LoginUser current() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }
}
