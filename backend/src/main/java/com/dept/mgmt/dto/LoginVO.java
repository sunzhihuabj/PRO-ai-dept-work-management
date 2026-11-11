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
        /** 精细权限点（T-404：MENU/BUTTON/API 三级权限编码，实时生效） */
        private List<String> finePermissions;
        /** 是否启用精细控制（false=未配置任何维度，行为与纯角色一致） */
        private Boolean finePermEnabled;
        /** 全局权限版本号（前端轮询比对） */
        private Long permVersion;
    }
}
