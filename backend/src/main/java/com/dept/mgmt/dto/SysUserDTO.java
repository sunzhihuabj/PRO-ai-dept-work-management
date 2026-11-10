package com.dept.mgmt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户DTO（创建/更新用）
 *
 * @author dept-mgmt
 */
@Data
public class SysUserDTO {

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空", groups = {Create.class})
    private String username;

    /** 密码（创建时可选，不填默认123456） */
    private String password;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 头像URL */
    private String avatar;

    /** 角色编码（逗号分隔或JSON数组） */
    private String roleCodes;

    /** 状态（0停用 1正常） */
    private Integer status;

    /** 创建校验组 */
    public interface Create {}
    /** 更新校验组 */
    public interface Update {}
}
