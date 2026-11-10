package com.dept.mgmt.platform.notify.channel;

import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.UserMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 钉钉群机器人 Webhook 通道（T-212，单向通知）
 * 降级链内嵌（V2 §3.2）：webhook 未配置或发送失败 → 自动降级落站内信，不阻塞通知主流程；
 * 运行时可通过 /v1/notify/dingtalk/config 调整 webhook 地址（演示与运维友好），yml 配置为默认值
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class DingTalkNotificationChannel implements NotificationChannel {

    private final UserMessageMapper userMessageMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** 钉钉群机器人 webhook 地址（空 = 未接入，直接降级站内信） */
    @Value("${dingtalk.webhook.url:}")
    private String webhookUrl;

    public DingTalkNotificationChannel(UserMessageMapper userMessageMapper, ObjectMapper objectMapper) {
        this.userMessageMapper = userMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelCode() {
        return "DINGTALK";
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    /** 运行时调整 webhook 地址（空串 = 清除，回到站内信降级态） */
    public void setWebhookUrl(String url) {
        this.webhookUrl = url == null ? "" : url.trim();
        log.info("钉钉 webhook 地址已更新: {}", this.webhookUrl.isBlank() ? "(未配置，降级站内信)" : this.webhookUrl);
    }

    @Override
    public void send(List<UserMessage> messages) {
        for (UserMessage message : messages) {
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.info("钉钉 webhook 未配置，降级站内信: title={}", message.getTitle());
                userMessageMapper.insert(message);
                continue;
            }
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "msgtype", "markdown",
                        "markdown", Map.of(
                                "title", message.getTitle(),
                                "text", "# " + message.getTitle() + "\n\n" + message.getContent())));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json;charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("钉钉通知已发送: title={} status={}", message.getTitle(), resp.statusCode());
                } else {
                    // 钉钉不可用 → 降级站内信
                    log.warn("钉钉通知响应异常 {}，降级站内信: title={}", resp.statusCode(), message.getTitle());
                    userMessageMapper.insert(message);
                }
            } catch (Exception e) {
                // 网络失败 → 降级站内信（通知主流程不阻塞）
                log.warn("钉钉通知发送失败，降级站内信: title={} err={}", message.getTitle(), e.getMessage());
                userMessageMapper.insert(message);
            }
        }
    }
}
