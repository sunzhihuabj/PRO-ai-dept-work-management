package com.dept.mgmt.service;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 助手服务（已改造为网关下游，T-105）
 * 红线：不得直连模型 SDK/URL，统一经 AiGatewayClient 调网关（唯一出口）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    /** 输入限长（防超长文本拖垮模型与计费） */
    private static final int MAX_INPUT_CHARS = 30000;

    /** 摘要场景能力点（网关能力点注册表） */
    private static final String CAP_DOC_SUMMARY = "CAP_DOC_SUMMARY";

    private final AiGatewayClient aiGatewayClient;

    /**
     * 生成摘要/总结
     * @param text 原文
     * @param type 场景类型（daily / weekly / meeting / generic）
     */
    public String summarize(String text, String type) {
        String prompt = buildPrompt(text, type);
        LoginUser user = LoginUser.current();
        return aiGatewayClient.chat(CAP_DOC_SUMMARY, prompt, user == null ? null : user.getUserId(), null);
    }

    /**
     * 通用 AI 对话（供文件差异分析等扩展使用）
     */
    public String chat(String prompt) {
        LoginUser user = LoginUser.current();
        return aiGatewayClient.chat("AGT-09", prompt, user == null ? null : user.getUserId(), null);
    }

    private String buildPrompt(String text, String type) {
        if (text != null && text.length() > MAX_INPUT_CHARS) {
            throw new BusinessException(400, "输入文本超过 " + MAX_INPUT_CHARS + " 字符上限，请精简后重试");
        }
        String sceneDesc = switch (type == null ? "" : type) {
            case "daily" -> "请将以下工作内容整理为一份简洁的日报，突出完成事项与明日计划：";
            case "weekly" -> "请将以下内容整理为一份周报，按本周完成、问题风险、下周计划三个部分输出：";
            case "meeting" -> "请将以下发言记录整理为会议纪要，列出关键结论与行动项：";
            default -> "请对以下内容进行要点提炼与总结：";
        };
        return sceneDesc + "\n\n" + text;
    }
}
