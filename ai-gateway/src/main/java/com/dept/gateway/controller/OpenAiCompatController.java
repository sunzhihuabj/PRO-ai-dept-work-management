package com.dept.gateway.controller;

import com.dept.gateway.core.GatewayService;
import com.dept.gateway.dto.ChatRequest;
import com.dept.gateway.dto.ChatResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI 兼容门面（阶段一 WP1-1/WP1-2：Hermes / Dify 等外部 Agent 的统一模型入口）
 *
 * <p>协议：POST /v1/chat/completions（OpenAI Chat Completions 形状）。
 * 鉴权：X-API-Key 或 Authorization: Bearer（与网关共享密钥，ApiKeyFilter 已放行）。
 *
 * <p>设计：messages 合并为单 prompt 走网关治理流水线（能力点校验 → 35 次/分限流 →
 * 日配额 → 脱敏 → Provider 链 → 审计）。stream=true 时以 SSE 单分片仿真流式
 * （内容一次性下发 + [DONE]，兼容绝大多数 OpenAI 客户端；真流式为阶段二）。
 * model 参数仅透传记录，实际通道由网关按启用链优先级选择。
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAiCompatController {

    /** 门面专用能力点（sys_capability 种子，见 database/10_gateway_facade.sql） */
    private static final String FACADE_CAPABILITY = "OPENAI_COMPAT";

    private final GatewayService gatewayService;

    @Data
    public static class OpenAiChatRequest {
        private String model;
        private List<Message> messages;
        private Integer max_tokens;
        private Boolean stream;
        private String user;

        @Data
        public static class Message {
            private String role;
            private Object content;
        }
    }

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object chatCompletions(@RequestBody OpenAiChatRequest body) {
        String prompt = flatten(body.getMessages());
        if (!StringUtils.hasText(prompt)) {
            return Map.of(
                    "error", Map.of("message", "messages 不能为空", "type", "invalid_request_error"));
        }
        ChatRequest request = new ChatRequest();
        request.setCapabilityCode(FACADE_CAPABILITY);
        request.setPrompt(prompt);
        request.setMaxTokens(body.getMax_tokens() == null ? 0 : body.getMax_tokens());
        request.setCallerType("AGENT");
        request.setCallerId(StringUtils.hasText(body.getUser()) ? body.getUser() : "openai-compat");
        ChatResponse resp = gatewayService.chat(request);

        String model = StringUtils.hasText(body.getModel()) ? body.getModel() : "gateway-auto";
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        if (Boolean.TRUE.equals(body.getStream())) {
            // SSE 仿真流式（真流式为阶段二）；SseEmitter 必须直接返回，不可包 ResponseEntity
            // F9(2026-11-18)：OpenAI 流式契约要求最后一个 choice 携带非 null finish_reason，
            // 否则严格客户端（Hermes Agent）判定"流被截断"，触发 4 轮 continuation 重试后 run.failed
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            SseEmitter emitter = new SseEmitter(120_000L);
            try {
                emitter.send(SseEmitter.event().data(mapper.writeValueAsString(chunkFrame(id, model, resp.getContent()))));
                emitter.send(SseEmitter.event().data(mapper.writeValueAsString(stopFrame(id, model))));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("object", "chat.completion");
        out.put("created", System.currentTimeMillis() / 1000);
        out.put("model", model);
        Map<String, Object> message = Map.of("role", "assistant", "content", resp.getContent());
        out.put("choices", List.of(Map.of(
                "index", 0,
                "message", message,
                "finish_reason", "stop")));
        Map<String, Object> usage = Map.of(
                "prompt_tokens", prompt.length(),
                "completion_tokens", resp.getUsage() == null ? 0
                        : (resp.getUsage().getOrDefault("totalTokens", 0) instanceof Integer i ? i : 0),
                "total_tokens", resp.getUsage() == null ? 0
                        : (resp.getUsage().getOrDefault("totalTokens", 0) instanceof Integer i ? i : 0));
        out.put("usage", usage);
        if (Boolean.TRUE.equals(resp.getDegraded())) {
            out.put("gateway_degraded", true);
        }
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> chunkFrame(String id, String model, String content) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("role", "assistant");
        delta.put("content", content);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", null);
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);
        chunk.put("model", model);
        chunk.put("choices", List.of(choice));
        return chunk;
    }

    /** 流式终止块：空 delta + finish_reason=stop（F9 修复，见 chatCompletions 注释） */
    private Map<String, Object> stopFrame(String id, String model) {
        Map<String, Object> delta = new LinkedHashMap<>();
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", "stop");
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);
        chunk.put("model", model);
        chunk.put("choices", List.of(choice));
        return chunk;
    }

    /** messages → 单 prompt（role 前缀保留对话结构；content 支持字符串与分段数组两种形状） */
    private String flatten(List<OpenAiChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OpenAiChatRequest.Message m : messages) {
            String role = StringUtils.hasText(m.getRole()) ? m.getRole() : "user";
            String content = m.getContent() instanceof String s ? s : flattenContent(m.getContent());
            if (content == null || content.isBlank()) {
                continue;
            }
            sb.append("[").append(role).append("] ").append(content).append("\n");
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String flattenContent(Object content) {
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof Map<?, ?> part && part.get("text") != null) {
                    sb.append(part.get("text"));
                }
            }
            return sb.toString();
        }
        return content == null ? null : String.valueOf(content);
    }
}
