package com.dept.mgmt.platform.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysCapabilityMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.platform.identity.dto.EffectivePermissionVO;
import com.dept.mgmt.platform.identity.dto.PermissionCheckResult;
import com.dept.mgmt.platform.identity.entity.SysAgentGrant;
import com.dept.mgmt.platform.identity.entity.SysCapability;
import com.dept.mgmt.platform.identity.enums.Identity;
import com.dept.mgmt.mapper.SysAgentGrantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 有效权限计算引擎（T-102 核心）
 *
 * <p>公式落地：有效权限 = min(身份基线, 被代理人权限, 能力白名单)
 * <ul>
 *   <li>人类路径：能力点启用 且 用户身份 ∈ allowed_identities（is_never 不拦截人类）</li>
 *   <li>Agent 路径（ASSISTANT 以被代理人名义）：有效授权 且 能力点启用 且 非 Never
 *       且 ASSISTANT ∈ allowed_identities 且 被代理人身份 ∈ allowed_identities</li>
 * </ul>
 * <p>deny by default：能力点未登记/停用一律拒绝；被代理人禁用/删除，代理链路实时失效。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private final SysCapabilityMapper capabilityMapper;
    private final SysAgentGrantMapper agentGrantMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 人类用户能力判定
     */
    public PermissionCheckResult checkForUser(Long userId, String capabilityCode) {
        List<String> chain = new ArrayList<>();
        // 1. 能力点登记校验（deny by default）
        SysCapability capability = loadCapability(capabilityCode, chain);
        if (capability == null) {
            return PermissionCheckResult.deny("能力点未登记或已停用: " + capabilityCode, chain);
        }
        // 2. 用户身份校验
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            chain.add("用户无效或已禁用: userId=" + userId);
            return PermissionCheckResult.deny("用户无效或已禁用", chain);
        }
        Identity identity = Identity.of(user.getIdentity());
        chain.add("用户身份: " + identity.getCode());
        Set<String> allowed = parseIdentities(capability.getAllowedIdentities());
        if (allowed.contains(identity.getCode())) {
            return PermissionCheckResult.allow(chain);
        }
        chain.add("身份不在能力允许范围: allowed=" + allowed);
        return PermissionCheckResult.deny("当前身份无权执行该能力", chain);
    }

    /**
     * Agent（ASSISTANT）以被代理人名义的能力判定
     * 公式：min(身份基线=ASSISTANT 基线, 被代理人权限, 能力白名单=授权表)
     *
     * @param agentInstanceKey Agent 实例标识
     * @param principalUserId  被代理人用户ID
     * @param capabilityCode   能力点编码
     */
    public PermissionCheckResult checkForAgent(String agentInstanceKey, Long principalUserId, String capabilityCode) {
        List<String> chain = new ArrayList<>();
        // 1. 能力点登记校验
        SysCapability capability = loadCapability(capabilityCode, chain);
        if (capability == null) {
            return PermissionCheckResult.deny("能力点未登记或已停用: " + capabilityCode, chain);
        }
        // 2. 永久禁止类一票否决（审批/资金/主数据建档/删除，任何白名单都救不回）
        if (capability.getIsNever() != null && capability.getIsNever() == 1) {
            chain.add("永久禁止类操作（is_never=1），Agent 不可执行");
            return PermissionCheckResult.deny("永久禁止类操作，Agent 不可执行", chain);
        }
        // 3. 能力白名单（授权表）：有效 = 匹配 + 状态有效 + 未过期
        SysAgentGrant grant = agentGrantMapper.selectOne(new LambdaQueryWrapper<SysAgentGrant>()
                .eq(SysAgentGrant::getAgentInstanceKey, agentInstanceKey)
                .eq(SysAgentGrant::getCapabilityCode, capabilityCode)
                .eq(SysAgentGrant::getStatus, 1)
                .gt(SysAgentGrant::getExpireTime, LocalDateTime.now()));
        if (grant == null) {
            chain.add("无有效授权（不存在/已撤销/已过期）: agentKey=" + agentInstanceKey);
            return PermissionCheckResult.deny("无有效授权或授权已过期", chain);
        }
        chain.add("授权有效: grantId=" + grant.getId() + ", expire=" + grant.getExpireTime());
        // 4. 授权主体一致性（防冒用他人授权）
        if (!principalUserId.equals(grant.getPrincipalUserId())) {
            chain.add("授权主体不匹配: grant.principal=" + grant.getPrincipalUserId());
            return PermissionCheckResult.deny("授权主体不匹配", chain);
        }
        // 5. 被代理人实时有效性（禁用/删除即失效）
        SysUser principal = sysUserMapper.selectById(principalUserId);
        if (principal == null || (principal.getStatus() != null && principal.getStatus() == 0)) {
            chain.add("被代理人无效或已禁用: userId=" + principalUserId);
            return PermissionCheckResult.deny("被代理人无效或已禁用", chain);
        }
        // 6. 双身份校验：ASSISTANT 基线 + 被代理人身份都必须在 allowed_identities 内
        Set<String> allowed = parseIdentities(capability.getAllowedIdentities());
        if (!allowed.contains(Identity.ASSISTANT.getCode())) {
            chain.add("能力不允许 ASSISTANT 身份: allowed=" + allowed);
            return PermissionCheckResult.deny("该能力不允许 Agent 执行", chain);
        }
        Identity principalIdentity = Identity.of(principal.getIdentity());
        chain.add("被代理人身份: " + principalIdentity.getCode());
        if (!allowed.contains(principalIdentity.getCode())) {
            chain.add("被代理人身份不足: allowed=" + allowed);
            return PermissionCheckResult.deny("被代理人身份不足", chain);
        }
        return PermissionCheckResult.allow(chain);
    }

    /**
     * 当前用户有效权限列表（前端权限菜单后续消费）
     */
    public EffectivePermissionVO listEffective(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        Identity identity = user == null ? Identity.WORKER : Identity.of(user.getIdentity());
        List<SysCapability> capabilities = capabilityMapper.selectList(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getStatus, 1));
        List<EffectivePermissionVO.CapabilityVO> effective = capabilities.stream()
                .filter(c -> parseIdentities(c.getAllowedIdentities()).contains(identity.getCode()))
                .map(c -> new EffectivePermissionVO.CapabilityVO(
                        c.getCapabilityCode(), c.getCapabilityName(), c.getCategory(), c.getIsNever()))
                .collect(Collectors.toList());
        return new EffectivePermissionVO(identity.getCode(), identity.getLabel(), effective);
    }

    // ===== 私有辅助 =====

    private SysCapability loadCapability(String capabilityCode, List<String> chain) {
        SysCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getCapabilityCode, capabilityCode)
                .eq(SysCapability::getStatus, 1));
        chain.add("能力点" + (capability == null ? "未登记或已停用: " + capabilityCode : "已登记: " + capabilityCode));
        return capability;
    }

    private Set<String> parseIdentities(String allowedIdentities) {
        if (!StringUtils.hasText(allowedIdentities)) {
            return Set.of();
        }
        return Arrays.stream(allowedIdentities.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
