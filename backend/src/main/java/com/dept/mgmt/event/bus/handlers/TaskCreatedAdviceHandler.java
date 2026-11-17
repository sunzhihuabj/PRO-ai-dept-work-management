package com.dept.mgmt.event.bus.handlers;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.model.DomainEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务创建事件 → 要素完整性/指派建议（阶段一多 Agent 协同）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreatedAdviceHandler implements DomainEventHandler {

    private final DecisionAdviceService adviceService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.TASK_CREATED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) {
        adviceService.onTaskCreated(payload);
    }
}
