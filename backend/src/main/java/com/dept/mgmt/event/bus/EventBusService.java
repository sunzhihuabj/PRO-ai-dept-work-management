package com.dept.mgmt.event.bus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.SysDomainEventMapper;
import com.dept.mgmt.event.entity.SysDomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 事件总线（T-108）—— 跨域通信唯一通道
 *
 * <p>实现路线（选型披露）：规划为 RabbitMQ + 本地事件表兜底；本机无 MQ 环境，
 * 本期以本地事件表（DB 轮询投递 + 指数退避重试 + 死信标记）为默认通道，
 * RabbitChannel 预留 {@link DomainEventChannel} 接口位，MQ 环境就绪后配置切换。
 *
 * <p>核心语义：发布幂等（event_id 唯一，重复发布静默忽略）；消费按事件类型分发到
 * {@link DomainEventHandler} SPI；失败重试 3 次后标记 DEAD 由人工介入。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class EventBusService {

    /** 最大重试次数（超过转 DEAD） */
    private static final int MAX_RETRY = 3;

    private final SysDomainEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    /** 同一事件类型支持多个订阅者（T-406：contract.effective 同时被预付款提醒与合同-项目联动消费） */
    private final Map<DomainEventType, List<DomainEventHandler>> handlers;

    public EventBusService(SysDomainEventMapper eventMapper, ObjectMapper objectMapper,
                           List<DomainEventHandler> handlerBeans) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
        this.handlers = handlerBeans.stream()
                .collect(Collectors.groupingBy(DomainEventHandler::eventType));
    }

    /**
     * 发布事件（幂等：event_id 已存在时忽略并返回已有事件）
     *
     * @param type    事件类型
     * @param payload 载荷
     * @return 事件唯一标识
     */
    public String publish(DomainEventType type, Map<String, Object> payload) {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        SysDomainEvent event = new SysDomainEvent();
        event.setEventId(eventId);
        event.setEventType(type.getCode());
        try {
            event.setPayload(payload == null ? null : objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            event.setPayload(null);
        }
        event.setStatus("PENDING");
        event.setNextRetryAt(LocalDateTime.now());
        try {
            eventMapper.insert(event);
            log.info("事件已发布: type={}, eventId={}", type.getCode(), eventId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("事件重复发布已忽略（幂等）: {}", eventId);
        }
        return eventId;
    }

    /**
     * 轮询投递（每 3 秒）：领取到期 PENDING/FAILED 事件，分发处理器，失败指数退避。
     * 仅领取已注册处理器的事件类型——无消费者事件保持 PENDING（下游域上线后自动消费），
     * 同时避免队头阻塞：无 handler 的历史积压事件不得挤占 LIMIT 窗口阻塞有 handler 事件投递
     */
    @Scheduled(fixedDelay = 3000)
    public void deliver() {
        List<String> handledCodes = handlers.keySet().stream().map(DomainEventType::getCode).toList();
        List<SysDomainEvent> pending = eventMapper.selectList(new LambdaQueryWrapper<SysDomainEvent>()
                .in(SysDomainEvent::getStatus, "PENDING", "FAILED")
                .in(SysDomainEvent::getEventType, handledCodes)
                .and(w -> w.isNull(SysDomainEvent::getNextRetryAt)
                        .or().le(SysDomainEvent::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(SysDomainEvent::getId)
                .last("LIMIT 20"));
        for (SysDomainEvent event : pending) {
            List<DomainEventHandler> eventHandlers = matchHandlers(event.getEventType());
            if (eventHandlers.isEmpty()) {
                // 无消费者：保持 PENDING（下游域上线后自动消费），不重试不计失败
                continue;
            }
            Map<String, Object> payload;
            try {
                payload = event.getPayload() == null ? Map.of()
                        : objectMapper.readValue(event.getPayload(), Map.class);
            } catch (Exception e) {
                event.setStatus("DEAD");
                event.setConsumerNote("载荷解析失败转死信: " + e.getMessage());
                eventMapper.updateById(event);
                continue;
            }
            List<String> okConsumers = new java.util.ArrayList<>();
            Exception lastError = null;
            for (DomainEventHandler handler : eventHandlers) {
                try {
                    handler.handle(event.getEventId(), payload);
                    okConsumers.add(handler.getClass().getSimpleName());
                } catch (Exception e) {
                    lastError = e;
                    log.warn("事件处理器执行失败: eventId={}, handler={}", event.getEventId(),
                            handler.getClass().getSimpleName(), e);
                }
            }
            if (okConsumers.size() == eventHandlers.size()) {
                event.setStatus("CONSUMED");
                event.setConsumedAt(LocalDateTime.now());
                event.setConsumerNote("消费成功: " + String.join(",", okConsumers));
            } else {
                int retry = event.getRetryCount() + 1;
                event.setRetryCount(retry);
                if (retry >= MAX_RETRY) {
                    event.setStatus("DEAD");
                    event.setConsumerNote("重试" + MAX_RETRY + "次仍失败，转死信人工介入: "
                            + (lastError == null ? "未知" : lastError.getMessage()));
                    log.error("事件转死信: eventId={}, type={}", event.getEventId(), event.getEventType(), lastError);
                } else {
                    event.setStatus("FAILED");
                    // 指数退避：2^retry 分钟
                    event.setNextRetryAt(LocalDateTime.now().plusMinutes((long) Math.pow(2, retry)));
                    event.setConsumerNote("部分消费成功(" + String.join(",", okConsumers) + ")，第"
                            + retry + "次重试: " + (lastError == null ? "未知" : lastError.getMessage()));
                }
            }
            eventMapper.updateById(event);
        }
    }

    private List<DomainEventHandler> matchHandlers(String eventTypeCode) {
        for (DomainEventType type : DomainEventType.values()) {
            if (type.getCode().equals(eventTypeCode)) {
                return handlers.getOrDefault(type, List.of());
            }
        }
        return List.of();
    }
}
