package com.dept.gateway.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 生产环境密钥治理守卫（H4 整改：fail-fast）
 *
 * <p>生产类 profile（prod/uat/release）启动时强制校验：数据库口令、服务间密钥、
 * 通道配置加密密钥均不得为源码已公开的默认值，命中即拒绝启动。
 * 本地开发（无 profile / dev）不做拦截，保持开发便利性。
 *
 * <p>注意：config-secret 用于解密 sys_ai_provider_config 存量密文，
 * 生产注入专属密钥前须完成存量密文重加密迁移（详见 yml 注释）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class ProdSecretGuard {

    /** 已随源码公开的旧服务间密钥（轮换作废） */
    private static final String LEGACY_GATEWAY_KEY = "dept-gateway-internal-key-2026";
    /** 已随源码公开的默认配置加密密钥 */
    private static final String LEGACY_CONFIG_SECRET = "dept-ai-config-secret-key-2026-change-me-please";

    private final Environment environment;

    @Value("${gateway.api-key}")
    private String apiKey;

    @Value("${gateway.config-secret}")
    private String configSecret;

    public ProdSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        if (!isProdLikeProfile()) {
            return;
        }
        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if ("root".equalsIgnoreCase(dbPassword.trim())) {
            throw fail("数据库口令仍为默认 root，必须通过环境变量 DB_PASSWORD 注入强口令");
        }
        if (apiKey == null || apiKey.isBlank() || LEGACY_GATEWAY_KEY.equals(apiKey.trim())) {
            throw fail("GATEWAY_API_KEY 未配置或仍为已泄露的旧默认值，必须通过环境变量注入并与 backend 同步");
        }
        if (configSecret == null || configSecret.isBlank() || LEGACY_CONFIG_SECRET.equals(configSecret.trim())) {
            throw fail("AI_CONFIG_SECRET 仍为源码默认值：请注入生产专属密钥（注入前须完成 sys_ai_provider_config 存量密文重加密迁移）");
        }
        log.info("生产环境密钥治理检查通过（数据库口令/服务间密钥/配置加密密钥均非默认值）");
    }

    private boolean isProdLikeProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "uat".equalsIgnoreCase(p) || "release".equalsIgnoreCase(p));
    }

    private IllegalStateException fail(String detail) {
        return new IllegalStateException("生产环境密钥治理 fail-fast：" + detail);
    }
}
