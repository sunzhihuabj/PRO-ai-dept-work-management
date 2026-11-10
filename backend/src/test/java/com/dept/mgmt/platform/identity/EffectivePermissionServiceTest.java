package com.dept.mgmt.platform.identity;

import com.dept.mgmt.platform.identity.dto.AgentGrantDTO;
import com.dept.mgmt.platform.identity.dto.PermissionCheckResult;
import com.dept.mgmt.platform.identity.service.AgentGrantService;
import com.dept.mgmt.platform.identity.service.EffectivePermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有效权限计算引擎测试（依赖本地 MySQL 3307 dept_mgmt 库与 T-102 种子数据）
 * 覆盖：人类双身份判定 / Never 一票否决 / 代理授权三条件 min / 过期 / 主体不匹配 / deny by default
 *
 * @author dept-mgmt
 */
@SpringBootTest
@Transactional
class EffectivePermissionServiceTest {

    @Autowired
    private EffectivePermissionService effectivePermissionService;

    @Autowired
    private AgentGrantService agentGrantService;

    /** 种子：id=1 sunzhihua EXPERT；id=4 yangshuang WORKER */

    @Test
    void humanExpert_allowedOnNeverCapability() {
        // is_never=1 仅拦截 Agent，人类 EXPERT 正常判定
        PermissionCheckResult result = effectivePermissionService.checkForUser(1L, "CAP_CONTRACT_APPROVE");
        assertTrue(result.isAllowed(), "EXPERT 应可通过: " + result.getReason());
    }

    @Test
    void humanWorker_deniedOnExpertOnlyCapability() {
        PermissionCheckResult result = effectivePermissionService.checkForUser(4L, "CAP_CONTRACT_APPROVE");
        assertFalse(result.isAllowed(), "WORKER 应被拒绝: " + result.getReason());
    }

    @Test
    void agent_withoutGrant_denied() {
        PermissionCheckResult result = effectivePermissionService.checkForAgent(
                "agent:1:AGT-06", 1L, "AGT-06");
        assertFalse(result.isAllowed(), "无授权应被拒绝: " + result.getReason());
    }

    @Test
    void agent_withValidGrant_allowed() {
        agentGrantService.create(grant("agent:1:AGT-06", 1L, "AGT-06", 72));
        PermissionCheckResult result = effectivePermissionService.checkForAgent(
                "agent:1:AGT-06", 1L, "AGT-06");
        assertTrue(result.isAllowed(), "三条件齐备应通过: " + result.getReason());
        assertTrue(result.getChain().stream().anyMatch(s -> s.contains("能力点已登记")), "判定链应含能力点检查");
        assertTrue(result.getChain().stream().anyMatch(s -> s.contains("授权有效")), "判定链应含授权检查");
        assertTrue(result.getChain().stream().anyMatch(s -> s.contains("被代理人身份")), "判定链应含被代理人身份检查");
    }

    @Test
    void agent_neverCapability_deniedEvenWithGrant() {
        // 授权表可写入（数据面），但引擎对 is_never=1 一票否决
        agentGrantService.create(grant("agent:1:APPROVE", 1L, "CAP_CONTRACT_APPROVE", 72));
        PermissionCheckResult result = effectivePermissionService.checkForAgent(
                "agent:1:APPROVE", 1L, "CAP_CONTRACT_APPROVE");
        assertFalse(result.isAllowed(), "Never 类 Agent 应被一票否决: " + result.getReason());
    }

    @Test
    void agent_expiredGrant_denied() {
        agentGrantService.create(grant("agent:1:EXPIRED", 1L, "AGT-06", 1));
        // 用过期校验模拟：直接构造 1 小时前到期的授权不可行（create 只能向后），改查未来授权后
        // 通过负数小时不被允许（@Min(1)），这里验证 1 小时内有效 + 说明：过期场景由 SQL 条件 gt(now) 覆盖
        // 为真实验证过期，改为更新 expire_time 到过去
        agentGrantServiceForceExpire("agent:1:EXPIRED", "AGT-06");
        PermissionCheckResult result = effectivePermissionService.checkForAgent(
                "agent:1:EXPIRED", 1L, "AGT-06");
        assertFalse(result.isAllowed(), "过期授权应被拒绝: " + result.getReason());
    }

    @Test
    void agent_principalMismatch_denied() {
        agentGrantService.create(grant("agent:1:MISMATCH", 1L, "AGT-06", 72));
        // 用 yangshuang(4) 的名义使用 sunzhihua(1) 的授权 → 主体不匹配
        PermissionCheckResult result = effectivePermissionService.checkForAgent(
                "agent:1:MISMATCH", 4L, "AGT-06");
        assertFalse(result.isAllowed(), "授权主体不匹配应被拒绝: " + result.getReason());
    }

    @Test
    void unknownCapability_deniedByDefault() {
        PermissionCheckResult result = effectivePermissionService.checkForUser(1L, "CAP_NOT_EXIST");
        assertFalse(result.isAllowed(), "未登记能力点应 deny by default: " + result.getReason());
    }

    // ===== 辅助 =====

    private AgentGrantDTO grant(String agentKey, Long principal, String capability, int hours) {
        AgentGrantDTO dto = new AgentGrantDTO();
        dto.setAgentInstanceKey(agentKey);
        dto.setPrincipalUserId(principal);
        dto.setCapabilityCode(capability);
        dto.setExpireHours(hours);
        return dto;
    }

    @Autowired
    private com.dept.mgmt.mapper.SysAgentGrantMapper agentGrantMapper;

    private void agentGrantServiceForceExpire(String agentKey, String capability) {
        com.dept.mgmt.platform.identity.entity.SysAgentGrant g = agentGrantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dept.mgmt.platform.identity.entity.SysAgentGrant>()
                        .eq(com.dept.mgmt.platform.identity.entity.SysAgentGrant::getAgentInstanceKey, agentKey)
                        .eq(com.dept.mgmt.platform.identity.entity.SysAgentGrant::getCapabilityCode, capability));
        g.setExpireTime(java.time.LocalDateTime.now().minusMinutes(1));
        agentGrantMapper.updateById(g);
    }
}
