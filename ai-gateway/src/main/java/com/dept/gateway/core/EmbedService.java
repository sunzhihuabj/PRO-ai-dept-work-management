package com.dept.gateway.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.gateway.crypto.AesGcmCipher;
import com.dept.gateway.dto.EmbedRequest;
import com.dept.gateway.dto.EmbedResponse;
import com.dept.gateway.entity.AiProviderConfig;
import com.dept.gateway.exception.GatewayException;
import com.dept.gateway.mapper.AiProviderConfigMapper;
import com.dept.gateway.provider.AiProvider;
import com.dept.gateway.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 向量化编排服务（T-106 RAG）
 * 走启用的通道降级链：优先支持 embeddings 的商用通道；MOCK 通道提供确定性向量兜底
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedService {

    private final AiProviderConfigMapper configMapper;
    private final AesGcmCipher cipher;
    private final ProviderRegistry providerRegistry;

    /**
     * 批量向量化：逐通道尝试（支持 embed 的通道），全失败返回 503
     */
    public EmbedResponse embed(EmbedRequest request) {
        List<AiProviderConfig> chain = configMapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getEnabled, 1)
                .orderByAsc(AiProviderConfig::getPriority));
        String lastError = null;
        for (AiProviderConfig config : chain) {
            // 解密明文 Key（仅运行时内存）
            if (StringUtils.hasText(config.getApiKeyCipher())) {
                try {
                    config.setApiKeyPlain(cipher.decrypt(config.getApiKeyCipher()));
                } catch (Exception e) {
                    lastError = "通道 " + config.getProviderCode() + " Key 解密失败";
                    continue;
                }
            }
            AiProvider provider = providerRegistry.get(config.getProviderType());
            try {
                List<List<Double>> vectors = provider.embed(config, request.getTexts());
                String model = provider.embedModel(config);
                log.info("向量化成功: provider={}, model={}, texts={}",
                        config.getProviderType(), model, request.getTexts().size());
                return new EmbedResponse(vectors, model);
            } catch (UnsupportedOperationException e) {
                lastError = "通道 " + config.getProviderCode() + " 不支持向量化";
            } catch (Exception e) {
                log.warn("向量化通道失败: {}", config.getProviderCode(), e);
                lastError = "通道 " + config.getProviderCode() + " 调用失败";
            }
        }
        throw new GatewayException(503,
                "向量化失败（无可用通道）：请配置支持 embeddings 的商用通道或启用 Mock 通道"
                        + (lastError == null ? "" : "。最后错误：" + lastError));
    }
}
