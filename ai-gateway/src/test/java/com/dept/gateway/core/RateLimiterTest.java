package com.dept.gateway.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 分钟滑动窗口限流器测试（Clock 注入，不依赖 Spring 上下文）
 * 覆盖：阈值内放行 / 超阈值熔断 / 熔断 10 分钟后恢复
 *
 * @author dept-mgmt
 */
class RateLimiterTest {

    @Test
    void withinLimit_passes() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 35; i++) {
            assertNull(limiter.tryAcquire("CH1", 35), "阈值内第 " + (i + 1) + " 次应放行");
        }
    }

    @Test
    void overLimit_triggersCircuitBreak() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 35; i++) {
            limiter.tryAcquire("CH2", 35);
        }
        Long waitMs = limiter.tryAcquire("CH2", 35);
        assertNotNull(waitMs, "超过阈值应拒绝");
        // 用户硬性规则：熔断 10 分钟
        assertEquals(10 * 60 * 1000L, waitMs);
    }

    @Test
    void circuitBreak_recoversAfterTenMinutes() {
        RateLimiter limiter = new RateLimiter();
        long base = Instant.parse("2026-11-10T10:00:00Z").toEpochMilli();
        limiter.setClock(Clock.fixed(Instant.ofEpochMilli(base), ZoneOffset.UTC));
        for (int i = 0; i < 35; i++) {
            limiter.tryAcquire("CH3", 35);
        }
        assertNotNull(limiter.tryAcquire("CH3", 35), "应触发熔断");
        // 前进 9 分钟：仍熔断
        limiter.setClock(Clock.fixed(Instant.ofEpochMilli(base + 9 * 60 * 1000L), ZoneOffset.UTC));
        assertNotNull(limiter.tryAcquire("CH3", 35), "9 分钟后仍应熔断");
        // 前进 11 分钟：熔断解除（新窗口）
        limiter.setClock(Clock.fixed(Instant.ofEpochMilli(base + 11 * 60 * 1000L), ZoneOffset.UTC));
        assertNull(limiter.tryAcquire("CH3", 35), "10 分钟后熔断应解除");
    }

    @Test
    void differentChannels_isolated() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 35; i++) {
            limiter.tryAcquire("CHA", 35);
        }
        assertNull(limiter.tryAcquire("CHB", 35), "不同通道限流互相隔离");
    }
}
