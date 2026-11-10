package com.dept.gateway.provider;

import com.dept.gateway.entity.AiProviderConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock Provider：开发/测试兜底通道，返回可识别的固定结构，不产生任何费用
 * 仅在显式配置 MOCK 通道并启用时使用，不作为生产降级目标（降级终点是"转人工"）
 *
 * @author dept-mgmt
 */
@Component
public class MockProvider implements AiProvider {

    @Override
    public String type() {
        return "MOCK";
    }

    @Override
    public ProviderResult chat(AiProviderConfig config, String prompt, int maxTokens) {
        // 契约感知：声明了 JSON 契约（【输出要求】标记）时返回满足 required 字段的确定性 JSON，
        // 使 AGT-06/07 等结构化能力在 Mock 通道下全链路可测（含网关契约校验器）
        if (prompt != null && prompt.contains("【输出要求】")) {
            return new ProviderResult(mockContractJson(prompt), 0);
        }
        String content = "[MOCK] 已收到能力点调用，prompt 长度 " + prompt.length()
                + " 字符。此为 Mock 测试通道返回，配置真实商用通道后将由大模型生成。";
        return new ProviderResult(content, 0);
    }

    /** 从提示词中提取 required 字段并生成确定性 Mock JSON */
    private static String mockContractJson(String prompt) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"required\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(prompt);
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        if (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                String field = raw.replace("\"", "").trim();
                if (!field.isEmpty()) {
                    out.put(field, mockValue(field));
                }
            }
        }
        if (out.isEmpty()) {
            out.put("mock", true);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out);
        } catch (Exception e) {
            return "{\"mock\":true}";
        }
    }

    /** 已知语义字段的确定性取值；未知字段输出 MOCK_ 前缀占位 */
    private static Object mockValue(String field) {
        return switch (field) {
            case "contract_name" -> "MOCK提取-塔筒制造合同";
            case "customer_name" -> "MOCK客户-国电联合动力";
            case "amount" -> 1000000;
            case "sign_date" -> "2026-11-10";
            case "warranty_months" -> 24;
            case "pay_ratios" -> java.util.Map.of("prepay", 30, "delivery", 30, "accept", 30, "warranty", 10);
            case "risk_level" -> "中";
            case "risk_summary" -> "MOCK风险摘要：付款条件与验收标准建议复核";
            case "risks" -> List.of();
            default -> "MOCK_" + field;
        };
    }

    /**
     * Mock 向量化：确定性伪随机向量（同一文本恒定、不同文本区分度足够演示检索）
     * 仅用于无 API Key 环境验证 RAG 全链路，生产必须配置真实 embedding 通道
     */
    @Override
    public List<List<Double>> embed(AiProviderConfig config, List<String> texts) {
        return texts.stream().map(MockProvider::mockVector).toList();
    }

    @Override
    public String embedModel(AiProviderConfig config) {
        return "mock-embedding-64";
    }

    /** 生成 64 维确定性向量：以字符 n-gram 哈希分布到各维度 */
    private static List<Double> mockVector(String text) {
        double[] vec = new double[64];
        String normalized = text == null ? "" : text.trim();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int h = Character.hashCode(c);
            vec[h % 64] += 1.0;
            if (i + 1 < normalized.length()) {
                int h2 = (h * 31 + Character.hashCode(normalized.charAt(i + 1)));
                vec[Math.abs(h2) % 64] += 0.5;
            }
        }
        // 归一化（余弦相似度只需方向）
        double norm = Math.sqrt(java.util.Arrays.stream(vec).map(v -> v * v).sum());
        List<Double> list = new java.util.ArrayList<>(64);
        for (double v : vec) {
            list.add(norm == 0 ? 0.0 : v / norm);
        }
        return list;
    }
}
