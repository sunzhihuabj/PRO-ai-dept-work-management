package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.LoginDTO;
import com.dept.mgmt.dto.LoginVO;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录、登出、用户信息、修改密码")
public class AuthController {

    private final AuthService authService;

    /**
     * 登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        log.info("用户登录请求: username={}", dto.getUsername());
        LoginVO vo = authService.login(dto, request);
        return R.ok("登录成功", vo);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public R<Void> logout() {
        // JWT无状态，前端清除Token即可；如需服务端失效可加入黑名单
        LoginUser loginUser = LoginUser.current();
        if (loginUser != null) {
            log.info("用户登出: {}", loginUser.getUsername());
        }
        return R.ok("登出成功", null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前登录用户信息")
    public R<LoginVO.UserInfoVO> info() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        LoginVO.UserInfoVO info = authService.getCurrentUserInfo(loginUser.getUserId());
        return R.ok(info);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        authService.changePassword(loginUser.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return R.ok("密码修改成功", null);
    }

    /**
     * 小程序微信授权登录（code 换 token；未绑定返回业务码 4011）
     */
    @PostMapping("/wx-login")
    @Operation(summary = "微信小程序授权登录")
    public R<LoginVO> wxLogin(@RequestBody WxLoginDTO dto) {
        return R.ok(authService.wxLogin(dto.getCode()));
    }

    /**
     * 账号密码绑定微信（首次授权登录时调用）
     */
    @PostMapping("/wx-bind")
    @Operation(summary = "账号绑定微信")
    @com.dept.mgmt.common.annotation.OperLog(title = "认证", businessType = 2)
    public R<LoginVO> wxBind(@RequestBody WxBindDTO dto) {
        return R.ok("绑定成功", authService.wxBind(dto.getCode(), dto.getUsername(), dto.getPassword()));
    }

    /**
     * 微信登录DTO
     */
    @Data
    public static class WxLoginDTO {
        @NotBlank(message = "code不能为空")
        private String code;
    }

    /**
     * 微信绑定DTO
     */
    @Data
    public static class WxBindDTO {
        @NotBlank(message = "code不能为空")
        private String code;

        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    /**
     * 修改密码DTO
     */
    @Data
    public static class ChangePasswordDTO {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        private String newPassword;

        @NotBlank(message = "确认密码不能为空")
        private String confirmPassword;
    }
}
