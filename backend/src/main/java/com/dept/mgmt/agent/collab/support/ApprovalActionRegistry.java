package com.dept.mgmt.agent.collab.support;

import com.dept.mgmt.aiworkspace.ops.OpsTool;
import com.dept.mgmt.aiworkspace.ops.OpsToolRegistry;
import com.dept.mgmt.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 审批动作注册表（A5：Ops 写工具动作化白名单，HITL 执行入口）
 *
 * <p>动作编码规范：{@code OPS_<OpsTool 名点转下划线大写>}，如 OPS_TASK_CREATE。
 * 审批通过后由 {@code DecisionAdviceService#executeApproval} 调用
 * {@link #execute}，复用 OpsTool 的 executor + precheck——零重复实现。
 *
 * <p>白名单原则（deny by default）：
 * <ul>
 *   <li>审批类操作不登记（ecn.approve——Agent 不可代批人工审批）</li>
 *   <li>同步外呼长耗时操作不登记（tender.collect——已有 AGT-27 队列版）</li>
 *   <li>越权风险由调用方双重把关：checkPermission（委托人身份）+ precheck（目标存在性）</li>
 * </ul>
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class ApprovalActionRegistry {

    /** 动作前缀 */
    public static final String PREFIX = "OPS_";

    private final org.springframework.beans.factory.ObjectProvider<OpsToolRegistry> opsRegistryProvider;

    /**
     * 可审批执行的写工具白名单（OpsTool.name）。
     * ecn.approve（审批本身）/ tender.collect（同步外呼）明确排除。
     */
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "task.create", "task.changeStatus", "task.addWorkHour",
            "project.create", "contract.bindProject", "tender.updateStatus",
            "cost.settle", "aftermarket.repairCreate",
            "archive.borrow", "archive.return", "event.handle");

    /** 动作编码 ↔ 工具名双向映射（大写编码无法还原驼峰，必须显式登记） */
    private static final Map<String, String> ACTION_TO_TOOL;
    private static final Map<String, String> TOOL_TO_ACTION;

    static {
        Map<String, String> a2t = new LinkedHashMap<>();
        for (String tool : ALLOWED_TOOLS) {
            a2t.put(PREFIX + tool.toUpperCase().replace('.', '_'), tool);
        }
        ACTION_TO_TOOL = java.util.Collections.unmodifiableMap(a2t);
        Map<String, String> t2a = new LinkedHashMap<>();
        ACTION_TO_TOOL.forEach((a, t) -> t2a.put(t, a));
        TOOL_TO_ACTION = java.util.Collections.unmodifiableMap(t2a);
    }

    /**
     * ObjectProvider 延迟解析 OpsToolRegistry：本 Bean 被 DecisionAdviceService 直接注入，
     * 而 OpsToolRegistry → TaskService → EventBusService → AdviceHandler → DecisionAdviceService
     * 存在构造环（沿用 ObjectProvider 打环模式），Registry 必须保持无强依赖叶子。
     */
    public ApprovalActionRegistry(org.springframework.beans.factory.ObjectProvider<OpsToolRegistry> opsRegistryProvider) {
        this.opsRegistryProvider = opsRegistryProvider;
    }

    /** OpsTool 名 → 动作编码（不在白名单返回 null） */
    public String actionOf(String toolName) {
        return toolName == null ? null : TOOL_TO_ACTION.get(toolName);
    }

    /** 动作编码 → OpsTool（未登记/不存在返回 null） */
    public OpsTool toolOf(String action) {
        if (action == null) {
            return null;
        }
        String toolName = ACTION_TO_TOOL.get(action);
        return toolName == null ? null : opsRegistryProvider.getObject().find(toolName);
    }

    /** 已登记动作清单（tools/list 声明用） */
    public Set<String> allowedActions() {
        return ACTION_TO_TOOL.keySet();
    }

    /**
     * 执行审批动作：precheck 复核（目标存在性/归属，H2 语义）→ 以委托人身份执行 executor。
     * 权限校验（checkPermission）由调用方在执行前完成（审批人已批准 + 委托人生成时校验）。
     *
     * @return 执行结果（OpsTool executor 返回值，通常为渲染后的确认文案）
     */
    public Object execute(String action, Map<String, Object> params,
                          com.dept.mgmt.security.LoginUser actingUser) {
        OpsTool tool = toolOf(action);
        if (tool == null) {
            throw new BusinessException(400, "审批动作未登记或不可执行: " + action);
        }
        // precheck 复核：待确认期间目标状态可能已变化（H2 二次复核语义）
        if (tool.getPrecheck() != null) {
            String blocked = tool.getPrecheck().apply(params == null ? Map.of() : params, actingUser);
            if (blocked != null) {
                throw new BusinessException(409, "审批执行前置校验未通过: " + blocked);
            }
        }
        log.info("审批动作执行: action={}, tool={}, actingUser={}", action, tool.getName(),
                actingUser == null ? null : actingUser.getUserId());
        return tool.getExecutor().apply(params == null ? Map.of() : params, actingUser);
    }
}
