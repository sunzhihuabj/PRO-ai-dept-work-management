package com.dept.gateway.provider;

import com.dept.gateway.entity.AiProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Ollama 本地模型 Provider（预留实现，本机未安装不启用）
 * POST {base-url}/api/generate  body: {model, prompt, stream:false}
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class OllamaProvider implements AiProvider {

    @Override
    public String type() {
        return "OLLAMA";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderResult chat(AiProviderConfig config, String prompt, int maxTokens) throws Exception {
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new IllegalArgumentException("Ollama endpoint 未配置");
        }
        RestClient client = RestClient.builder().baseUrl(config.getEndpoint()).build();
        Map<String, Object> resp = client.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", config.getModel(), "prompt", prompt, "stream", false))
                .retrieve()
                .body(Map.class);
        if (resp == null || resp.get("response") == null) {
            throw new IllegalStateException("Ollama 返回空响应");
        }
        return new ProviderResult(resp.get("response").toString(), 0);
    }
}
