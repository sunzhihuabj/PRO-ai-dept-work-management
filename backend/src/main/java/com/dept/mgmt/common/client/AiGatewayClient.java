package com.dept.mgmt.common.client;

import com.dept.mgmt.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * AI 网关客户端（backend → ai-gateway 8090，服务间 X-API-Key 认证）
 * 红线落地：backend 内任何业务代码不得直连模型 SDK/URL，一律经本客户端调网关
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class AiGatewayClient {

    private final String baseUrl;
    private final String apiKey;
    private final RestClient client;

    public AiGatewayClient(@Value("${ai.gateway.base-url:http://localhost:8090}") String baseUrl,
                           @Value("${ai.gateway.api-key:dept-gateway-internal-key-2026}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 调用网关对话能力
     *
     * @param capabilityCode 能力点编码（AGT-XX/CAP_XXX）
     * @param prompt         提示词
     * @param userId         调用用户ID（审计用）
     * @param jsonSchema     输出契约（可 null）
     * @return 模型输出全文
     */
    @SuppressWarnings("unchecked")
    public String chat(String capabilityCode, String prompt, Long userId, Map<String, Object> jsonSchema) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("capabilityCode", capabilityCode);
        body.put("prompt", prompt);
        body.put("callerType", "USER");
        body.put("callerId", userId == null ? null : String.valueOf(userId));
        if (jsonSchema != null) {
            body.put("jsonSchema", jsonSchema);
        }
        try {
            Map<String, Object> resp = client.post()
                    .uri("/v1/gateway/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("content") == null) {
                throw new BusinessException(503, "AI 服务返回空内容，请稍后重试或转人工处理");
            }
            // 降级响应（网关全链失败转人工）：content 为人工提示文案
            if (Boolean.TRUE.equals(resp.get("degraded"))) {
                log.warn("AI 网关降级: capability={}, requestId={}", capabilityCode, resp.get("requestId"));
                throw new BusinessException(503, "AI 服务暂不可用，请转人工处理");
            }
            return resp.get("content").toString();
        } catch (RestClientResponseException e) {
            // 网关错误码收敛：不透出上游细节
            int status = e.getStatusCode().value();
            log.warn("AI 网关调用失败: capability={}, status={}", capabilityCode, status);
            if (status == 403) {
                throw new BusinessException(403, "该能力未开通或禁止 AI 执行");
            }
            if (status == 429) {
                throw new BusinessException(429, "AI 调用达到限流阈值，请约10分钟后再试");
            }
            throw new BusinessException(503, "AI 服务暂不可用，请稍后重试或转人工处理");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 网关连接失败: {}", baseUrl, e);
            throw new BusinessException(503, "AI 服务连接失败，请稍后重试或转人工处理");
        }
    }
}
