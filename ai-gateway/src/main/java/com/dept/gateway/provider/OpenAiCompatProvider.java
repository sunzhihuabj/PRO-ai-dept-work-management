package com.dept.gateway.provider;

import com.dept.gateway.entity.AiProviderConfig;
import com.dept.gateway.exception.NonRetryableProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 Provider（全配置化）
 * 一套代码适配 DeepSeek / 智谱 GLM / 通义 DashScope / Kimi / LM Studio / 任意 OpenAI 兼容端点：
 * POST {endpoint}/chat/completions  body: {model, messages, stream:false, max_tokens?}
 *
 * <p>T-410 修复：连接/读取超时（5s/60s）、HTTP 错误分类转译（认证/参数/限流/上游）、
 * 响应体摘要随异常上抛（根因落审计）、thinking 模型 content 空时 reasoning_content 兜底。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class OpenAiCompatProvider implements AiProvider {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 180_000; // F5: glm-5.3-flash Ops 判定实测 55s+，60s 边缘超时会 failover 落 MOCK（与 backend AiGatewayClient 180s 对齐）

    @Override
    public String type() {
        return "OPENAI_COMPAT";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderResult chat(AiProviderConfig config, String prompt, int maxTokens) throws Exception {
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new IllegalArgumentException("通道 endpoint 未配置");
        }
        String apiKey = config.resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("通道 API Key 未配置");
        }
        RestClient client = buildClient(config.getEndpoint(), apiKey);

        Map<String, Object> body = maxTokens > 0
                ? Map.of("model", config.getModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", false, "max_tokens", maxTokens)
                : Map.of("model", config.getModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", false);

        Map<String, Object> resp;
        try {
            resp = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw translate(e, config.getEndpoint());
        }
        if (resp == null) {
            throw new IllegalStateException("上游返回空响应");
        }
        // OpenAI 兼容结构：choices[0].message.content；usage.total_tokens
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("上游响应缺少 choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = message == null ? null : (String) message.get("content");
        if (!StringUtils.hasText(content) && message != null
                && StringUtils.hasText((String) message.get("reasoning_content"))) {
            // thinking 模型（如 gemma-4）：max_tokens 过小时思考链耗尽配额导致 content 为空
            content = (String) message.get("reasoning_content");
            log.warn("通道 {} 上游 content 为空，reasoning_content 兜底", config.getProviderCode());
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("上游返回空内容");
        }
        int tokens = 0;
        Map<String, Object> usage = (Map<String, Object>) resp.get("usage");
        if (usage != null && usage.get("total_tokens") instanceof Number n) {
            tokens = n.intValue();
        }
        return new ProviderResult(content, tokens);
    }

    /**
     * OpenAI 兼容向量化：POST {endpoint}/embeddings  body: {model, input:[...]}
     * 响应：data[i].embedding（与 input 顺序一致）
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<List<Double>> embed(AiProviderConfig config, List<String> texts) throws Exception {
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new IllegalArgumentException("通道 endpoint 未配置");
        }
        String apiKey = config.resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("通道 API Key 未配置");
        }
        RestClient client = buildClient(config.getEndpoint(), apiKey);
        Map<String, Object> resp;
        try {
            resp = client.post()
                    .uri("/embeddings")
                    .body(Map.of("model", config.getModel(), "input", texts))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw translate(e, config.getEndpoint());
        }
        if (resp == null || resp.get("data") == null) {
            throw new IllegalStateException("向量化返回空响应");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        if (data == null || data.size() != texts.size()) {
            throw new IllegalStateException("向量化返回数量与输入不一致");
        }
        // data[i].embedding，按 index 排序保证顺序一致
        return data.stream()
                .sorted(java.util.Comparator.comparingInt(d -> ((Number) d.get("index")).intValue()))
                .map(d -> (List<Double>) d.get("embedding"))
                .toList();
    }

    private RestClient buildClient(String endpoint, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));
        return RestClient.builder()
                .baseUrl(endpoint)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 错误分类转译：把底层 HTTP/网络异常翻译为带根因摘要的业务异常。
     * 401/403 认证失败、400 参数非法、404 端点不存在、429 限流、连接失败 → 不可重试；
     * 5xx 上游异常 → 可重试（保留普通异常语义）。
     */
    private RuntimeException translate(Exception e, String endpoint) {
        if (e instanceof HttpStatusCodeException http) {
            int sc = http.getStatusCode().value();
            String brief = http.getResponseBodyAsString();
            if (brief != null) {
                brief = brief.replaceAll("\\s+", " ").trim();
                if (brief.length() > 200) {
                    brief = brief.substring(0, 200) + "…";
                }
            }
            if (sc == 401 || sc == 403) {
                return new NonRetryableProviderException("认证失败(HTTP " + sc + "): " + brief, e);
            }
            if (sc == 400) {
                return new NonRetryableProviderException("请求参数被拒绝(HTTP 400): " + brief, e);
            }
            if (sc == 404) {
                return new NonRetryableProviderException("端点不存在(HTTP 404): " + endpoint, e);
            }
            if (sc == 429) {
                return new NonRetryableProviderException("上游限流(HTTP 429): " + brief, e);
            }
            return new IllegalStateException("上游服务异常(HTTP " + sc + "): " + brief, e);
        }
        if (e instanceof ResourceAccessException rae) {
            // 连接拒绝/超时：立即切下一通道，重试只会翻倍等待
            return new NonRetryableProviderException(
                    "无法连接上游(" + endpoint + "): " + rootMessage(rae), e);
        }
        return e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
