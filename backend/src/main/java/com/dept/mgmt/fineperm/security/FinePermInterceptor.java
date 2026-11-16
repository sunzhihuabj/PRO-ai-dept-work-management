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
import java.util.List;
import java.util.Map;

/**
 * 精细权限拦截器（T-404 接口级权限校验）
 * 校验标注了 @RequirePerm 的接口：当前用户须持有对应 API 权限点。
 *
 * <p>安全策略（H1 整改：默认拒绝、白名单放行）：
 * <ul>
 *   <li>已启用精细权限的用户：按授权记录判定；</li>
 *   <li>未启用精细权限的存量用户：按「权限点 → 默认授权角色基线」白名单判定——
 *       系统管理域严格收口至部门领导，业务域保持角色可用性；无角色/陌生角色账号一律拒绝；</li>
 *   <li>部门领导始终兜底（防自锁）。</li>
 * </ul>
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class FinePermInterceptor implements HandlerInterceptor {

    /**
     * 权限点 → 默认授权角色基线（灰度回退白名单）。
     * key 支持精确权限点或前缀（如 api:system/user:）；未命中基线的权限点默认拒绝。
     */
    private static final Map<String, List<String>> DEFAULT_ROLE_BASELINE = Map.ofEntries(
            // 系统管理域：仅部门领导（用户/权限配置为敏感管理功能）
            Map.entry("api:system/user:", List.of("ROLE_DEPT_LEADER")),
            Map.entry("api:system/perm-config:", List.of("ROLE_DEPT_LEADER")),
            // 合同业务域：全业务角色可用（维持存量可用性，仅挡住无角色账号）
            Map.entry("api:contract:", List.of("ROLE_DEPT_LEADER", "ROLE_PROJECT_LEADER", "ROLE_REFORM_LEADER",
                    "ROLE_TECHNICIAN", "ROLE_DEVELOPER", "ROLE_ARCHIVIST", "ROLE_AUDITOR")),
            // AI 工作流：管理/发起/审批按角色分工
            Map.entry("api:wf:manage", List.of("ROLE_DEPT_LEADER", "ROLE_PROJECT_LEADER", "ROLE_REFORM_LEADER")),
            Map.entry("api:wf:start", List.of("ROLE_DEPT_LEADER", "ROLE_PROJECT_LEADER", "ROLE_REFORM_LEADER",
                    "ROLE_TECHNICIAN", "ROLE_DEVELOPER", "ROLE_ARCHIVIST", "ROLE_AUDITOR")),
            Map.entry("api:wf:approve", List.of("ROLE_DEPT_LEADER", "ROLE_PROJECT_LEADER", "ROLE_REFORM_LEADER", "ROLE_AUDITOR")));

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

        // 最高角色兜底（防自锁）：ROLE_DEPT_LEADER 为系统最高角色，始终具备权限配置能力，
        // 避免首个被配置精细权限的管理员把自己锁在配置界面之外（与现状角色体系行为一致）
        boolean isTopRole = loginUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEPT_LEADER".equals(a.getAuthority()));
        if (isTopRole) {
            return true;
        }

        // 已启用精细权限：按授权记录判定
        FinePermCacheService.CacheEntry entry = cacheService.get(loginUser.getUserId());
        if (entry.finePermEnabled()) {
            if (entry.grantedPerms().contains(requirePerm.value())) {
                return true;
            }
            return reject(response, HttpStatus.FORBIDDEN.value(), "无访问权限，缺少接口权限点: " + requirePerm.value());
        }

        // 未启用精细权限的存量用户：按默认角色基线白名单判定（默认拒绝，未命中即拒）
        List<String> baselineRoles = resolveBaseline(requirePerm.value());
        if (baselineRoles != null) {
            boolean roleAllowed = loginUser.getAuthorities().stream()
                    .anyMatch(a -> baselineRoles.contains(a.getAuthority()));
            if (roleAllowed) {
                return true;
            }
        }
        return reject(response, HttpStatus.FORBIDDEN.value(), "无访问权限，缺少接口权限点: " + requirePerm.value());
    }

    /** 解析权限点对应的基线角色：先精确匹配，再前缀匹配 */
    private List<String> resolveBaseline(String permCode) {
        List<String> exact = DEFAULT_ROLE_BASELINE.get(permCode);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, List<String>> e : DEFAULT_ROLE_BASELINE.entrySet()) {
            if (e.getKey().endsWith(":") && permCode.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(status, message)));
        return false;
    }
}
