package com.dept.mgmt.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 生产环境密钥治理守卫（H4 整改：fail-fast）
 *
 * <p>生产类 profile（prod/uat/release）启动时强制校验敏感配置，命中源码已公开的
 * 默认值/弱口令即拒绝启动，杜绝"漏配环境变量静默使用公开默认密钥"的上线风险。
 * 本地开发（无 profile / dev）不做拦截，保持开发便利性。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class ProdSecretGuard {

    /** 已随源码公开的旧服务间密钥（轮换作废） */
    private static final String LEGACY_GATEWAY_KEY = "dept-gateway-internal-key-2026";

    private final Environment environment;

    public ProdSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        if (!isProdLikeProfile()) {
            return;
        }
        // 1. 数据库口令：禁止默认 root
        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if ("root".equalsIgnoreCase(dbPassword.trim())) {
            throw fail("数据库口令仍为默认 root，必须通过环境变量 DB_PASSWORD 注入强口令");
        }
        // 2. 服务间密钥：禁止已泄露旧默认值
        String gatewayKey = environment.getProperty("ai.gateway.api-key", "");
        if (gatewayKey.isBlank() || LEGACY_GATEWAY_KEY.equals(gatewayKey.trim())) {
            throw fail("GATEWAY_API_KEY 未配置或仍为已泄露的旧默认值，必须通过环境变量注入并与网关同步");
        }
        // 3. JWT 密钥由 JwtUtils 自行 fail-fast（空/旧默认/强度不足），此处不重复
        log.info("生产环境密钥治理检查通过（数据库口令/服务间密钥均非默认值）");
    }

    private boolean isProdLikeProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "uat".equalsIgnoreCase(p) || "release".equalsIgnoreCase(p));
    }

    private IllegalStateException fail(String detail) {
        return new IllegalStateException("生产环境密钥治理 fail-fast：" + detail);
    }
}
