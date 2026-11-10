package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.PhaseGate;
import com.dept.mgmt.mapper.PhaseGateMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阶段门禁服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GateService {

    private final PhaseGateMapper gateMapper;

    public List<PhaseGate> listByProject(Long projectId) {
        LambdaQueryWrapper<PhaseGate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PhaseGate::getProjectId, projectId);
        wrapper.orderByAsc(PhaseGate::getGateOrder);
        return gateMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PhaseGate dto) {
        if (dto.getProjectId() == null || !StringUtils.hasText(dto.getGateName())) {
            throw new BusinessException(400, "项目与门禁名称必填");
        }
        dto.setId(null);
        dto.setAuditResult(0);
        if (dto.getGateOrder() == null) {
            dto.setGateOrder((int) (listByProject(dto.getProjectId()).size() + 1));
        }
        gateMapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PhaseGate dto) {
        PhaseGate exists = get(id);
        if (exists.getAuditResult() != null && exists.getAuditResult() != 0) {
            throw new BusinessException(409, "已审核的门禁不可修改");
        }
        exists.setGateName(dto.getGateName());
        exists.setGateOrder(dto.getGateOrder());
        exists.setPassCondition(dto.getPassCondition());
        gateMapper.updateById(exists);
    }

    /**
     * 审核门禁：pass=true 通过 / false 驳回
     */
    @Transactional(rollbackFor = Exception.class)
    public PhaseGate audit(Long id, boolean pass, String comment) {
        PhaseGate gate = get(id);
        LoginUser loginUser = LoginUser.current();
        gate.setAuditResult(pass ? 1 : 2);
        gate.setAuditBy(loginUser != null ? loginUser.getUsername() : "system");
        gate.setAuditTime(LocalDateTime.now());
        gate.setAuditComment(comment);
        gateMapper.updateById(gate);
        log.info("门禁审核: id={}, result={}, by={}", id, pass ? "通过" : "驳回", gate.getAuditBy());
        return gate;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        get(id);
        gateMapper.deleteById(id);
    }

    private PhaseGate get(Long id) {
        PhaseGate gate = gateMapper.selectById(id);
        if (gate == null) {
            throw new BusinessException(404, "门禁不存在");
        }
        return gate;
    }
}
