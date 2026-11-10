package com.dept.gateway.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider 注册表：按通道类型路由到对应实现
 *
 * @author dept-mgmt
 */
@Component
public class ProviderRegistry {

    private final Map<String, AiProvider> providers;

    public ProviderRegistry(List<AiProvider> providerBeans) {
        this.providers = providerBeans.stream()
                .collect(Collectors.toMap(AiProvider::type, Function.identity()));
    }

    public AiProvider get(String type) {
        AiProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("未知通道类型: " + type);
        }
        return provider;
    }
}
