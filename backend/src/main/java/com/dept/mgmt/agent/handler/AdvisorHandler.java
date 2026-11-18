package com.dept.mgmt.agent.handler;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.kb.dto.KbSearchHit;
import com.dept.mgmt.kb.service.KbService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专业意见处理器（V2.0 M7，AGT-28）。
 *
 * <p>链路：业务问题 → kb 混合检索 Top-5 依据（可溯源 chunk）→ LLM 专业推理（JSON 契约：
 * 意见 + 置信度 + 依据引用[ref→kb chunk]）→ 结构化意见落「决策建议中心」（scene=ADVISOR，
 * evidenceJson=依据引用可回链）→ 置信度 &lt;0.85 强制人工审核（requiresApproval=1）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvisorHandler implements CapabilityHandler {

    /** 低置信度阈值：低于此值意见强制人工审核（V2.0 M7 校验标准） */
    static final double REVIEW_THRESHOLD = 0.85;

    private final KbService kbService;
    private final AiGatewayClient aiGatewayClient;
    private final DecisionAdviceService adviceService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-28";
    }

    /** 只读分析（kb 检索 + LLM 推理），不写业务数据，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        return execute(instruction, userId, Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(String instruction, Long userId, Map<String, Object> params) throws Exception {
        if (instruction == null || instruction.isBlank()) {
            throw new BusinessException(400, "专业问题不能为空");
        }
        // 1. kb 依据检索（Top-5）
        List<KbSearchHit> hits = kbService.search(instruction, 5);
        StringBuilder evidence = new StringBuilder();
        int i = 1;
        for (KbSearchHit h : hits) {
            evidence.append("[KB-").append(i++).append("] 《").append(h.getDocumentTitle())
                    .append("》片段#").append(h.getChunkIndex()).append("：")
                    .append(abbreviate(h.getContent(), 300)).append("\n");
        }
        if (hits.isEmpty()) {
            evidence.append("（知识库未检索到直接依据，请基于通用行业实践谨慎作答并在意见中明确说明依据不足）");
        }

        // 2. LLM 专业推理（JSON 契约）
        Map<String, Object> schema = Map.of(
                "required", List.of("opinion", "confidence", "citations"),
                "opinion", "string", "confidence", "number", "citations", "array");
        String prompt = "你是部门的专业合规顾问。基于以下知识库依据回答专业问题，只输出 JSON 不要解释文字。"
                + "字段：opinion（专业意见，Markdown，含结论+理由+建议动作）、confidence（置信度 0~1，"
                + "依据充分且明确取高值，依据不足或相互冲突取低值）、citations（依据引用数组，每项含 ref 如 KB-1、"
                + "quote 支撑原文摘录 ≤80 字；无依据支撑的结论不要写进 citations）。\n\n"
                + "知识库依据：\n" + evidence + "\n\n专业问题：" + instruction;
        String content = stripCodeFence(aiGatewayClient.chat(capabilityCode(), prompt, userId, schema));

        JsonNode node;
        try {
            node = objectMapper.readTree(content);
        } catch (Exception e) {
            throw new BusinessException(500, "专业意见解析失败（模型输出异常），请重试或转人工: " + e.getMessage());
        }
        String opinion = node.path("opinion").asText("");
        double confidence = node.path("confidence").asDouble(0.5);
        List<Map<String, String>> citations = new ArrayList<>();
        for (JsonNode c : node.path("citations")) {
            Map<String, String> cit = new LinkedHashMap<>();
            cit.put("ref", c.path("ref").asText(""));
            cit.put("quote", c.path("quote").asText(""));
            // 回链信息：ref → kb 文档标题/chunk（供前端点击打开原文档）
            int idx = parseRefIndex(cit.get("ref"));
            if (idx >= 1 && idx <= hits.size()) {
                cit.put("documentTitle", hits.get(idx - 1).getDocumentTitle());
                cit.put("documentId", String.valueOf(hits.get(idx - 1).getDocumentId()));
                cit.put("chunkIndex", String.valueOf(hits.get(idx - 1).getChunkIndex()));
            }
            citations.add(cit);
        }

        // 3. 意见落决策建议中心（scene=ADVISOR，置信度<0.85 强制人工审核）
        boolean needReview = confidence < REVIEW_THRESHOLD;
        adviceService.createAdvisorAdvice(instruction, opinion, confidence, citations, userId, needReview);

        // 4. 任务结果（卡片展示）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", "AGT-28 专业意见");
        result.put("opinion", opinion);
        result.put("confidence", confidence);
        result.put("needReview", needReview);
        result.put("citations", citations);
        if (needReview) {
            result.put("notice", "置信度低于 " + REVIEW_THRESHOLD + "，本意见已进入人工审核流，请在「决策建议中心」确认");
        }
        return objectMapper.writeValueAsString(result);
    }

    private int parseRefIndex(String ref) {
        if (ref == null) {
            return -1;
        }
        try {
            return Integer.parseInt(ref.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String stripCodeFence(String s) {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (first > 0 && last > first) {
                return t.substring(first + 1, last).trim();
            }
        }
        return t;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
