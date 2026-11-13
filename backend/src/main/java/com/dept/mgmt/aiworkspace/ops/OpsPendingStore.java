package com.dept.mgmt.aiworkspace.ops;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写入类操作确认暂存（T-411）：AI 抽取参数后不直接执行，先回执待用户确认。
 * 会话级暂存，TTL 5 分钟；确认/取消/过期后清除。
 *
 * @author dept-mgmt
 */
@Component
public class OpsPendingStore {

    private static final long TTL_MINUTES = 5;

    /** 会话ID → 待确认操作 */
    private final Map<Long, PendingOp> store = new ConcurrentHashMap<>();

    public record PendingOp(String toolName, Map<String, Object> params, LocalDateTime expireAt) {
    }

    /** 暂存待确认操作（覆盖同会话旧的） */
    public void put(Long conversationId, String toolName, Map<String, Object> params) {
        store.put(conversationId, new PendingOp(toolName, params, LocalDateTime.now().plusMinutes(TTL_MINUTES)));
    }

    /** 取出（不移除；过期自动清除返回 null） */
    public PendingOp peek(Long conversationId) {
        PendingOp op = store.get(conversationId);
        if (op == null) {
            return null;
        }
        if (op.expireAt().isBefore(LocalDateTime.now())) {
            store.remove(conversationId);
            return null;
        }
        return op;
    }

    /** 移除 */
    public void remove(Long conversationId) {
        store.remove(conversationId);
    }
}
