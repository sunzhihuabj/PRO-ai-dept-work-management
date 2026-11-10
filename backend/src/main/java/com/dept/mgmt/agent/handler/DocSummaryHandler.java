package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.client.AiGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 日报/周报/纪要摘要处理器（CAP_DOC_SUMMARY）
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class DocSummaryHandler implements CapabilityHandler {

    private final AiGatewayClient aiGatewayClient;

    @Override
    public String capabilityCode() {
        return "CAP_DOC_SUMMARY";
    }

    @Override
    public String execute(String instruction, Long userId) {
        String scene = instruction.contains("周报") ? "weekly"
                : instruction.contains("纪要") || instruction.contains("会议") ? "meeting"
                : "daily";
        String prompt = switch (scene) {
            case "weekly" -> "请将以下内容整理为一份周报，按本周完成、问题风险、下周计划三个部分输出：\n\n";
            case "meeting" -> "请将以下发言记录整理为会议纪要，列出关键结论与行动项：\n\n";
            default -> "请将以下工作内容整理为一份简洁的日报，突出完成事项与明日计划：\n\n";
        };
        return aiGatewayClient.chat("CAP_DOC_SUMMARY", prompt + instruction, userId, null);
    }
}
