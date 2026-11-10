/**
 * 事件发布订阅抽象
 *
 * <p>职责：统一的事件发布/订阅接口，RabbitMQ 为主通道，
 * 本地事件表（DB）兜底轮询重试；提供事件 ID 去重的幂等消费框架。
 *
 * <p>对应规划任务：T-108。当前状态：骨架占位（Phase 0 / T-004）。
 */
package com.dept.mgmt.event.bus;
