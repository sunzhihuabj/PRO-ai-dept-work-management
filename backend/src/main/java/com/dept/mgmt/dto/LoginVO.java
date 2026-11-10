package com.dept.mgmt.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应VO
 *
 * @author dept-mgmt
 */
@Data
public class LoginVO {

    /** 访问令牌 */
    private String token;

    /** Token类型 */
    private String tokenType = "Bearer";

    /** 过期时间（秒） */
    private Long expiresIn;

    /** 用户信息 */
    private UserInfoVO user;

    /**
     * 用户信息
     */
    @Data
    public static class UserInfoVO {
        private Long id;
        private String username;
        private String realName;
        private String avatar;
        private String email;
        private String phone;
        private List<String> roles;
        private List<String> permissions;
    }
}
