package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 *
 * @author dept-mgmt
 */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文，前端可MD5后传输） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 记住我（7天内免登录） */
    private Boolean rememberMe = false;
}
