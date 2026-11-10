package com.dept.mgmt.event.bus;

/**
 * 事件投递通道接口（T-108）
 * 当前默认实现：本地事件表（EventBusService 内轮询投递）；
 * RabbitMQ 通道预留：MQ 环境就绪后实现本接口并注册（@ConditionalOnProperty event.channel=rabbitmq），
 * 业务发布代码零改动。
 *
 * @author dept-mgmt
 */
public interface DomainEventChannel {

    /**
     * 通道标识（LOCAL_DB / RABBITMQ）
     */
    String channelCode();
}
