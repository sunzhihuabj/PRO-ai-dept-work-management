package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.tender.entity.TenderNotice;
import com.dept.mgmt.tender.service.TenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件分析处理器（AGT-03，T-301，Ask）
 * V2 B-06：招标文件 → 需求条目 + 废标条款 + 评分办法（网关 LLM 契约解析），
 * 结果回填 tender_notice.analysis_json 供投标决策引用
 * 文件文本来源：任务 params.docText；缺省取标讯原文 raw_text
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderDocAnalysisHandler implements CapabilityHandler {

    private static final Pattern TENDER_ID = Pattern.compile("tenderId\\s*=\\s*(\\d+)");

    private final AiGatewayClient aiGatewayClient;
    private final TenderService tenderService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-03";
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        return execute(instruction, userId, Map.of());
    }

    @Override
    public String execute(String instruction, Long userId, Map<String, Object> params) throws Exception {
        Long tenderId = parseTenderId(instruction);
        if (tenderId == null) {
            throw new BusinessException(400, "指令中未找到 tenderId=，无法定位标讯");
        }
        TenderNotice notice = tenderService.detail(tenderId);
        String docText = params.get("docText") == null ? null : String.valueOf(params.get("docText"));
        if (docText == null || docText.isBlank()) {
            docText = notice.getRawText();
        }
        if (docText == null || docText.isBlank()) {
            throw new BusinessException(400, "未提供招标文件文本（params.docText），标讯亦无原文，无法分析");
        }

        Map<String, Object> schema = Map.of(
                "required", List.of("requirements", "disqualify_clauses", "scoring_method"),
                "requirements", "array", "disqualify_clauses", "array", "scoring_method", "string");
        String prompt = "你是招标文件分析助手。请从以下招标文件文本中拆解，只输出 JSON，不要任何解释文字。"
                + "字段定义：requirements（技术/商务需求条目数组，每条一句话）、"
                + "disqualify_clauses（废标条款数组，逐条原文摘录）、scoring_method（评分办法概述，一段话）。"
                + "文本中未提及的部分输出空数组或空串，禁止编造。\n\n招标文件文本：\n" + docText;
        String content = stripCodeFence(aiGatewayClient.chat(capabilityCode(), prompt, userId, schema));

        Map<?, ?> analysis;
        try {
            analysis = objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "招标文件分析结果异常，请重试或转人工: " + e.getMessage());
        }
        // 结果回填 analysis_json
        notice.setAnalysisJson(objectMapper.writeValueAsString(analysis));
        tenderService.saveAnalysis(tenderId, notice.getAnalysisJson());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-03 招标文件分析");
        out.put("tenderId", tenderId);
        out.put("analysis", analysis);
        log.info("AGT-03 招标文件分析完成: tenderId={}", tenderId);
        return objectMapper.writeValueAsString(out);
    }

    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
    }

    private Long parseTenderId(String instruction) {
        if (instruction == null) {
            return null;
        }
        Matcher m = TENDER_ID.matcher(instruction);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
