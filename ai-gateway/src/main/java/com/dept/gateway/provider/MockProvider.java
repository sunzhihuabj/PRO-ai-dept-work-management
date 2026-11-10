package com.dept.gateway.provider;

import com.dept.gateway.entity.AiProviderConfig;
import org.springframework.stereotype.Component;

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
}
