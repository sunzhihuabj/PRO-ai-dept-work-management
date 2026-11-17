package com.dept.mgmt.agent.collab;

import com.dept.mgmt.agent.collab.mcp.McpProtocolService;
import com.dept.mgmt.agent.collab.mcp.McpTool;
import com.dept.mgmt.agent.collab.mcp.McpToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 协议服务单元测试（JSON-RPC 2.0：initialize/tools list/tools call/错误码）
 *
 * @author dept-mgmt
 */
class McpProtocolServiceTest {

    private McpProtocolService service;
    private McpToolRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = mock(McpToolRegistry.class);
        service = new McpProtocolService(registry, objectMapper);
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
    }

    @Test
    void toolsCallReturnsTextContent() {
        when(registry.get("task.my_todo")).thenReturn(new McpTool("task.my_todo", "待办任务",
                McpTool.objectSchema(Map.of(), List.of("userId")),
                args -> Map.of("count", 3)));
        var result = asMap(service.handle(rpc(3, "tools/call",
                Map.of("name", "task.my_todo", "arguments", Map.of("userId", 7)))));
        assertFalse((Boolean) result.get("isError"));
        List<?> content = (List<?>) result.get("content");
        assertEquals("text", ((Map<?, ?>) content.get(0)).get("type"));
        assertTrue(String.valueOf(((Map<?, ?>) content.get(0)).get("text")).contains("\"count\":3"));
    }

    @Test
    void toolsCallExecutionFailureIsErrorResult() {
        when(registry.get("boom")).thenReturn(new McpTool("boom", "执行失败示例",
                McpTool.objectSchema(Map.of(), null),
                args -> { throw new IllegalArgumentException("参数类型错误"); }));
        var result = asMap(service.handle(rpc(4, "tools/call", Map.of("name", "boom", "arguments", Map.of()))));
        assertTrue((Boolean) result.get("isError"));
        assertTrue(String.valueOf(((List<?>) result.get("content")).get(0)).contains("工具执行失败"));
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
