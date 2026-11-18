package com.dept.mgmt.agent.collab;

import com.dept.mgmt.agent.collab.mcp.HermesToolAuditService;
import com.dept.mgmt.agent.collab.mcp.McpIdentityResolver;
import com.dept.mgmt.agent.collab.mcp.McpProtocolService;
import com.dept.mgmt.agent.collab.mcp.McpTool;
import com.dept.mgmt.agent.collab.mcp.McpToolRegistry;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP 协议服务单元测试（JSON-RPC 2.0：initialize/tools list/tools call/错误码
 * + V2.0 铁律二 fail-closed 身份 + 铁律五调用审计）
 *
 * @author dept-mgmt
 */
class McpProtocolServiceTest {

    private McpProtocolService service;
    private McpToolRegistry registry;
    private HermesToolAuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    @BeforeEach
    void setUp() {
        registry = mock(McpToolRegistry.class);
        auditService = mock(HermesToolAuditService.class);
        // 有效用户桩（任一 userId 均返回有效用户）
        Mockito.lenient().when(userMapper.selectById(any())).thenAnswer(inv -> {
            com.dept.mgmt.entity.SysUser u = new com.dept.mgmt.entity.SysUser();
            u.setId(((Number) inv.getArgument(0)).longValue());
            u.setUsername("user" + inv.getArgument(0));
            u.setRoleCodes("ROLE_WORKER");
            u.setStatus(1);
            return u;
        });
        McpIdentityResolver identityResolver = new McpIdentityResolver(userMapper);
        service = new McpProtocolService(registry, identityResolver, auditService, objectMapper);
        // 清理线程上下文（防串号）
        com.dept.mgmt.agent.collab.mcp.McpCallContext.clear();
    }

