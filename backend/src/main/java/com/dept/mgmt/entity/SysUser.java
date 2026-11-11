package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt加密） */
    @JsonIgnore
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 所属部门ID */
    private Long deptId;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像URL */
    private String avatar;

    /** 微信openid（小程序授权登录绑定） */
    private String wxOpenid;

    /** 钉钉userId（钉钉免登绑定，T-309） */
    private String dingtalkUserId;

    /** 角色编码列表（JSON数组） */
    private String roleCodes;

    /** 三身份（T-102）：EXPERT专家/WORKER工作者；ASSISTANT为AI虚拟身份不落人类账号 */
    private String identity;

    /** 状态（0停用 1正常） */
    private Integer status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 密码最后修改时间 */
    private LocalDateTime pwdUpdateTime;

    /**
     * 获取角色编码列表
     */
    @TableField(exist = false)
    private java.util.List<String> roleList;
}
