package com.dept.mgmt.agent.collab.mcp;

/**
 * MCP 调用身份上下文（A4 身份透传：请求级 ThreadLocal）
 *
 * <p>McpServerController 解析 {@code X-Acting-User-Id} 头后写入，工具执行层读取；
 * 请求结束必须 clear（控制器 finally），防止线程池串号。缺省（null）= 无透传，
 * 执行层回落系统委托身份（app.mcp.acting-user-id）。
 *
 * @author dept-mgmt
 */
public final class McpCallContext {

    private static final ThreadLocal<Long> ACTING_USER = new ThreadLocal<>();

    private McpCallContext() {
    }

    public static void setActingUserId(Long userId) {
        ACTING_USER.set(userId);
    }

    /** 无透传时返回 null */
    public static Long actingUserId() {
        return ACTING_USER.get();
    }

    public static void clear() {
        ACTING_USER.remove();
    }
}