    private Map<String, Object> rpc(Object id, String method, Map<String, Object> params) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.put("params", params);
        }
        return req;
    }

    private Map<String, Object> argsOf(Object userId) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(McpIdentityResolver.IDENTITY_PARAM, userId);
        return args;
    }

    @Test
    void initializeEchoesKnownProtocolVersion() {
        var result = asMap(service.handle(rpc(1, "initialize",
                Map.of("protocolVersion", "2025-06-18"))));
        assertEquals("2025-06-18", result.get("protocolVersion"));
        assertEquals("dept-mgmt-mcp", ((Map<?, ?>) result.get("serverInfo")).get("name"));
        assertTrue(result.containsKey("capabilities"));
    }

    @Test
    void initializeUnknownVersionFallsBack() {
        var result = asMap(service.handle(rpc(1, "initialize", Map.of("protocolVersion", "1999-01-01"))));
        assertEquals("2025-06-18", result.get("protocolVersion"));
    }

    @Test
    void toolsListDescribesRegisteredTools() {
        when(registry.all()).thenReturn(List.of(new McpTool("task.query", "查询任务",
                Map.of("type", "object"), args -> "ok")));
        var result = asMap(service.handle(rpc(2, "tools/list", null)));
        List<?> tools = (List<?>) result.get("tools");
        assertEquals(1, tools.size());
        Map<?, ?> tool = (Map<?, ?>) tools.get(0);
        assertEquals("task.query", tool.get("name"));
        assertEquals("查询任务", tool.get("description"));
        assertNotNull(tool.get("inputSchema"));
        // B4 写操作声明：只读工具 requiresApproval=false + annotations.readOnlyHint=true
        assertEquals(false, tool.get("requiresApproval"));
        assertEquals(true, ((Map<?, ?>) tool.get("annotations")).get("readOnlyHint"));
    }

    @Test
    void toolsListDeclaresApprovalForWriteTools() {
        // B4：写工具 requiresApproval=true + readOnlyHint=false（调用即生成审批建议）
        when(registry.all()).thenReturn(List.of(new McpTool("task.create", "创建任务",
                Map.of("type", "object"), true, args -> "ok")));
        var result = asMap(service.handle(rpc(9, "tools/list", null)));
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) result.get("tools")).get(0);
        assertEquals(true, tool.get("requiresApproval"));
        assertEquals(false, ((Map<?, ?>) tool.get("annotations")).get("readOnlyHint"));
    }

    @Test
    void toolsCallReturnsTextContent() {
        when(registry.get("task.my_todo")).thenReturn(new McpTool("task.my_todo", "待办任务",
                McpTool.objectSchema(Map.of(), List.of(McpIdentityResolver.IDENTITY_PARAM)),
                args -> Map.of("count", 3)));
        var result = asMap(service.handle(rpc(3, "tools/call",
                Map.of("name", "task.my_todo", "arguments", argsOf(7)))));
        assertFalse((Boolean) result.get("isError"));
        List<?> content = (List<?>) result.get("content");
        assertEquals("text", ((Map<?, ?>) content.get(0)).get("type"));
        assertTrue(String.valueOf(((Map<?, ?>) content.get(0)).get("text")).contains("\"count\":3"));
        // 铁律五：成功调用产生 SUCCESS 审计
        verify(auditService).record(eq(null), eq("task.my_todo"), eq("readonly"),
                eq(7L), eq("ARGS"), eq("SUCCESS"), eq(null), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void toolsCallWithoutIdentityFailsClosed() {
        // 铁律二：无 actingUserId 参数且无头透传 → 协议错误拒绝执行（不回落系统身份）
        when(registry.get("task.my_todo")).thenReturn(new McpTool("task.my_todo", "待办任务",
                McpTool.objectSchema(Map.of(), null), args -> Map.of("count", 3)));
        var e = assertThrows(McpProtocolService.McpRpcException.class,
                () -> service.handle(rpc(30, "tools/call",
                        Map.of("name", "task.my_todo", "arguments", Map.of()))));
        assertEquals(-32001, e.code());
        assertTrue(e.getMessage().contains("缺少调用身份"));
        // 审计记录 INVALID
        verify(auditService).record(eq(null), eq("task.my_todo"), eq("readonly"),
                eq(null), eq(null), eq("INVALID"), any(), any(), eq(0L));
    }

    @Test
    void toolsCallPermissionDeniedRecordsAudit() {
        // 铁律五：BusinessException（403 等）→ DENIED 审计 + isError 结果
        when(registry.get("guarded")).thenReturn(new McpTool("guarded", "受保护工具",
                McpTool.objectSchema(Map.of(), null),
                args -> { throw new BusinessException(403, "无权执行"); }));
        var result = asMap(service.handle(rpc(31, "tools/call",
                Map.of("name", "guarded", "arguments", argsOf(7)))));
        assertTrue((Boolean) result.get("isError"));
        assertTrue(String.valueOf(((List<?>) result.get("content")).get(0)).contains("无权执行"));
        verify(auditService).record(eq(null), eq("guarded"), eq("readonly"),
                eq(7L), eq("ARGS"), eq("DENIED"), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void toolsCallExecutionFailureIsErrorResult() {
        when(registry.get("boom")).thenReturn(new McpTool("boom", "执行失败示例",
                McpTool.objectSchema(Map.of(), null),
                args -> { throw new IllegalArgumentException("参数类型错误"); }));
        var result = asMap(service.handle(rpc(4, "tools/call",
                Map.of("name", "boom", "arguments", argsOf(7)))));
        assertTrue((Boolean) result.get("isError"));
        assertTrue(String.valueOf(((List<?>) result.get("content")).get(0)).contains("工具执行失败"));
        verify(auditService).record(eq(null), eq("boom"), eq("readonly"),
                eq(7L), eq("ARGS"), eq("FAILED"), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void unknownToolThrowsProtocolError() {
        when(registry.get("nope")).thenReturn(null);
        var e = assertThrows(McpProtocolService.McpRpcException.class,
                () -> service.handle(rpc(5, "tools/call", Map.of("name", "nope"))));
        assertEquals(-32602, e.code());
    }

    @Test
    void unknownMethodThrowsProtocolError() {
        var e = assertThrows(McpProtocolService.McpRpcException.class,
                () -> service.handle(rpc(6, "resources/list", null)));
        assertEquals(-32601, e.code());
    }

    @Test
    void notificationReturnsNull() {
        assertNull(service.handle(rpc(null, "notifications/initialized", null)));
    }

    private Map<String, Object> asMap(Object v) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }
}
