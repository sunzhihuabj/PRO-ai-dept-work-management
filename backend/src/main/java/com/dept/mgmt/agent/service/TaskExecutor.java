package com.dept.mgmt.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.agent.enums.TaskStatus;
import com.dept.mgmt.agent.handler.CapabilityHandler;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.mapper.AgentTaskMapper;
import com.dept.mgmt.platform.identity.service.EffectivePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 任务执行器（T-107）
 * DB 队列轮询 + 乐观锁领取 + Semaphore 并发控制 ≤5
 * （选型披露：规划为 Redis 队列；本机无 Redis 环境，DB 轮询队列在单体部署下语义等价且更易调试，
 * Redis 通道预留待环境就绪切换）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component("agentTaskExecutor")
public class TaskExecutor {

    /** 并发控制上限（规划硬约束：≤5） */
    private static final int MAX_CONCURRENCY = 5;

    /** 单次轮询领取数 */
    private static final int BATCH_SIZE = 10;

    private final AgentTaskMapper taskMapper;
    private final MasterAgentService masterAgentService;
    private final EffectivePermissionService effectivePermissionService;
    private final EventBusService eventBusService;
    private final com.dept.mgmt.agent.service.AutonomyService autonomyService;
    private final Semaphore slots;
    private final Map<String, CapabilityHandler> handlers;

    public TaskExecutor(AgentTaskMapper taskMapper, MasterAgentService masterAgentService,
                        EffectivePermissionService effectivePermissionService,
                        EventBusService eventBusService,
                        com.dept.mgmt.agent.service.AutonomyService autonomyService,
                        List<CapabilityHandler> handlerBeans) {
        this.taskMapper = taskMapper;
        this.masterAgentService = masterAgentService;
        this.effectivePermissionService = effectivePermissionService;
        this.eventBusService = eventBusService;
        this.autonomyService = autonomyService;
        this.slots = new Semaphore(MAX_CONCURRENCY);
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(CapabilityHandler::capabilityCode, Function.identity()));
    }

    /**
     * 每 2 秒轮询一次队列，领取任务异步执行（并发 ≤5）
     */
    @Scheduled(fixedDelay = 2000)
    public void poll() {
        if (slots.availablePermits() == 0) {
            return;
        }
        List<AgentTask> queued = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getStatus, TaskStatus.QUEUED.getCode())
                .orderByAsc(AgentTask::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (AgentTask task : queued) {
            if (!slots.tryAcquire()) {
                return;
            }
            // 乐观锁领取：仅一个执行线程能成功置 running
            String executedBy = "agent:" + task.getCallerUserId() + ":" + task.getCapabilityCode();
            if (taskMapper.claim(task.getId(), executedBy) != 1) {
                slots.release();
                continue;
            }
            AgentTask claimed = taskMapper.selectById(task.getId());
            masterAgentService.transition(task.getId(), TaskStatus.QUEUED.getCode(), TaskStatus.RUNNING.getCode(),
                    executedBy, "执行器领取");
            Thread worker = new Thread(() -> {
                try {
                    execute(claimed, executedBy);
                } finally {
                    slots.release();
                }
            }, "agent-exec-" + task.getId());
            worker.setDaemon(true);
            worker.start();
        }
    }

    /**
     * 执行单个任务：T-102 权限引擎校验 → 处理器分发 → 结果落库
     */
    private void execute(AgentTask task, String executedBy) {
        try {
            CapabilityHandler handler = handlers.get(task.getCapabilityCode());
            if (handler == null) {
                // 该能力点尚无自动执行实现：进入待确认（建议级语义）
                task.setStatus(TaskStatus.PENDING_CONFIRM.getCode());
                task.setResultText("该能力暂未开通自动执行，已转待确认；您可等待能力上线后确认执行，或转人工处理。");
                task.setAiGenerated(0);
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                masterAgentService.transition(task.getId(), TaskStatus.RUNNING.getCode(),
                        TaskStatus.PENDING_CONFIRM.getCode(), executedBy, "能力点无自动执行处理器，转待确认");
                return;
            }
            // T-110 渐进自主：自主级别低于自动级（含管理员手动降档）或能力声明需确认 → 转待确认；
            // 用户已确认的任务（confirm 标记）跳过级别拦截，直接执行本次
            boolean needConfirm = !MasterAgentService.isConfirmed(task)
                    && (autonomyService.levelOf(task.getCapabilityCode()).getCode() < 2
                    || handler.requireConfirm());
            if (needConfirm) {
                task.setStatus(TaskStatus.PENDING_CONFIRM.getCode());
                task.setResultText("该能力当前为建议级（需人工确认后执行）。请审阅指令后在 Agent 工作台确认执行。");
                task.setAiGenerated(0);
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                masterAgentService.transition(task.getId(), TaskStatus.RUNNING.getCode(),
                        TaskStatus.PENDING_CONFIRM.getCode(), executedBy, "建议级能力等待人工确认");
                return;
            }
            // T-102 权限引擎校验（人类用户路径）
            var check = effectivePermissionService.checkForUser(task.getCallerUserId(), task.getCapabilityCode());
            if (!check.isAllowed()) {
                task.setStatus(TaskStatus.ESCALATED.getCode());
                task.setErrorMsg("权限校验未通过：" + check.getReason());
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                masterAgentService.transition(task.getId(), TaskStatus.RUNNING.getCode(),
                        TaskStatus.ESCALATED.getCode(), executedBy, "权限不足升级人工");
                return;
            }
            // 执行（AI 通道异常由网关降级，不阻塞主流程）；params JSON 解析失败按空参数处理
            java.util.Map<String, Object> params = parseParams(task.getParams());
            String result = handler.execute(task.getInstruction(), task.getCallerUserId(), params);
            boolean aiGenerated = result != null && !result.startsWith("【检索结果");
            task.setStatus(TaskStatus.DONE.getCode());
            task.setResultText(result);
            task.setAiGenerated(aiGenerated ? 1 : 0);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            masterAgentService.transition(task.getId(), TaskStatus.RUNNING.getCode(), TaskStatus.DONE.getCode(),
                    executedBy, "执行完成" + (aiGenerated ? "（AI 生成）" : ""));
            // 跨域通知走事件总线（T-108）：Agent 域不直接调用通知域
            eventBusService.publish(com.dept.mgmt.event.model.DomainEventType.AGENT_TASK_FINISHED,
                    Map.of("taskId", task.getId(), "taskNo", task.getTaskNo(), "callerUserId", task.getCallerUserId()));
            // T-110：记录一次成功执行（采纳率/错误率统计数据源）
            autonomyService.recordExecution(task.getCapabilityCode(), true);
        } catch (Exception e) {
            log.error("任务执行异常: taskNo={}", task.getTaskNo(), e);
            task.setStatus(TaskStatus.FAILED.getCode());
            task.setErrorMsg("执行失败：" + e.getClass().getSimpleName());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            masterAgentService.transition(task.getId(), TaskStatus.RUNNING.getCode(), TaskStatus.FAILED.getCode(),
                    executedBy, "执行异常");
            // T-110：记录一次失败执行
            autonomyService.recordExecution(task.getCapabilityCode(), false);
        }
    }

    /** 任务扩展参数 JSON → Map（空/非法返回空 Map，不阻塞执行） */
    private java.util.Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(paramsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.warn("任务 params 解析失败，按空参数执行: {}", e.getMessage());
            return java.util.Map.of();
        }
    }
}
