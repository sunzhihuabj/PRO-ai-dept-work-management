package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.dept.mgmt.tender.service.TenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标讯采集解析处理器（AGT-01，T-301，Auto）
 * V2 B-02：每日巡检平台抓取标讯 → 结构化推送；本机无抓取环境按规划砍序规则采用
 * 人工导入路径（标讯原文 → 网关 LLM 结构化解析 → 建档 → 推送商务确认报名），
 * DMZ 抓取器就绪后仅需将原文来源切换为抓取队列，解析链路不变
 * 原文来源：任务 params.rawText 或指令 text= 令牌
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderImportHandler implements CapabilityHandler {

    private final AiGatewayClient aiGatewayClient;
    private final TenderService tenderService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-01";
    }

    /** 解析 + 建档 + 推送建议，报名由人工决策，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        return execute(instruction, userId, Map.of());
    }

    @Override
    public String execute(String instruction, Long userId, Map<String, Object> params) throws Exception {
        String rawText = params.get("rawText") == null ? null : String.valueOf(params.get("rawText"));
        if ((rawText == null || rawText.isBlank()) && instruction != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("text=([\\s\\S]+)").matcher(instruction);
            if (m.find()) {
                rawText = m.group(1).trim();
            }
        }
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(400, "未提供标讯原文（params.rawText 或指令 text=），无法解析");
        }

        Map<String, Object> schema = Map.of(
                "required", List.of("title", "platform", "budget", "qualification_req", "deadline"),
                "title", "string", "platform", "string", "budget", "number",
                "deposit", "number", "qualification_req", "string", "deadline", "string");
        String prompt = "你是招标信息解析助手。请从以下标讯原文中提取结构化信息，只输出 JSON，不要任何解释文字。"
                + "字段定义：title（项目名称）、platform（来源平台/业主单位）、budget（预算金额，数字，单位元，"
                + "找不到为 null）、deposit（投标保证金，数字）、qualification_req（资质要求原文，多项用中文分号；分隔，"
                + "保留等级词如 一级/二级/三级/特级）、deadline（投标截止日期 yyyy-MM-dd，找不到为 null）。"
                + "未提及的字段填 null，禁止编造。\n\n标讯原文：\n" + rawText;
        String content = stripCodeFence(aiGatewayClient.chat(capabilityCode(), prompt, userId, schema));

        Map<?, ?> extracted;
        try {
            extracted = objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "标讯解析结果异常，请重试或转人工: " + e.getMessage());
        }
        String title = str(extracted.get("title"));
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "解析结果缺项目名称，请检查标讯原文");
        }
        Long tenderId = tenderService.createTender(
                title,
                str(extracted.get("platform")),
                parseMoney(extracted.get("budget")),
                parseMoney(extracted.get("deposit")),
                str(extracted.get("qualification_req")),
                parseDate(extracted.get("deadline")),
                LocalDate.now(),
                "AGENT",
                rawText);

        // 结构化标讯推送商务（站内信，人工确认是否报名）
        try {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTemplateCode("GENERIC_REMIND");
            dto.setParams(Map.of(
                    "title", "新标讯推送",
                    "content", "标讯「" + title + "」已解析建档（预算 "
                            + (extracted.get("budget") == null ? "未标注" : extracted.get("budget"))
                            + "，截止 " + (extracted.get("deadline") == null ? "未标注" : extracted.get("deadline"))
                            + "），请审核资质要求并确认是否报名"));
            dto.setReceiverIds(List.of(1L));
            dto.setRelatedId(tenderId);
            NotificationSendResult result = notificationService.send(dto);
            log.info("AGT-01 标讯推送已发送: tenderId={}, sent={}", tenderId, result.getSent());
        } catch (Exception e) {
            log.warn("AGT-01 标讯推送失败（不阻塞建档）: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-01 标讯采集解析");
        out.put("tenderId", tenderId);
        out.put("extracted", extracted);
        return objectMapper.writeValueAsString(out);
    }

    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal parseMoney(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(Object value) {
        String text = str(value);
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            log.warn("AGT-01 截止日期解析失败，置空: {}", text);
            return null;
        }
    }
}
