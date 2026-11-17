package com.dept.mgmt.agent.collab.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Streamable HTTP 端点（阶段一 WP1-3：供 Dify 编排底座与 Hermes 主 Agent 接入）
 *
 * <p>路径：POST /api/mcp（context-path=/api）。
 *
 * <p>鉴权：API Key（X-API-Key 头或 Authorization: Bearer），密钥经
 * {@code app.mcp.api-key} 配置（生产环境变量注入）；常数时间比较防时序侧信道。
 * 该端点在 SecurityConfig 白名单中放行，由本控制器自鉴权（外部 Agent 无法走 JWT 登录态）。
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@Tag(name = "MCP Server", description = "MCP Streamable HTTP：业务工具暴露（Dify/Hermes 接入点）")
public class McpServerController {

    private final McpProtocolService protocol;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public McpServerController(McpProtocolService protocol, ObjectMapper objectMapper,
                               @Value("${app.mcp.api-key:dept-mcp-2026r2-dev-key}") String apiKey) {
        this.protocol = protocol;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "MCP JSON-RPC 入口（initialize/tools/list/tools/call/ping）")
    public ResponseEntity<String> post(@RequestBody String body,
                                       @RequestHeader(value = "X-API-Key", required = false) String xApiKey,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!hasValidKey(xApiKey, authorization)) {
            return jsonRpcErrorStatus(HttpStatus.UNAUTHORIZED, null, -32000,
                    "Unauthorized: missing/invalid X-API-Key or Bearer");
        }
        Map<String, Object> request;
        try {
            request = objectMapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return jsonRpcError(null, -32700, "JSON 解析失败: " + e.getMessage());
        }
        Object id = request.get("id");
        try {
            Object result = protocol.handle(request);
            if (result == null) {
                // 通知（notifications/initialized 等）：202 无正文
                return ResponseEntity.accepted().build();
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jsonrpc", "2.0");
            resp.put("id", id);
            resp.put("result", result);
            String json = objectMapper.writeValueAsString(resp);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        } catch (McpProtocolService.McpRpcException e) {
            return jsonRpcError(id, e.code(), e.getMessage());
        } catch (Exception e) {
            log.error("MCP 请求处理异常", e);
            return jsonRpcError(id, -32603, "内部错误");
        }
    }

    @GetMapping("/mcp")
    @Operation(summary = "SSE 流（阶段一未提供，规范允许 405）")
    public ResponseEntity<Void> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @DeleteMapping("/mcp")
    @Operation(summary = "会话终止（无会话实现，规范允许 405）")
    public ResponseEntity<Void> delete() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    /** API Key 常数时间校验 */
    private boolean hasValidKey(String xApiKey, String authorization) {
        String provided = null;
        if (xApiKey != null && !xApiKey.isBlank()) {
            provided = xApiKey.trim();
        } else if (authorization != null && authorization.startsWith("Bearer ")) {
            provided = authorization.substring(7).trim();
        }
        return provided != null && !apiKey.isEmpty()
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                        apiKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 指定 HTTP 状态码的 JSON-RPC error 响应（鉴权失败等协议外错误） */
    private ResponseEntity<String> jsonRpcErrorStatus(HttpStatus status, Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", error);
        String json;
        try {
            json = objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            json = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"internal error\"}}";
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json);
    }

    private ResponseEntity<String> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", error);
        String json;
        try {
            json = objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            json = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"内部错误\"}}";
        }
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(json);
    }
}
