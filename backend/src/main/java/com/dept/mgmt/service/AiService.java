package com.dept.mgmt.service;

import com.dept.mgmt.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 助手服务（对接 Ollama）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class AiService {

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ai.ollama.model:qwen2.5:7b}")
    private String model;

    @Value("${ai.ollama.timeout:60}")
    private long timeoutSeconds;

    /**
     * 生成摘要/总结
     * @param text 原文
     * @param type 场景类型（daily / weekly / meeting / generic）
     */
    public String summarize(String text, String type) {
        String prompt = buildPrompt(text, type);
        return callOllama(prompt);
    }

    /**
     * 通用 AI 对话（供文件差异分析等扩展使用）
     */
    public String chat(String prompt) {
        return callOllama(prompt);
    }

    private String buildPrompt(String text, String type) {
        String sceneDesc = switch (type == null ? "" : type) {
            case "daily" -> "请将以下工作内容整理为一份简洁的日报，突出完成事项与明日计划：";
            case "weekly" -> "请将以下内容整理为一份周报，按本周完成、问题风险、下周计划三个部分输出：";
            case "meeting" -> "请将以下发言记录整理为会议纪要，列出关键结论与行动项：";
            default -> "请对以下内容进行要点提炼与总结：";
        };
        return sceneDesc + "\n\n" + text;
    }

    private String callOllama(String prompt) {
        try {
            RestTemplate restTemplate = new RestTemplate(simpleFactory());
            Map<String, Object> body = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/api/generate", entity, Map.class);
            Map<?, ?> respBody = resp.getBody();
            if (respBody == null || respBody.get("response") == null) {
                throw new BusinessException(500, "AI 服务返回空内容");
            }
            return respBody.get("response").toString();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("调用 Ollama 失败", e);
            throw new BusinessException(502, "AI 服务暂不可用，请稍后重试 (" + e.getMessage() + ")");
        }
    }

    private SimpleClientHttpRequestFactory simpleFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) (timeoutSeconds * 1000));
        factory.setReadTimeout((int) (timeoutSeconds * 1000));
        return factory;
    }
}
