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
 * 变更生效事件消费者（T-302）：W5 变更域 → 通知（批量准备提醒）
 * ECN 实施发布 ecn.changed 事件，推送生产/采购关注批量齐套与旧版回收
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EcnChangedHandler implements DomainEventHandler {

    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.ECN_CHANGED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) throws Exception {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of(
                "title", "变更生效提醒",
                "content", "ECN「" + payload.getOrDefault("ecnNo", "") + "」（" + payload.getOrDefault("impactLevel", "")
                        + " 级变更）已实施生效，新方案已绑定；请按新方案核对批量物料齐套与旧版资料回收任务"));
        dto.setChannel("DINGTALK");
        dto.setReceiverIds(List.of(1L));
        NotificationSendResult result = notificationService.send(dto);
        log.info("ecn.changed 批量准备提醒已发送: eventId={}, ecnNo={}, channel={}, sent={}",
                eventId, payload.get("ecnNo"), result.getChannel(), result.getSent());
    }
}
