package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户（含角色编码列表）
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据用户ID查询用户（含角色编码列表）
     */
    SysUser selectByIdWithRoles(@Param("id") Long id);

    /**
     * 更新最后登录信息
     */
    int updateLoginInfo(@Param("userId") Long userId, @Param("loginIp") String loginIp);
}
