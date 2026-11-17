package com.dept.mgmt.event.bus.handlers;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.model.DomainEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 项目进度事件（阶段完成）→ 后续动作建议（阶段一多 Agent 协同；
 * 同一事件双消费者：ProjectProgressHandler 通知合同侧 + 本处理器生成 Agent 建议）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectProgressAdviceHandler implements DomainEventHandler {

    private final DecisionAdviceService adviceService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.PROJECT_PROGRESS_UPDATED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) {
        adviceService.onProjectProgress(payload);
    }
}
