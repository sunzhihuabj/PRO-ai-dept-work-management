package com.dept.mgmt.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.agent.entity.AgentTaskEvent;
import com.dept.mgmt.agent.enums.TaskStatus;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AgentTaskEventMapper;
import com.dept.mgmt.mapper.AgentTaskMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * MasterAgent 调度服务（T-107）：指令提交 + 意图识别 + 任务单管理
 * 执行调度见 {@link TaskExecutor}
 *
 * <p>意图识别（基础规则版）：指令与能力点名称的子串匹配 + 场景关键词映射，输出置信度；
 * LLM 意图识别（目标 ≥90%）在商用 AI 通道真实启用后切换（预留 @PromoteApp 注明）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterAgentService {

    /** 意图识别：能力点 → 触发关键词（小写匹配指令） */
    private static final Map<String, List<String>> INTENT_KEYWORDS = Map.of(
            "CAP_KB_SEARCH", List.of("知识", "检索", "查询", "查一下", "规定", "规程", "制度", "怎么办", "多少", "是什么"),
            "CAP_DOC_SUMMARY", List.of("总结", "摘要", "日报", "周报", "纪要", "整理"));

    private final AgentTaskMapper taskMapper;
    private final AgentTaskEventMapper eventMapper;
    private final CodeEngineService codeEngineService;
    private final AutonomyService autonomyService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 提交指令 → 意图识别 → 建任务单（queued）
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentTask submit(String instruction, String capabilityCode) {
        if (!StringUtils.hasText(instruction) || instruction.length() > 2000) {
            throw new BusinessException(400, "指令为空或超过 2000 字符");
        }
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        // 意图识别（显式指定能力点时置信度 1.0）
        String routed = capabilityCode;
        double confidence;
        if (StringUtils.hasText(routed)) {
            confidence = 1.0;
        } else {
            Map.Entry<String, Double> intent = recognize(instruction);
            routed = intent.getKey();
            confidence = intent.getValue();
            if (!StringUtils.hasText(routed)) {
                // 意图不明：给出明确提示（不阻塞用户，可带 capabilityCode 重试）
                throw new BusinessException(400,
                        "无法识别该指令对应的能力，请明确意图（如：检索知识库 / 生成日报摘要），或在指令中直接描述需求");
            }
        }
        AgentTask task = new AgentTask();
        task.setTaskNo(codeEngineService.nextCode("AGTT", null));
        task.setCapabilityCode(routed);
        task.setInstruction(instruction);
        task.setStatus(TaskStatus.QUEUED.getCode());
        task.setCallerUserId(user.getUserId());
        task.setAgentInstanceKey("user:" + user.getUserId());
        task.setIntentConfidence(confidence);
        task.setExecutedBy("user:" + user.getUserId());
        taskMapper.insert(task);
        transition(task.getId(), null, TaskStatus.QUEUED.getCode(), "user:" + user.getUserId(), "指令提交，意图=" + routed);
        log.info("Agent 任务已创建: taskNo={}, capability={}, confidence={}", task.getTaskNo(), routed, confidence);
        return task;
    }

    /**
     * 我的任务列表
     */
    public List<AgentTask> myTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getCallerUserId, userId)
                .orderByDesc(AgentTask::getCreateTime)
                .last("LIMIT 50"));
    }

    /**
     * 任务详情（含事件时间线）
     */
    public Map<String, Object> detail(Long taskId, Long userId) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getCallerUserId().equals(userId)) {
            throw new BusinessException(404, "任务不存在");
        }
        List<AgentTaskEvent> events = eventMapper.selectList(new LambdaQueryWrapper<AgentTaskEvent>()
                .eq(AgentTaskEvent::getTaskId, taskId)
                .orderByAsc(AgentTaskEvent::getCreateTime));
        return Map.of("task", task, "events", events);
    }

    /**
     * 确认执行（pending_confirm → queued 重新排队；params 打已确认标记，执行器跳过级别拦截）
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long taskId, Long userId) {
        AgentTask task = mustOwn(taskId, userId);
        if (!TaskStatus.PENDING_CONFIRM.getCode().equals(task.getStatus())) {
            throw new BusinessException(400, "任务当前状态不可确认: " + task.getStatus());
        }
        task.setStatus(TaskStatus.QUEUED.getCode());
        task.setParams(mergeConfirmed(task.getParams()));
        taskMapper.updateById(task);
        transition(taskId, TaskStatus.PENDING_CONFIRM.getCode(), TaskStatus.QUEUED.getCode(),
                "user:" + userId, "用户确认执行（本次跳过级别拦截）");
    }

    /** 在 params JSON 上合并 confirmed 标记 */
    private String mergeConfirmed(String params) {
        try {
            java.util.Map<String, Object> map = StringUtils.hasText(params)
                    ? objectMapper.readValue(params, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
            })
                    : new java.util.HashMap<>();
            map.put("confirmed", true);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"confirmed\":true}";
        }
    }

    /** 任务是否已被用户确认过（供执行器判断，静态工具避免循环依赖） */
    public static boolean isConfirmed(AgentTask task) {
        if (!StringUtils.hasText(task.getParams())) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(task.getParams(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                    }).get("confirmed"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 取消任务（排队/待确认可取消；终态任务拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long taskId, Long userId) {
        AgentTask task = mustOwn(taskId, userId);
        if (TaskStatus.DONE.getCode().equals(task.getStatus())) {
            throw new BusinessException(400, "已完成的任务不可取消");
        }
        if (TaskStatus.RUNNING.getCode().equals(task.getStatus())) {
            throw new BusinessException(400, "任务执行中，暂不可取消");
        }
        task.setStatus(TaskStatus.CANCELLED.getCode());
        task.setFinishedAt(java.time.LocalDateTime.now());
        taskMapper.updateById(task);
        transition(taskId, task.getStatus(), TaskStatus.CANCELLED.getCode(), "user:" + userId, "用户取消");
    }

    /**
     * 状态流转留痕（供 TaskExecutor 复用）
     */
    public void transition(Long taskId, String from, String to, String operator, String detail) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setTaskId(taskId);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setOperator(operator);
        event.setDetail(detail);
        eventMapper.insert(event);
    }

    private AgentTask mustOwn(Long taskId, Long userId) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getCallerUserId().equals(userId)) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    /**
     * 结果三态反馈：更新任务标记 + 累计到能力策略（T-110 数据源）
     */
    @Transactional(rollbackFor = Exception.class)
    public void feedback(Long taskId, Long userId, String feedback) {
        AgentTask task = mustOwn(taskId, userId);
        if (!TaskStatus.DONE.getCode().equals(task.getStatus())) {
            throw new BusinessException(400, "仅已完成任务可反馈");
        }
        if (!List.of("ACCEPTED", "MODIFIED", "REJECTED").contains(feedback)) {
            throw new BusinessException(400, "反馈仅允许 ACCEPTED/MODIFIED/REJECTED");
        }
        task.setResultFeedback(feedback);
        taskMapper.updateById(task);
        autonomyService.feedback(task.getCapabilityCode(), feedback);
        transition(taskId, TaskStatus.DONE.getCode(), TaskStatus.DONE.getCode(),
                "user:" + userId, "结果反馈: " + feedback);
    }

    /**
     * 能力自主策略列表
     */
    public Object autonomyPolicies() {
        return autonomyService.list();
    }

    /**
     * 手动调整自主级别
     */
    public void adjustLevel(String capabilityCode, int level, Long userId) {
        autonomyService.adjustLevel(capabilityCode, level);
        log.info("管理员手动调整自主级别: capability={}, level={}, by=user:{}", capabilityCode, level, userId);
    }

    /**
     * 手动触发升降级评估
     */
    public Object evaluateAll(Long userId) {
        int changed = autonomyService.evaluateAll();
        log.info("手动触发自主评估: changed={}, by=user:{}", changed, userId);
        return Map.of("changed", changed);
    }

    /**
     * 规则版意图识别：关键词命中数 / 关键词总数 作为置信度；无命中返回 null
     */
    private Map.Entry<String, Double> recognize(String instruction) {
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            long hits = entry.getValue().stream().filter(instruction::contains).count();
            if (hits > 0) {
                double score = (double) hits / entry.getValue().size();
                if (score > bestScore) {
                    bestScore = score;
                    best = entry.getKey();
                }
            }
        }
        if (best == null) {
            return Map.entry("", 0.0);
        }
        // 置信度下限 0.5（命中即半信），上限 0.9（规则版非确定）
        return Map.entry(best, Math.min(0.5 + bestScore / 2, 0.9));
    }
}
