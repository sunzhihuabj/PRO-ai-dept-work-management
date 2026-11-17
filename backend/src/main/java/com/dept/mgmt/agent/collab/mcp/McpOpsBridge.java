package com.dept.mgmt.agent.collab.mcp;

import com.dept.mgmt.aiworkspace.ops.OpsParam;
import com.dept.mgmt.aiworkspace.ops.OpsTool;
import com.dept.mgmt.aiworkspace.ops.OpsToolRegistry;
import com.dept.mgmt.aiworkspace.ops.SystemOpsService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ops 工具 → MCP 工具自动注册桥（A3 只读工具全量化 + A4 身份透传，零手写）
 *
 * <p>遍历 {@link OpsToolRegistry} 中 readOnly=true 的工具，按 OpsParam 声明自动生成
 * JSON Schema 并包装为 McpTool——executor 复用 Service 层，不新增 SQL。
 * 写入类工具不在此暴露（A5 审批动作注册表落地后分级接入）。
 *
 * <p>身份与权限（A4）：MCP 调用携带 {@code X-Acting-User-Id}（委托发起人）→
 * {@link McpCallContext} 请求级透传 → 本桥逐调用按该用户查库校验（存在且未禁用），
 * 并复用 {@link SystemOpsService#checkPermission} 做 minRole/permCode 权限判定；
 * 无透传/无效用户回落系统委托身份 {@code app.mcp.acting-user-id}（默认 1）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class McpOpsBridge {

    /** 与 McpToolRegistry 手写 8 工具语义重复的 Ops 工具（保留手写版，避免 tools/list 重复） */
    private static final Set<String> SKIP = Set.of(
            "task.myTodo",            // ≈ task.my_todo
            "project.list",           // ≈ project.query
            "tender.collectPlatforms" // ≈ tender.platforms
    );

    private final OpsToolRegistry opsRegistry;
    private final SysUserMapper userMapper;
    private final SystemOpsService systemOpsService;

    @Value("${app.mcp.acting-user-id:1}")
    private long actingUserId;

    /** 系统委托身份缓存（启动后首次调用时解析，失败回退最小身份） */
    private volatile LoginUser delegateUser;

    public McpOpsBridge(OpsToolRegistry opsRegistry, SysUserMapper userMapper,
                        SystemOpsService systemOpsService) {
        this.opsRegistry = opsRegistry;
        this.userMapper = userMapper;
        this.systemOpsService = systemOpsService;
    }

    /** 只读 Ops 工具 → McpTool 列表（保持注册顺序） */
    public List<McpTool> bridgeTools() {
        List<McpTool> out = new ArrayList<>();
        for (OpsTool t : opsRegistry.all()) {
            if (!t.isReadOnly() || SKIP.contains(t.getName())) {
                continue;
            }
            out.add(new McpTool(t.getName(), t.getDesc(), toSchema(t),
                    args -> execute(t, args)));
        }
        log.info("MCP Ops 桥接注册完成: {} 个只读工具（A4 身份透传已启用）", out.size());
        return out;
    }

    /** OpsParam 声明 → JSON Schema（properties + required） */
    private Map<String, Object> toSchema(OpsTool t) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (OpsParam p : t.getParams()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType(p.getType()));
            prop.put("description", p.getDesc()
                    + ("date".equals(p.getType()) ? "（格式 yyyy-MM-dd）" : "")
                    + (p.getEnums() == null ? "" : "；可选值：" + String.join("/", p.getEnums())));
            properties.put(p.getName(), prop);
            if (p.isRequired()) {
                required.add(p.getName());
            }
        }
        return McpTool.objectSchema(properties, required);
    }

    /** OpsParam.type → JSON Schema type（number 统一为 number，兼容整数与小数入参） */
    private static String jsonType(String opsType) {
        return switch (opsType == null ? "string" : opsType) {
            case "number" -> "number";
            case "bool" -> "boolean";
            default -> "string"; // string/date 统一 string
        };
    }

    /**
     * 工具执行：解析生效身份（X-Acting-User-Id 透传优先 → 系统委托回落）
     * → 逐调用权限校验（复用 SystemOpsService.checkPerm 语义）→ 以该身份执行。
     */
    private Object execute(OpsTool tool, Map<String, Object> args) {
        LoginUser user = resolveActingUser();
        String denied = systemOpsService.checkPermission(tool, user);
        if (denied != null) {
            throw new BusinessException(403, denied);
        }
        return tool.getExecutor().apply(args == null ? Map.of() : args, user);
    }

    /**
     * 生效身份解析：透传用户（校验存在且未禁用，无效即 401 语义拒绝——
     * 避免冒用他人身份静默降级到委托身份执行）→ 系统委托身份回落。
     */
    private LoginUser resolveActingUser() {
        Long acting = McpCallContext.actingUserId();
        if (acting != null) {
            SysUser su = userMapper.selectById(acting);
            if (su == null || (su.getStatus() != null && su.getStatus() == 0)) {
                throw new BusinessException(401, "X-Acting-User-Id 对应用户无效或已禁用: " + acting);
            }
            return toLoginUser(su);
        }
        return delegateUser();
    }

    private LoginUser toLoginUser(SysUser su) {
        return new LoginUser(su.getId(), su.getUsername(), su.getRoleCodes(), List.of());
    }

    /** 系统委托身份（懒加载：按 app.mcp.acting-user-id 查库；查不到回退最小身份） */
    private LoginUser delegateUser() {
        LoginUser u = delegateUser;
        if (u != null) {
            return u;
        }
        synchronized (this) {
            if (delegateUser == null) {
                SysUser su = userMapper.selectById(actingUserId);
                delegateUser = su == null
                        ? new LoginUser(actingUserId, "mcp-delegate", "", List.of())
                        : toLoginUser(su);
                log.info("MCP 系统委托身份就绪: userId={} username={}",
                        delegateUser.getUserId(), delegateUser.getUsername());
            }
            return delegateUser;
        }
    }
}
