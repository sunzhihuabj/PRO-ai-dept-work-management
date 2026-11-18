package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.kb.dto.KbSearchHit;
import com.dept.mgmt.kb.service.KbService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档合规审核服务（V2.0 M6）：文档 → kb 行业规范片段检索 → LLM 逐节比对 →
 * 结构化「合规风险清单」（JSON 契约：章节/风险项/依据/建议改法）。
 * 只读分析不落库批注；文档修改仍走人工编辑/审批（铁律三）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocComplianceService {

    private final DocDraftService docDraftService;
    private final KbService kbService;
    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    /**
     * 合规检查（返回结构化风险清单 + 合规结论）。
     */
    public Map<String, Object> check(Long docId, Long userId) {
        var doc = docDraftService.get(docId);
        String content = doc.getContentMd();
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "文档内容为空，无法合规检查");
        }

        // kb 规范依据（按文档标题+首段检索，Top-4）
        String probe = doc.getTitle() + "\n" + abbreviate(firstParagraph(content), 200);
        StringBuilder basis = new StringBuilder();
        int i = 1;
        try {
            for (KbSearchHit h : kbService.search(probe, 4)) {
                basis.append("[KB-").append(i++).append("] 《").append(h.getDocumentTitle())
                        .append("》#").append(h.getChunkIndex()).append("：")
                        .append(abbreviate(h.getContent(), 260)).append("\n");
            }
        } catch (Exception e) {
            log.info("合规检查 kb 检索失败（通用规则审查）: {}", e.getMessage());
        }
        if (basis.isEmpty()) {
            basis.append("（知识库无匹配规范，按通用行业实践审查）");
        }

        Map<String, Object> schema = Map.of(
                "required", List.of("conclusion", "items"),
                "conclusion", "string", "items", "array");
        String prompt = "你是文档合规审核员。对照知识库行业规范逐节审查以下文档，只输出 JSON。"
                + "字段：conclusion（总体合规结论 ≤80 字）、items（风险清单数组，每项含 section 章节、"
                + "risk 风险项描述、basis 依据（引用 KB-x 或'通用实践'）、suggestion 建议改法；"
                + "未发现的风险不要编造，items 可为空数组，最多 10 条）。\n\n"
                + "知识库规范依据：\n" + basis + "\n\n待审文档（标题：" + doc.getTitle() + "）：\n"
                + abbreviate(content, 6000);
        String out = stripCodeFence(aiGatewayClient.chat("AGT-28", prompt, userId, schema));

        JsonNode node;
        try {
            node = objectMapper.readTree(out);
        } catch (Exception e) {
            throw new BusinessException(500, "合规分析解析失败（模型输出异常），请重试: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", docId);
        result.put("docNo", doc.getDocNo());
        result.put("version", doc.getVersion());
        result.put("conclusion", node.path("conclusion").asText(""));
        result.put("items", node.path("items"));
        result.put("kbBasisCount", i - 1);
        return result;
    }

    private static String firstParagraph(String md) {
        for (String line : md.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("|") && !t.startsWith("```")) {
                return t;
            }
        }
        return md;
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

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
