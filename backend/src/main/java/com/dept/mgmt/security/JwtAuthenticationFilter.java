package com.dept.mgmt.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT认证过滤器
 * 拦截请求，解析Token并设置SecurityContext
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    /** 白名单路径（不需要认证） */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/v1/auth/login",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/webjars",
            "/favicon"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String token = resolveToken(request);

        // 白名单直接放行
        if (isWhiteListed(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            try {
                Claims claims = jwtUtils.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                if (userId == null) {
                    Object idObj = claims.get("userId");
                    if (idObj instanceof Integer) {
                        userId = ((Integer) idObj).longValue();
                    }
                }
                String username = claims.getSubject();
                String roles = claims.get("roles", String.class);

                // 构建权限列表
                List<SimpleGrantedAuthority> authorities = List.of();
                if (StringUtils.hasText(roles)) {
                    String cleanRoles = roles.replaceAll("[\\[\\]\"]", "");
                    authorities = Arrays.stream(cleanRoles.split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                }

                LoginUser loginUser = new LoginUser(userId, username, roles, authorities);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.warn("Token解析失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中解析Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 判断是否为白名单路径
     */
    private boolean isWhiteListed(String uri) {
        return WHITE_LIST.stream().anyMatch(uri::contains);
    }
}
