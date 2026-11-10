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
 * 红灯升级事件消费者（T-209）：M1 看板 → 通知中心（站内信推送管理员）
 * 红灯 OPEN 超期未处理或过堂后逾期 → ESCALATED 时发布 redcard.escalated 事件
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedcardEscalatedHandler implements DomainEventHandler {

    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.REDCARD_ESCALATED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) throws Exception {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of(
                "title", "红灯升级推送",
                "content", "看板红灯「" + payload.getOrDefault("ruleName", "") + "」已超期未处理，"
                        + "当前指标值 " + payload.getOrDefault("metricValue", "")
                        + "，请立即在早会过堂指定责任人跟进"));
        dto.setReceiverIds(List.of(1L));
        NotificationSendResult result = notificationService.send(dto);
        log.info("红灯升级推送已发送: eventId={}, rule={}, sent={}",
                eventId, payload.get("ruleCode"), result.getSent());
    }
}
