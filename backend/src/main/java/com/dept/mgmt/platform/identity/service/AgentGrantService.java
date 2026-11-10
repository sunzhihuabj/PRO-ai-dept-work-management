package com.dept.mgmt.platform.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysCapabilityMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.platform.identity.dto.AgentGrantDTO;
import com.dept.mgmt.platform.identity.entity.SysAgentGrant;
import com.dept.mgmt.platform.identity.entity.SysCapability;
import com.dept.mgmt.mapper.SysAgentGrantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 代理授权管理服务（数据面，T-107 MasterAgent 消费）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGrantService {

    private final SysAgentGrantMapper agentGrantMapper;
    private final SysCapabilityMapper capabilityMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 创建代理授权（同一 agentKey+能力点唯一，重复则覆盖续期）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(AgentGrantDTO dto) {
        // 能力点必须已登记（ deny by default：未登记的能力不允许授权）
        SysCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getCapabilityCode, dto.getCapabilityCode())
                .eq(SysCapability::getStatus, 1));
        if (capability == null) {
            throw new BusinessException(404, "能力点未登记或已停用: " + dto.getCapabilityCode());
        }
        // 被代理人必须存在且启用
        SysUser principal = sysUserMapper.selectById(dto.getPrincipalUserId());
        if (principal == null || (principal.getStatus() != null && principal.getStatus() == 0)) {
            throw new BusinessException(404, "被代理人不存在或已禁用");
        }
        // upsert：同 key+能力点已有授权则续期覆盖
        SysAgentGrant existing = agentGrantMapper.selectOne(new LambdaQueryWrapper<SysAgentGrant>()
                .eq(SysAgentGrant::getAgentInstanceKey, dto.getAgentInstanceKey())
                .eq(SysAgentGrant::getCapabilityCode, dto.getCapabilityCode()));
        LocalDateTime expire = LocalDateTime.now().plusHours(dto.getExpireHours() == null ? 72 : dto.getExpireHours());
        if (existing != null) {
            existing.setPrincipalUserId(dto.getPrincipalUserId());
            existing.setStatus(1);
            existing.setExpireTime(expire);
            agentGrantMapper.updateById(existing);
            log.info("续期Agent授权: agentKey={}, capability={}, expire={}",
                    dto.getAgentInstanceKey(), dto.getCapabilityCode(), expire);
            return existing.getId();
        }
        SysAgentGrant grant = new SysAgentGrant();
        grant.setAgentInstanceKey(dto.getAgentInstanceKey());
        grant.setPrincipalUserId(dto.getPrincipalUserId());
        grant.setCapabilityCode(dto.getCapabilityCode());
        grant.setStatus(1);
        grant.setExpireTime(expire);
        agentGrantMapper.insert(grant);
        log.info("创建Agent授权: agentKey={}, capability={}, principal={}, expire={}",
                dto.getAgentInstanceKey(), dto.getCapabilityCode(), dto.getPrincipalUserId(), expire);
        return grant.getId();
    }

    /**
     * 授权列表（可按被代理人过滤）
     */
    public List<SysAgentGrant> list(Long principalUserId) {
        LambdaQueryWrapper<SysAgentGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(principalUserId != null, SysAgentGrant::getPrincipalUserId, principalUserId);
        wrapper.orderByDesc(SysAgentGrant::getCreateTime);
        return agentGrantMapper.selectList(wrapper);
    }

    /**
     * 撤销授权（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        SysAgentGrant grant = agentGrantMapper.selectById(id);
        if (grant == null) {
            throw new BusinessException(404, "授权不存在");
        }
        agentGrantMapper.deleteById(id);
        log.info("撤销Agent授权: id={}, agentKey={}, capability={}",
                id, grant.getAgentInstanceKey(), grant.getCapabilityCode());
    }
}
