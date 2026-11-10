package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.dto.SysUserDTO;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户服务
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    /**
     * 用户列表分页查询
     */
    public PageResult<SysUser> list(Integer pageNum, Integer pageSize, String username, String realName, Integer status) {
        Page<SysUser> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SysUser::getUsername, username);
        wrapper.like(StringUtils.hasText(realName), SysUser::getRealName, realName);
        wrapper.eq(status != null, SysUser::getStatus, status);
        wrapper.orderByDesc(SysUser::getCreateTime);
        sysUserMapper.selectPage(page, wrapper);
        page.getRecords().forEach(u -> u.setPassword(null));
        return PageResult.of(page);
    }

    /**
     * 用户详情
     */
    public SysUser detail(Long id) {
        SysUser user = sysUserMapper.selectByIdWithRoles(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    /**
     * 创建用户
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUserDTO dto) {
        SysUser existing = sysUserMapper.selectByUsername(dto.getUsername());
        if (existing != null) {
            throw new BusinessException(409, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        String pwd = StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : "123456";
        user.setPassword(passwordEncoder.encode(pwd));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setRoleCodes(toJsonArray(dto.getRoleCodes()));

        sysUserMapper.insert(user);
        log.info("创建用户成功: {} ({})", user.getUsername(), user.getRealName());
        return user.getId();
    }

    /**
     * 更新用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SysUserDTO dto) {
        SysUser existing = sysUserMapper.selectByIdWithRoles(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }

        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        user.setStatus(dto.getStatus());
        if (StringUtils.hasText(dto.getRoleCodes())) {
            user.setRoleCodes(toJsonArray(dto.getRoleCodes()));
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            user.setPwdUpdateTime(java.time.LocalDateTime.now());
        }

        sysUserMapper.updateById(user);
        log.info("更新用户成功: id={}", id);
    }

    /**
     * 删除用户（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser existing = sysUserMapper.selectByIdWithRoles(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }
        sysUserMapper.deleteById(id);
        log.info("删除用户成功: id={}", id);
    }

    /**
     * 重置密码（新密码由部门领导提供，禁止固定值；响应不回显）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException(400, "新密码长度不能少于8位");
        }
        if (!newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            throw new BusinessException(400, "新密码必须同时包含字母和数字");
        }
        SysUser existing = sysUserMapper.selectByIdWithRoles(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPwdUpdateTime(java.time.LocalDateTime.now());
        sysUserMapper.updateById(user);
        log.info("重置用户密码成功: id={}", id);
    }

    /**
     * 获取所有启用用户（下拉选择用）
     */
    public List<SysUser> listAllEnabled() {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getStatus, 1);
        wrapper.orderByAsc(SysUser::getRealName);
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        users.forEach(u -> {
            u.setPassword(null);
            u.setRoleCodes(null);
        });
        return users;
    }

    /**
     * 将逗号分隔字符串转为JSON数组字符串
     */
    private String toJsonArray(String roleCodes) {
        if (!StringUtils.hasText(roleCodes)) {
            return null;
        }
        if (roleCodes.trim().startsWith("[")) {
            return roleCodes;
        }
        try {
            List<String> list = List.of(roleCodes.split(","));
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return roleCodes;
        }
    }
}
