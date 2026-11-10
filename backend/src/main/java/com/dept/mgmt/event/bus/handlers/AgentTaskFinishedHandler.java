package com.dept.mgmt.event.bus.handlers;

import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent 任务完成事件消费者（T-108 示范闭环）：Agent 域 → 通知中心（站内信）
 * 展示"模块间禁止直读表、只走事件总线"的标准接法；消费幂等：重复事件重新发送通知属可接受副作用，
 * 严格幂等场景应引入消费记录表（本期规模不引入）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskFinishedHandler implements DomainEventHandler {

    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.AGENT_TASK_FINISHED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) throws Exception {
        Object callerUserId = payload.get("callerUserId");
        if (callerUserId == null) {
            log.warn("事件缺少 callerUserId，跳过通知: {}", eventId);
            return;
        }
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of(
                "title", "Agent 任务完成",
                "content", "您的任务 " + payload.getOrDefault("taskNo", "") + " 已执行完成，结果可在 Agent 工作台查看"));
        dto.setReceiverIds(List.of(Long.valueOf(String.valueOf(callerUserId))));
        dto.setRelatedId(payload.get("taskId") == null ? null : Long.valueOf(String.valueOf(payload.get("taskId"))));
        NotificationSendResult result = notificationService.send(dto);
        log.info("Agent 任务完成通知已发送: eventId={}, sent={}", eventId, result.getSent());
    }
}
