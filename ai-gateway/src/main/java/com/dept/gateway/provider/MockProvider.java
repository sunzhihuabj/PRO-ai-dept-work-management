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
        String content = "[MOCK] 已收到能力点调用，prompt 长度 " + prompt.length()
                + " 字符。此为 Mock 测试通道返回，配置真实商用通道后将由大模型生成。";
        return new ProviderResult(content, 0);
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
