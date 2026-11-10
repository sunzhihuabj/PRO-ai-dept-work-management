package com.dept.gateway.provider;

import com.dept.gateway.entity.AiProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 Provider（全配置化）
 * 一套代码适配 DeepSeek / 智谱 GLM / 通义 DashScope / Kimi / 任意 OpenAI 兼容端点：
 * POST {endpoint}/chat/completions  body: {model, messages, stream:false, max_tokens?}
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class OpenAiCompatProvider implements AiProvider {

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
        RestClient client = RestClient.builder()
                .baseUrl(config.getEndpoint())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = maxTokens > 0
                ? Map.of("model", config.getModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", false, "max_tokens", maxTokens)
                : Map.of("model", config.getModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", false);

        Map<String, Object> resp = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
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
        RestClient client = RestClient.builder()
                .baseUrl(config.getEndpoint())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        Map<String, Object> resp = client.post()
                .uri("/embeddings")
                .body(Map.of("model", config.getModel(), "input", texts))
                .retrieve()
                .body(Map.class);
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
}
