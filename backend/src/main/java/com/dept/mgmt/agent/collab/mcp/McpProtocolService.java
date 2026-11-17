package com.dept.mgmt.agent.collab.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 协议处理服务（JSON-RPC 2.0 over Streamable HTTP，阶段一最小实现）
 *
 * <p>支持方法：initialize / notifications/* / tools/list / tools/call / ping。
 * 单请求-单响应（application/json），不提供 SSE 流（GET 返回 405 符合规范——
 * 服务端不声明 stream 能力时客户端应使用无流模式）。
 *
 * <p>选型披露：阶段一为零依赖最小实现（无 Spring AI SDK），协议面覆盖 Dify/Hermes
 * MCP Client 的核心调用链（初始化→发现→调用）；升级 Spring AI MCP Server 为阶段二可选项。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class McpProtocolService {

    /** 声明支持的协议版本（按客户端请求回显兼容版本） */
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    private final McpToolRegistry registry;
    private final ObjectMapper objectMapper;

    public McpProtocolService(McpToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一条 JSON-RPC 消息
     *
     * @param rpcMethod 处理结果：REQUEST → 返回 result（挂到 id）；NOTIFICATION → 返回 null（HTTP 202）
     * @throws McpRpcException 协议级错误（-32601 方法不存在 / -32602 参数错误 / -32600 非法请求）
     */
    public Object handle(Map<String, Object> request) throws McpRpcException {
        Object id = request.get("id");
        String method = String.valueOf(request.getOrDefault("method", ""));
        Map<String, Object> params = asMap(request.get("params"));

        // 通知：无 id，不回包
        if (request.containsKey("id") == false || id == null) {
            log.debug("MCP 通知: {}", method);
            return null;
        }

        return switch (method) {
            case "initialize" -> initialize(params);
            case "ping" -> Map.of();
            case "tools/list" -> Map.of("tools", registry.all().stream()
                    .map(t -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", t.name());
                        m.put("description", t.description());
                        m.put("inputSchema", t.inputSchema());
                        // B4 写操作声明：requiresApproval + MCP annotations 只读提示
                        m.put("requiresApproval", t.requiresApproval());
                        m.put("annotations", Map.of("readOnlyHint", !t.requiresApproval()));
                        return m;
                    }).toList());
            case "tools/call" -> toolsCall(params);
            default -> throw new McpRpcException(-32601, "方法不存在: " + method);
        };
    }

    private Map<String, Object> initialize(Map<String, Object> params) {
        String requested = params == null ? null : String.valueOf(params.get("protocolVersion"));
        // 客户端请求版本直接回显（已知版本）；未知版本回退默认
        String protocolVersion = isKnownVersion(requested) ? requested : DEFAULT_PROTOCOL_VERSION;
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "dept-mgmt-mcp");
        serverInfo.put("version", "1.0.0");
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private boolean isKnownVersion(String v) {
        return v != null && !v.isBlank() && !v.equals("null")
                && List.of("2024-11-05", "2025-03-26", "2025-06-18", "2026-07-28").contains(v);
    }

    private Map<String, Object> toolsCall(Map<String, Object> params) throws McpRpcException {
        String name = params == null ? null : String.valueOf(params.get("name"));
        if (name == null || name.isBlank() || name.equals("null")) {
            throw new McpRpcException(-32602, "缺少工具名 name");
        }
        McpTool tool = registry.get(name);
        if (tool == null) {
            throw new McpRpcException(-32602, "工具不存在: " + name);
        }
        Map<String, Object> arguments = asMap(params.get("arguments"));
        try {
            Object out = tool.handler().apply(arguments);
            String text = out == null ? "null" : (out instanceof String s ? s
                    : objectMapper.writeValueAsString(out));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of("type", "text", "text", text)));
            result.put("isError", false);
            return result;
        } catch (McpRpcException e) {
            throw e;
        } catch (Exception e) {
            // 工具执行错误按 MCP 规范返回 isError 结果（而非协议级 error），便于客户端提示重试
            log.warn("MCP 工具执行失败: tool={}", name, e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of("type", "text",
                    "text", "工具执行失败: " + e.getMessage())));
            result.put("isError", true);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        if (v == null) {
            return Map.of();
        }
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return objectMapper.convertValue(v, new TypeReference<Map<String, Object>>() {
        });
    }

    /** 协议级错误（JSON-RPC error 对象） */
    public static class McpRpcException extends RuntimeException {
        private final int code;

        public McpRpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
