package com.dept.mgmt.agent.collab.mcp;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用身份统一解析器（V2.0 铁律二：权限跟随当前用户，fail-closed）
 *
 * <p>解析顺序：工具参数 actingUserId（Hermes 按 inputSchema required 回填，任务书声明
 * 委托人身份）→ 请求头透传 {@link McpCallContext}（X-Acting-User-Id）→ 两者皆无
 * 抛 401 拒绝执行。相比 V1.0 的「系统委托身份回落」，删除任何降级路径——
 * 上下文缺失即拒绝（fail-closed），杜绝以系统身份提权执行。
 *
 * <p>身份校验：userId 必须对应存在且未禁用的用户（401 语义），杜绝冒用他人身份
 * 静默执行；伪造 actingUserId 生成的写审批建议会发给被冒充者本人，不会被陌生
 * 建议的采纳操作放行（铁律三 HITL 兜底），且全链路留痕（铁律五审计）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class McpIdentityResolver {

    public static final String IDENTITY_PARAM = "actingUserId";
    public static final String IDENTITY_PARAM_DESC =
            "委托发起人用户ID（必填）：必须与委托任务书中声明的委托人一致；"
                    + "所有业务数据以该用户身份访问（权限跟随当前用户）";

    private final SysUserMapper userMapper;

    public McpIdentityResolver(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 解析必填身份（fail-closed）：args.actingUserId 优先 → 请求头透传 → 均无即 401。
     *
     * @param args 工具调用参数（可为 null）
     * @return 委托发起人 userId
     * @throws BusinessException 401 无身份上下文
     */
    public long resolveRequired(Map<String, Object> args) {
        Long fromArgs = longArg(args, IDENTITY_PARAM);
        if (fromArgs != null && fromArgs > 0) {
            return fromArgs;
        }
        Long fromHeader = McpCallContext.actingUserId();
        if (fromHeader != null && fromHeader > 0) {
            return fromHeader;
        }
        // V2.0 铁律二 fail-closed：不再回落系统委托身份
        throw new BusinessException(401,
                "缺少调用身份（actingUserId 参数或 X-Acting-User-Id 头）——"
                        + "MCP 工具调用必须携带委托发起人身份，权限跟随当前用户");
    }

    /** userId → 有效 LoginUser（存在且未禁用；否则 401 语义拒绝） */
    public LoginUser requireValidUser(long userId) {
        SysUser su = userMapper.selectById(userId);
        if (su == null || (su.getStatus() != null && su.getStatus() == 0)) {
            throw new BusinessException(401, "调用身份无效或已禁用: userId=" + userId);
        }
        return new LoginUser(su.getId(), su.getUsername(), su.getRoleCodes(), List.of());
    }

    /** 统一入口：解析身份 + 校验用户有效性 */
    public LoginUser resolveUser(Map<String, Object> args) {
        return requireValidUser(resolveRequired(args));
    }

    private static Long longArg(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank() || "null".equals(String.valueOf(v))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "参数 " + key + " 类型错误（应为整数）");
        }
    }
}
