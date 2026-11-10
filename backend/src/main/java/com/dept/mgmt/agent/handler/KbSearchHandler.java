package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.kb.dto.KbSearchHit;
import com.dept.mgmt.kb.service.KbService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索问答处理器（CAP_KB_SEARCH）
 * 混合检索 + 溯源引用；若 AI 通道可用则由 LLM 汇总答案（带引用），不可用时直接返回检索片段
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class KbSearchHandler implements CapabilityHandler {

    private final KbService kbService;
    private final AiGatewayClient aiGatewayClient;

    @Override
    public String capabilityCode() {
        return "CAP_KB_SEARCH";
    }

    /** 只读检索，无副作用，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) {
        // 1. 混合检索（向量+关键词，RRF 融合）
        List<KbSearchHit> hits = kbService.search(instruction, 3);
        if (hits.isEmpty()) {
            return "知识库中未找到与该问题相关的内容，建议转人工或补充问题描述。";
        }
        // 2. 构建带溯源引用的上下文，尝试 LLM 汇总；网关不可用（503）时降级为直接返回检索片段
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            KbSearchHit hit = hits.get(i);
            context.append("[").append(i + 1).append("] 《").append(hit.getDocumentTitle())
                    .append("》第").append(hit.getChunkIndex() + 1).append("段：")
                    .append(hit.getContent()).append("\n\n");
        }
        try {
            String answer = aiGatewayClient.chat("CAP_KB_SEARCH",
                    "请基于以下知识库片段回答用户问题，回答末尾用 [编号] 标注引用来源；"
                            + "知识库中没有的信息不要编造。\n\n用户问题：" + instruction
                            + "\n\n知识库片段：\n" + context,
                    userId, null);
            return answer + "\n\n—— 引用来源：" + hits.stream()
                    .map(h -> "《" + h.getDocumentTitle() + "》").distinct().toList();
        } catch (Exception e) {
            // AI 通道不可用：降级为纯检索结果（规划降级链：模型不可用→人工模式，不阻塞）
            return "【检索结果（AI 汇总暂不可用，直接返回原文片段）】\n\n" + context;
        }
    }
}
