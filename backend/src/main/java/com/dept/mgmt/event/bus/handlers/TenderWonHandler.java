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
 * 中标事件消费者（T-301）：W1 投标域 → 通知（启动合同谈判/看板中标指标）
 * 中标公示登记（status=WON）时发布 tender.won 事件
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderWonHandler implements DomainEventHandler {

    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.TENDER_WON;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) throws Exception {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of(
                "title", "中标提醒",
                "content", "标讯「" + payload.getOrDefault("title", "") + "」已中标公示（预算 "
                        + payload.getOrDefault("budget", "") + "），请启动 B-16 合同谈判流程并跟踪投标承诺冻结（B-17）"));
        dto.setChannel("DINGTALK");
        dto.setReceiverIds(List.of(1L));
        NotificationSendResult result = notificationService.send(dto);
        log.info("tender.won 中标提醒已发送: eventId={}, tenderNo={}, channel={}, sent={}",
                eventId, payload.get("tenderNo"), result.getChannel(), result.getSent());
    }
}
