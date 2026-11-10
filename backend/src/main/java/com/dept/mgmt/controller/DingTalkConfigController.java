package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.platform.notify.channel.DingTalkNotificationChannel;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知通道配置接口（T-212 钉钉 Webhook）
 * webhook 地址运行时可调（yml 为默认值）；提供 DINGTALK 通道连通性测试
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/notify/dingtalk")
@RequiredArgsConstructor
@Tag(name = "钉钉通知", description = "钉钉 Webhook 配置与测试（T-212）")
public class DingTalkConfigController {

    private final DingTalkNotificationChannel dingTalkChannel;
    private final NotificationService notificationService;

    @GetMapping("/config")
    @Operation(summary = "查询钉钉 webhook 配置")
    public R<Map<String, Object>> config() {
        String url = dingTalkChannel.getWebhookUrl();
        return R.ok(Map.of(
                "webhookUrl", url == null ? "" : url,
                "configured", url != null && !url.isBlank()));
    }

    @PostMapping("/config")
    @Operation(summary = "设置钉钉 webhook 地址（空串清除，回到站内信降级态）")
    public R<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        dingTalkChannel.setWebhookUrl((String) body.get("webhookUrl"));
        String url = dingTalkChannel.getWebhookUrl();
        return R.ok("已更新", Map.of("webhookUrl", url, "configured", !url.isBlank()));
    }

    @PostMapping("/test")
    @Operation(summary = "钉钉通道连通性测试（未配置/失败自动降级站内信）")
    public R<Map<String, Object>> test(@RequestBody(required = false) Map<String, Object> body) {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of(
                "title", "钉钉通道测试",
                "content", "这是一条钉钉通道连通性测试消息；若钉钉不可用已自动降级站内信"));
        dto.setChannel("DINGTALK");
        dto.setReceiverIds(List.of(1L));
        NotificationSendResult result = notificationService.send(dto);
        return R.ok("测试发送完成", Map.of(
                "sent", result.getSent(),
                "skipped", result.getSkipped(),
                "channel", result.getChannel()));
    }
}
