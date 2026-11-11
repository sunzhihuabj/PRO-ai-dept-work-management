package com.dept.mgmt.config;

import com.dept.mgmt.fineperm.security.FinePermInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置（T-404：注册精细权限拦截器）
 *
 * @author dept-mgmt
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FinePermInterceptor finePermInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(finePermInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns(
                        "/v1/auth/login",
                        "/v1/auth/wx-login",
                        "/v1/auth/wx-bind",
                        "/v1/dingtalk/auth/login",
                        "/v1/dingtalk/auth/bind"
                );
    }
}
