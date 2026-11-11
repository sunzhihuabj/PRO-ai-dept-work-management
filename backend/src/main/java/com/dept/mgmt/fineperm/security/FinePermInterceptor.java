package com.dept.mgmt.fineperm.security;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.fineperm.annotation.RequirePerm;
import com.dept.mgmt.fineperm.service.FinePermCacheService;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 精细权限拦截器（T-404 接口级权限校验）
 * 校验标注了 @RequirePerm 的接口：当前用户须持有对应 API 权限点。
 * 灰度原则：用户三维度均未配置精细权限（finePermEnabled=false）时放行，存量用户零影响。
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class FinePermInterceptor implements HandlerInterceptor {

    private final FinePermCacheService cacheService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePerm requirePerm = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (requirePerm == null) {
            return true;
        }

        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return reject(response, HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期");
        }

        // 灰度：未启用精细控制的用户直接放行
        FinePermCacheService.CacheEntry entry = cacheService.get(loginUser.getUserId());
        if (!entry.finePermEnabled()) {
            return true;
        }

        if (entry.grantedPerms().contains(requirePerm.value())) {
            return true;
        }

        // 最高角色兜底（防自锁）：ROLE_DEPT_LEADER 为系统最高角色，始终具备权限配置能力，
        // 避免首个被配置精细权限的管理员把自己锁在配置界面之外（与现状角色体系行为一致）
        boolean isTopRole = loginUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEPT_LEADER".equals(a.getAuthority()));
        if (isTopRole) {
            return true;
        }

        return reject(response, HttpStatus.FORBIDDEN.value(), "无访问权限，缺少接口权限点: " + requirePerm.value());
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(status, message)));
        return false;
    }
}
