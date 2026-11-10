package com.dept.mgmt.event.bus.handlers;

import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预付款收款提醒处理器（AGT-08，T-113）：消费 contract.effective 事件
 * 合同生效 → 计算预付款金额（amount × payRatioPrepay/100）→ 站内信提醒部门领导
 * → 再发布 contract.payment.due 事件（收款台账/看板下游消费，事件链示范）
 *
 * <p>幂等披露：与 AgentTaskFinishedHandler 同口径，重复消费会重发通知；
 * 本期规模不引入消费记录表，Phase 2 收款台账建立后由台账唯一约束兜底。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractEffectivePaymentHandler implements DomainEventHandler {

    private final NotificationService notificationService;
    /**
     * 事件总线延迟注入：EventBusService 构造期收集全部 DomainEventHandler，
     * Handler 再回头注入 EventBusService 会形成构造环，用 ObjectProvider 打破
     */
    private final org.springframework.beans.factory.ObjectProvider<EventBusService> eventBusProvider;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.CONTRACT_EFFECTIVE;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) throws Exception {
        Object amountObj = payload.get("amount");
        Object ratioObj = payload.get("payRatioPrepay");
        if (amountObj == null || ratioObj == null) {
            log.warn("contract.effective 事件缺少金额/比例，跳过预付款提醒: {}", eventId);
            return;
        }
        BigDecimal amount = new BigDecimal(String.valueOf(amountObj));
        int ratio = Integer.parseInt(String.valueOf(ratioObj));
        BigDecimal prepayAmount = amount.multiply(BigDecimal.valueOf(ratio))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String contractNo = String.valueOf(payload.getOrDefault("contractNo", ""));
        String title = "预付款收款提醒";
        String content = "合同 " + contractNo + "《" + payload.getOrDefault("name", "") + "》（客户："
                + payload.getOrDefault("customerName", "") + "）已生效，预付款比例 " + ratio
                + "%，应收预付款 " + prepayAmount.stripTrailingZeros().toPlainString()
                + " 元，请及时跟进开票与收款。";

        // 通知部门领导（Phase 1 简化口径为 1 号账号，Phase 2 权限域完善后按财务/领导角色查询）
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of("title", title, "content", content));
        dto.setReceiverIds(List.of(1L));
        dto.setRelatedId(payload.get("contractId") == null ? null : Long.valueOf(String.valueOf(payload.get("contractId"))));
        NotificationSendResult result = notificationService.send(dto);

        // 事件链：contract.effective → contract.payment.due（下游收款台账/看板预留）
        Map<String, Object> duePayload = new HashMap<>(payload);
        duePayload.put("paymentStage", "PREPAY");
        duePayload.put("dueAmount", prepayAmount);
        duePayload.put("sourceEventId", eventId);
        eventBusProvider.getObject().publish(DomainEventType.CONTRACT_PAYMENT_DUE, duePayload);

        log.info("AGT-08 预付款提醒完成: eventId={}, contractNo={}, prepay={}, sent={}",
                eventId, contractNo, prepayAmount, result.getSent());
    }
}
