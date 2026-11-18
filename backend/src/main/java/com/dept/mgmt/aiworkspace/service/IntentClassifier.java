package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 意图判别器（V2.0 M2 自然语言驱动）。
 *
 * <p>定位：路由捞回层——规则路由（流程正则/系统操作/Agent 任务/Hermes 委托）全部未命中、
 * 即将落通用对话时，用一次轻量 LLM 调用判别意图；规则命中走快速路径（零额外调用）。
 * LLM 不可用/低置信度一律 fail-open 落通用对话，绝不阻塞对话主链路。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class IntentClassifier {

    /** 支持的意图标签（与路由 handler 一一对应） */
    public static final List<String> INTENTS =
            List.of("WORKFLOW", "OPS", "AGENT_TASK", "DELEGATE", "ADVISOR", "QUERY", "CHAT");

    /** 直接路由的最低置信度 */
    static final double MIN_CONFIDENCE = 0.75;

    private static final String CAP = "CAP_INTENT_CLASSIFY";

    private static final String PROMPT_TEMPLATE = """
            你是对话意图分类器。把用户消息分类到以下意图之一，只输出 JSON：
            - WORKFLOW：要求创建/新建/生成业务流程
            - OPS：要求执行系统内单步操作（改状态、建任务、查数据等，可明确对应系统功能）
            - AGENT_TASK：适合后台 Agent 能力点批量执行的分析/生成/采集类任务
            - DELEGATE：复杂多步/跨模块的综合任务，需要 AI Agent 员工全程处理
            - ADVISOR：行业/专业/合规类问题（标准要求、规范意见、合规怎么做、是否符合某标准）
            - QUERY：咨询问题、需要解释或知识问答
            - CHAT：闲聊、寒暄、与系统无关
            置信度 confidence 取 0~1。示例：
            「帮我看看这个月项目交付情况怎么样」→ OPS（查系统数据）
            「把和客户的对接情况整理成一份材料发给领导」→ DELEGATE
            
            用户消息：%s""";

    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.intent.llm-enabled:true}")
    private boolean llmEnabled;

    public IntentClassifier(AiGatewayClient aiGatewayClient) {
        this.aiGatewayClient = aiGatewayClient;
    }

    /** 意图判别结果 */
    public record Intent(String label, double confidence) {
    }

    /**
     * 判别意图。失败/未开开关返回 empty（fail-open）。
     */
    public Optional<Intent> classify(String content, Long userId) {
        if (!llmEnabled) {
            return Optional.empty();
        }
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            Map<String, Object> intentProp = new LinkedHashMap<>();
            intentProp.put("type", "string");
            intentProp.put("enum", INTENTS);
            Map<String, Object> confProp = Map.of("type", "number");
            schema.put("type", "object");
            schema.put("properties", Map.of("intent", intentProp, "confidence", confProp));
            schema.put("required", List.of("intent", "confidence"));

            String prompt = PROMPT_TEMPLATE.formatted(content.length() > 500 ? content.substring(0, 500) : content);
            String out = aiGatewayClient.chat(CAP, prompt, userId, schema);
            JsonNode node = objectMapper.readTree(out);
            String label = node.path("intent").asText("");
            double conf = node.path("confidence").asDouble(0);
            if (!INTENTS.contains(label) || conf <= 0) {
                log.debug("意图判别输出无效: {}", out);
                return Optional.empty();
            }
            return Optional.of(new Intent(label, conf));
        } catch (Exception e) {
            // fail-open：判别失败不阻塞对话
            log.info("意图判别不可用，回落规则路由: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
