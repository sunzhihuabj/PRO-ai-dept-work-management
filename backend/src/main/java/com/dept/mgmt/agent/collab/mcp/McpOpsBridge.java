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
 * Ops 工具 → MCP 工具自动注册桥（A3 只读工具全量化 + A4 身份透传 + B4 写工具审批化，零手写）
 *
 * <p>只读工具（readOnly=true）：executor 直连 Service 层，不新增 SQL；
 * 写入类工具（B4 二期）：仅暴露 {@code ApprovalActionRegistry} 白名单内的工具——
 * executor 不直接写入，而是经 {@code DecisionAdviceService#requestToolApproval}
 * 生成待确认审批建议（HMAC 锁定参数 + 委托人权限预校验），委托人采纳后才执行
 * （HITL 双保险：白名单 deny-by-default + 审批确认）。白名单外写工具不暴露。
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
    private final com.dept.mgmt.agent.collab.support.ApprovalActionRegistry approvalActionRegistry;
    private final com.dept.mgmt.agent.collab.service.DecisionAdviceService adviceService;

    @Value("${app.mcp.acting-user-id:1}")
    private long actingUserId;

    /** 系统委托身份缓存（启动后首次调用时解析，失败回退最小身份） */
    private volatile LoginUser delegateUser;

    public McpOpsBridge(OpsToolRegistry opsRegistry, SysUserMapper userMapper,
                        SystemOpsService systemOpsService,
                        com.dept.mgmt.agent.collab.support.ApprovalActionRegistry approvalActionRegistry,
                        com.dept.mgmt.agent.collab.service.DecisionAdviceService adviceService) {
        this.opsRegistry = opsRegistry;
        this.userMapper = userMapper;
        this.systemOpsService = systemOpsService;
        this.approvalActionRegistry = approvalActionRegistry;
        this.adviceService = adviceService;
    }

    /** Ops 工具 → McpTool 列表（只读直连 + 白名单写工具审批化，保持注册顺序） */
    public List<McpTool> bridgeTools() {
        List<McpTool> out = new ArrayList<>();
        int writeCount = 0;
        for (OpsTool t : opsRegistry.all()) {
            if (SKIP.contains(t.getName())) {
                continue;
            }
            if (t.isReadOnly()) {
                out.add(new McpTool(t.getName(), t.getDesc(), toSchema(t),
                        args -> execute(t, args)));
            } else if (approvalActionRegistry.actionOf(t.getName()) != null) {
                // B4：白名单写工具——requiresApproval 声明，调用即生成审批建议（不直接写入）
                out.add(new McpTool(t.getName(),
                        t.getDesc() + "（写操作·requiresApproval=true：调用即生成待确认审批建议，委托人在本系统采纳后才执行；本工具不直接写入）",
                        toSchema(t), true, args -> executeWriteViaApproval(t, args)));
                writeCount++;
            }
            // 白名单外写工具（如 ecn.approve / tender.collect）：不暴露
        }
        log.info("MCP Ops 桥接注册完成: 只读 {} + 写工具(审批化) {}（A4 身份透传 + B4 审批桥接已启用）",
                out.size() - writeCount, writeCount);
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
     * 写工具执行（B4 审批化）：不直接写入——解析生效身份并权限校验通过后，
     * 经 requestToolApproval 生成待确认审批建议（HMAC 锁定参数，委托人采纳后执行）。
     * 白名单校验在 requestToolApproval 内部二次把关（deny-by-default）。
     */
    private Object executeWriteViaApproval(OpsTool tool, Map<String, Object> args) {
        LoginUser user = resolveActingUser();
        String denied = systemOpsService.checkPermission(tool, user);
        if (denied != null) {
            throw new BusinessException(403, denied);
        }
        var advice = adviceService.requestToolApproval(tool.getName(),
                args == null ? Map.of() : args, user.getUserId(), "MCP 外部 Agent（Hermes）委托办理");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("approvalRequired", true);
        out.put("adviceId", advice.getId());
        out.put("toolName", tool.getName());
        out.put("title", tool.getTitle());
        out.put("message", "写操作已生成待确认审批建议（adviceId=" + advice.getId()
                + "）。委托人在本系统「AI 决策建议」中采纳后执行，执行结果由审批链路回写；本调用未直接写入任何业务数据。");
        log.info("MCP 写工具转审批: tool={}, adviceId={}, actingUser={}",
                tool.getName(), advice.getId(), user.getUserId());
        return out;
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
