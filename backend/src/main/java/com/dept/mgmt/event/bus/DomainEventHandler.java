package com.dept.mgmt.event.bus;

import com.dept.mgmt.event.model.DomainEventType;

import java.util.Map;

/**
 * 跨域事件处理器 SPI（T-108）
 * 域模块实现本接口并注册为 Spring Bean，事件总线按事件类型分发；消费必须幂等（事件 ID 去重）
 *
 * @author dept-mgmt
 */
public interface DomainEventHandler {

    /**
     * 处理的事件类型
     */
    DomainEventType eventType();

    /**
     * 消费事件（幂等：同一 event_id 重复消费不应产生副作用）
     *
     * @param eventId 事件唯一标识
     * @param payload 载荷
     */
    void handle(String eventId, Map<String, Object> payload) throws Exception;
}
