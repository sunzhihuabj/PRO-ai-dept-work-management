package com.dept.gateway.provider;

import com.dept.gateway.entity.AiProviderConfig;

/**
 * AI Provider 抽象：屏蔽底层模型协议差异，统一对话调用契约
 *
 * @author dept-mgmt
 */
public interface AiProvider {

    /**
     * 通道类型标识（OPENAI_COMPAT / MOCK / OLLAMA）
     */
    String type();

    /**
     * 执行一次对话调用
     *
     * @param config   通道配置（endpoint/model/已解密 Key）
     * @param prompt   脱敏后的提示词全文
     * @param maxTokens 最大生成 Token 数（0=默认）
     * @return 模型输出全文
     * @throws Exception 网络/协议/上游错误（由网关统一捕获走降级链）
     */
    ProviderResult chat(AiProviderConfig config, String prompt, int maxTokens) throws Exception;

    /**
     * 调用结果（全文 + Token 用量；用量拿不到时置 0）
     */
    record ProviderResult(String content, int totalTokens) {
    }
}
