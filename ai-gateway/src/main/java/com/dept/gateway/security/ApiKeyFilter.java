package com.dept.gateway.security;

import com.dept.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 服务间认证过滤器：校验 X-API-Key 请求头（backend 与网关共享密钥）
 * 网关无用户体系，服务间认证采用最简共享密钥（Phase 1 边界内合理）
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // 健康检查放行（L2 整改：精确路径匹配，替代 endsWith 宽松后缀——防 "/x/health" 等路径误放行）
        if ("/v1/gateway/health".equals(path) || "/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        // 阶段一：OpenAI 兼容门面放行 Authorization: Bearer（Hermes/Dify 等外部 Agent 惯用头）
        if (!StringUtils.hasText(apiKey)) {
            String auth = request.getHeader("Authorization");
            if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
                apiKey = auth.substring(7).trim();
            }
        }
        // L2 整改：恒时比较（MessageDigest.isEqual），防计时侧信道逐字节探测密钥
        boolean valid = apiKey != null && !apiKey.isBlank()
                && java.security.MessageDigest.isEqual(
                        apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        properties.getApiKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!valid) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("code", 401, "msg", "API-Key 无效")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
