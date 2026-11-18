package com.dept.mgmt.aiworkspace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hermes 事件 SSE 下行通道（V2.0 M4 异常节点实时提示）。
 *
 * <p>前端 EventSource 订阅 {@code GET /v1/ai/ws/hermes/stream?token=}，
 * 后台 run 事件消费点（HermesDelegateService）把白名单事件
 * （run.failed / approval.request / run.completed）实时推给会话归属用户；
 * 前端右上角风险 Toast + 声音提醒（联动 E2 嘴巴）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class HermesSseService {

    /** 连接保活超时（30 分钟，前端 EventSource 自动重连） */
    private static final long EMITTER_TIMEOUT = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 注册用户 SSE 连接 */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        try {
            // 立即回执一条连接确认，触发 EventSource onopen
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            list.remove(emitter);
        }
        log.info("SSE 连接建立: userId={}, online={}", userId, list.size());
        return emitter;
    }

    /** 推送事件给指定用户（无连接静默丢弃，不报错） */
    public void push(Long userId, String eventType, Map<String, Object> payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(payload));
            } catch (Exception e) {
                list.remove(emitter);
                log.debug("SSE 推送失败移除连接: userId={}", userId);
            }
        }
    }

    /** 心跳（每 25s 防代理断连；失败连接清理） */
    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("1"));
                } catch (Exception e) {
                    list.remove(emitter);
                }
            }
        });
    }

    /** 在线连接数（运维观测） */
    public int onlineCount() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }
}
