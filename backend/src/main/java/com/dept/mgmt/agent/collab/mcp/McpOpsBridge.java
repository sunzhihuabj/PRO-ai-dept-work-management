package com.dept.mgmt.agent.collab.mcp;

import com.dept.mgmt.aiworkspace.ops.OpsParam;
import com.dept.mgmt.aiworkspace.ops.OpsTool;
import com.dept.mgmt.aiworkspace.ops.OpsToolRegistry;
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
import java.util.function.Function;

/**
 * Ops 工具 → MCP 工具自动注册桥（A3：只读工具全量化，零手写）
 *
 * <p>遍历 {@link OpsToolRegistry} 中 readOnly=true 的工具，按 OpsParam 声明自动生成
 * JSON Schema 并包装为 McpTool——executor 复用 Service 层，不新增 SQL。
 * 写入类工具不在此暴露（A4 身份透传 + A5 审批动作注册表落地后分级接入）。
 *
 * <p>身份方案（本切片）：MCP 调用无登录态，统一以系统委托身份
 * {@code app.mcp.acting-user-id}（默认 1=管理员）执行；A4 升级为
 * X-Acting-User-Id 逐调用透传 + EffectivePermissionService 校验。
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

    @Value("${app.mcp.acting-user-id:1}")
    private long actingUserId;

    /** 委托身份缓存（启动后首次调用时解析，失败回退最小身份） */
    private volatile LoginUser delegateUser;

    public McpOpsBridge(OpsToolRegistry opsRegistry, SysUserMapper userMapper) {
        this.opsRegistry = opsRegistry;
        this.userMapper = userMapper;
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
        log.info("MCP Ops 桥接注册完成: {} 个只读工具", out.size());
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

    private Object execute(OpsTool tool, Map<String, Object> args) {
        return tool.getExecutor().apply(args == null ? Map.of() : args, delegateUser());
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
                        : new LoginUser(su.getId(), su.getUsername(), su.getRoleCodes(), List.of());
                log.info("MCP 系统委托身份就绪: userId={} username={}",
                        delegateUser.getUserId(), delegateUser.getUsername());
            }
            return delegateUser;
        }
    }
}
