package com.dept.gateway.core;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分钟级滑动窗口限流器（内存实现，网关单实例）
 * 商用 API 通道默认 35 次/分钟（用户硬性规则）；触发后该通道熔断 10 分钟
 *
 * @author dept-mgmt
 */
@Component
public class RateLimiter {

    /** 熔断时长：10 分钟 */
    private static final long CIRCUIT_BREAK_MS = 10 * 60 * 1000L;

    /** providerCode → 请求时间戳队列 */
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** providerCode → 熔断截止时间戳 */
    private final Map<String, Long> circuitUntil = new ConcurrentHashMap<>();

    /** 时钟可注入（测试用） */
    private Clock clock = Clock.systemUTC();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 尝试获取一次调用资格
     *
     * @param providerCode 通道配置编码
     * @param rpmLimit     每分钟上限
     * @return null=放行；否则返回需等待的毫秒数（熔断剩余时间）
     */
    public synchronized Long tryAcquire(String providerCode, int rpmLimit) {
        long now = clock.millis();
        // 熔断检查
        Long until = circuitUntil.get(providerCode);
        if (until != null && until > now) {
            return until - now;
        }
        if (until != null) {
            circuitUntil.remove(providerCode);
        }
        // 滑动窗口清理
        Deque<Long> window = windows.computeIfAbsent(providerCode, k -> new ArrayDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() >= Duration.ofMinutes(1).toMillis()) {
            window.pollFirst();
        }
        if (window.size() >= rpmLimit) {
            // 触发熔断：窗口最早请求满 1 分钟后恢复，但按规则固定熔断 10 分钟
            long breakUntil = now + CIRCUIT_BREAK_MS;
            circuitUntil.put(providerCode, breakUntil);
            return CIRCUIT_BREAK_MS;
        }
        window.addLast(now);
        return null;
    }
}
