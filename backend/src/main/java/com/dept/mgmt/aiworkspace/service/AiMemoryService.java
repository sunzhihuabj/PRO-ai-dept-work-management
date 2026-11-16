package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.aiworkspace.entity.AiUserMemory;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.mapper.AiConversationMapper;
import com.dept.mgmt.mapper.AiMessageMapper;
import com.dept.mgmt.mapper.AiUserMemoryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 对话记忆服务（P0-3）
 *
 * <p>两级记忆，解决长会话 token 爆炸与「聊过就忘」两个问题：
 * <ul>
 * <li><b>会话滚动摘要</b>：未摘要消息累计超过阈值时，把较早的消息压缩为一段摘要
 *     （保留实体名/诉求/结论），prompt 只携带「摘要 + 最近 KEEP_RECENT 条原文」，
 *     长会话 token 成本从线性增长收敛为常数。</li>
 * <li><b>用户长期记忆</b>：摘要时顺带抽取≤3条稳定事实（如常跟项目/偏好），
 *     沉淀到 ai_user_memory，后续每轮对话注入最近 5 条，实现跨会话「记得住用户」。</li>
 * </ul>
 *
 * <p>全程防御式（try-catch 收敛为日志）：记忆功能任何异常都不阻塞对话主流程。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMemoryService {

    /** 未摘要消息达到该条数才触发压缩（避免短会话浪费一次 LLM 调用） */
    private static final int UNSUMMARIZED_THRESHOLD = 12;

    /** 最近消息保持原文携带的条数（摘要 + 这部分 = 完整上下文） */
    private static final int KEEP_RECENT = 8;

    /** 每次抽取长期记忆事实的上限 */
    private static final int MAX_FACTS = 3;

    /** 长期记忆召回条数 */
    private static final int RECALL_LIMIT = 5;

    /** 摘要兜底长度（TEXT 列安全上限内） */
    private static final int SUMMARY_FALLBACK_MAX = 2000;

    /** 摘要能力通道（平台已注册的对话型能力点） */
    private static final String CAP_SUMMARY = "CAP_DOC_SUMMARY";

    /** 摘要输出 JSON 契约（网关侧校验 + 失败重生成一次） */
    private static final Map<String, Object> SUMMARY_SCHEMA = Map.of(
            "type", "object",
            "required", List.of("summary"),
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "facts", Map.of("type", "array", "items", Map.of("type", "string"))));

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiUserMemoryMapper memoryMapper;
    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    /**
     * 会话滚动摘要（每轮对话后调用；不满足阈值静默跳过，异常只记日志）。
     * 摘要水位线：summaryMsgId 之前（含）的消息已被压缩，之后的消息仍原文携带。
     */
    public void maybeSummarize(AiConversation conv) {
        try {
            doSummarize(conv);
        } catch (Exception e) {
            log.warn("会话滚动摘要失败（不影响对话）: conv={}", conv == null ? null : conv.getId(), e);
        }
    }

    /** 召回用户长期记忆（最近 RECALL_LIMIT 条；异常降级为空列表） */
    public List<String> recall(Long userId) {
        try {
            if (userId == null) {
                return List.of();
            }
            List<AiUserMemory> list = memoryMapper.selectList(new LambdaQueryWrapper<AiUserMemory>()
                    .eq(AiUserMemory::getUserId, userId)
                    .orderByDesc(AiUserMemory::getId)
                    .last("LIMIT " + RECALL_LIMIT));
            return list.stream()
                    .map(AiUserMemory::getContent)
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception e) {
            log.warn("用户长期记忆召回失败（降级为空）: userId={}", userId, e);
            return List.of();
        }
    }

    // ==================== 内部实现 ====================

    private void doSummarize(AiConversation conv) {
        if (conv == null || conv.getId() == null) {
            return;
        }
        List<AiMessage> all = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conv.getId())
                .orderByAsc(AiMessage::getId)
                .last("LIMIT 500"));
        // 最近 KEEP_RECENT 条保持原文，只有更早的才参与压缩
        if (all.size() <= KEEP_RECENT) {
            return;
        }
        int cutoffIdx = all.size() - KEEP_RECENT;
        Long coveredId = conv.getSummaryMsgId();
        List<AiMessage> pending = new ArrayList<>();
        for (int i = 0; i < cutoffIdx; i++) {
            AiMessage m = all.get(i);
            if (coveredId == null || m.getId() > coveredId) {
                pending.add(m);
            }
        }
        // 未压缩消息不足阈值：跳过（摊薄 LLM 调用成本）
        if (pending.size() < UNSUMMARIZED_THRESHOLD) {
            return;
        }

        String newSummary;
        List<String> newFacts;
        SummaryResult result = summarizeByLlm(conv, pending);
        newSummary = result.summary();
        newFacts = result.facts();
        Long newCoveredId = all.get(cutoffIdx - 1).getId();

        // 更新摘要水位线（解析失败也已兜底为原文截断，不会丢上下文）
        conv.setSummary(newSummary);
        conv.setSummaryMsgId(newCoveredId);
        conversationMapper.updateById(conv);

        // 抽取的稳定事实沉淀为长期记忆（仅 LLM 正常返回 facts 时）
        persistFacts(conv, newFacts);
    }

    /** LLM 摘要输出：新摘要 + 抽取的稳定事实 */
    private record SummaryResult(String summary, List<String> facts) {
    }

    /** 调用 LLM 生成「旧摘要 + 新消息」的增量压缩；失败兜底为原文拼接截断 */
    private SummaryResult summarizeByLlm(AiConversation conv, List<AiMessage> pending) {
        StringBuilder prompt = new StringBuilder("请压缩更新一段工作对话的会话摘要，只输出 JSON。\n\n");
        if (StringUtils.hasText(conv.getSummary())) {
            prompt.append("【已有会话摘要】\n").append(conv.getSummary()).append("\n\n");
        }
        prompt.append("【需并入摘要的对话记录】\n");
        for (AiMessage m : pending) {
            prompt.append("user".equals(m.getRole()) ? "用户：" : "助手：")
                    .append(abbreviate(m.getContent(), 400)).append('\n');
        }
        prompt.append("""

                【输出要求】
                1. summary：合并已有摘要与新记录，输出不超过300字的会话摘要。必须保留：涉及的项目/任务/合同/标讯等实体名称、用户的明确诉求、已形成的结论与待办承诺；省略寒暄与过程细节。
                2. facts：从对话中抽取最多3条关于该用户的稳定事实（跨会话长期有效，如「主要负责XX项目」「周报偏好表格形式」）；没有则输出空数组。
                只输出 JSON 本身：{"summary":"...","facts":["..."]}
                """);
        String raw;
        try {
            raw = aiGatewayClient.chat(CAP_SUMMARY, prompt.toString(), conv.getUserId(), SUMMARY_SCHEMA);
        } catch (Exception e) {
            log.warn("摘要 LLM 调用失败，降级为原文截断: conv={}", conv.getId(), e);
            return new SummaryResult(fallbackSummary(conv.getSummary(), pending), List.of());
        }
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            JsonNode summaryNode = node.get("summary");
            String summary = summaryNode == null ? null : summaryNode.asText();
            if (!StringUtils.hasText(summary)) {
                return new SummaryResult(fallbackSummary(conv.getSummary(), pending), List.of());
            }
            List<String> facts = new ArrayList<>();
            JsonNode factsNode = node.get("facts");
            if (factsNode != null && factsNode.isArray()) {
                for (JsonNode f : factsNode) {
                    if (StringUtils.hasText(f.asText())) {
                        facts.add(f.asText().trim());
                    }
                }
            }
            return new SummaryResult(summary, facts);
        } catch (Exception e) {
            log.warn("摘要输出解析失败，降级为原文截断: conv={}", conv.getId());
            return new SummaryResult(fallbackSummary(conv.getSummary(), pending), List.of());
        }
    }

    /** 事实沉淀：与既有记忆去重，最多 MAX_FACTS 条 */
    private void persistFacts(AiConversation conv, List<String> facts) {
        if (facts == null || facts.isEmpty() || conv.getUserId() == null) {
            return;
        }
        try {
            Set<String> existing = new HashSet<>();
            for (AiUserMemory m : memoryMapper.selectList(new LambdaQueryWrapper<AiUserMemory>()
                    .eq(AiUserMemory::getUserId, conv.getUserId()))) {
                if (StringUtils.hasText(m.getContent())) {
                    existing.add(m.getContent());
                }
            }
            int inserted = 0;
            for (String fact : facts) {
                String content = fact.length() > 500 ? fact.substring(0, 500) : fact;
                if (existing.contains(content) || inserted >= MAX_FACTS) {
                    continue;
                }
                AiUserMemory memory = new AiUserMemory();
                memory.setUserId(conv.getUserId());
                memory.setConversationId(conv.getId());
                memory.setKind("fact");
                memory.setContent(content);
                memory.setConfidence(0.8);
                memoryMapper.insert(memory);
                existing.add(content);
                inserted++;
            }
        } catch (Exception e) {
            log.warn("长期记忆沉淀失败（不影响对话）: conv={}", conv.getId(), e);
        }
    }

    /** 解析失败兜底：旧摘要 + 新消息原文拼接，截断到安全长度 */
    private String fallbackSummary(String oldSummary, List<AiMessage> pending) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(oldSummary)) {
            sb.append(oldSummary).append('\n');
        }
        for (AiMessage m : pending) {
            sb.append("user".equals(m.getRole()) ? "用户：" : "助手：")
                    .append(abbreviate(m.getContent(), 200)).append('\n');
        }
        String s = sb.toString();
        return s.length() > SUMMARY_FALLBACK_MAX ? s.substring(s.length() - SUMMARY_FALLBACK_MAX) : s;
    }

    /** 容忍模型输出围栏/前后杂文：截取第一个 { 到最后一个 } */
    private String extractJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "{}";
        }
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return start >= 0 && end > start ? s.substring(start, end + 1) : s;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
