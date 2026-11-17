package com.dept.mgmt.event.bus.handlers;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.model.DomainEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 项目创建事件 → 立项预审建议（阶段一多 Agent 协同）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCreatedAdviceHandler implements DomainEventHandler {

    private final DecisionAdviceService adviceService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.PROJECT_CREATED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) {
        adviceService.onProjectCreated(payload);
    }
}
