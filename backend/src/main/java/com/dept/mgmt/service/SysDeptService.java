package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dto.SysDeptDTO;
import com.dept.mgmt.entity.SysDept;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysDeptMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    /** 全部部门（平铺，前端组树） */
    public List<SysDept> listAll() {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysDept::getOrderNum);
        return sysDeptMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDeptDTO dto) {
        SysDept entity = new SysDept();
        apply(entity, dto);
        sysDeptMapper.insert(entity);
        entity.setAncestors(buildAncestors(entity.getParentId(), entity.getId()));
        sysDeptMapper.updateById(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SysDeptDTO dto) {
        SysDept exists = sysDeptMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "部门不存在");
        }
        if (dto.getParentId() != null && dto.getParentId().equals(id)) {
            throw new BusinessException(400, "父部门不能选择自己");
        }
        apply(exists, dto);
        exists.setAncestors(buildAncestors(exists.getParentId(), exists.getId()));
        sysDeptMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDept exists = sysDeptMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "部门不存在");
        }
        LambdaQueryWrapper<SysDept> child = new LambdaQueryWrapper<>();
        child.eq(SysDept::getParentId, id);
        if (sysDeptMapper.selectCount(child) > 0) {
            throw new BusinessException(409, "存在子部门，不支持删除");
        }
        LambdaQueryWrapper<SysUser> user = new LambdaQueryWrapper<>();
        user.eq(SysUser::getDeptId, id);
        if (sysUserMapper.selectCount(user) > 0) {
            throw new BusinessException(409, "部门下存在用户，不支持删除");
        }
        sysDeptMapper.deleteById(id);
    }

    private String buildAncestors(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return "0," + selfId;
        }
        SysDept parent = sysDeptMapper.selectById(parentId);
        String parentAncestors = parent != null && parent.getAncestors() != null ? parent.getAncestors() : String.valueOf(parentId);
        return parentAncestors + "," + selfId;
    }

    private void apply(SysDept entity, SysDeptDTO dto) {
        entity.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        entity.setDeptName(dto.getDeptName());
        entity.setOrderNum(dto.getOrderNum() == null ? 0 : dto.getOrderNum());
        entity.setLeader(dto.getLeader());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }
}
