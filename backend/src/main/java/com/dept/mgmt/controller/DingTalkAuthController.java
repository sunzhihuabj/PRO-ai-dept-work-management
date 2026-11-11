package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dingtalk.service.DingTalkAuthService;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.dto.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 钉钉免登接口（T-309 统一入口）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/dingtalk/auth")
@RequiredArgsConstructor
@Tag(name = "钉钉免登", description = "免登换 JWT / 账号绑定 / 企业应用配置（T-309）")
public class DingTalkAuthController {

    private final DingTalkAuthService dingTalkAuthService;

    @PostMapping("/login")
    @Operation(summary = "钉钉免登（authCode 换 JWT；未绑定返回 4012 引导绑定）")
    public R<LoginVO> login(@RequestBody Map<String, String> body) {
        return R.ok("免登成功", dingTalkAuthService.login(body.get("authCode")));
    }

    @PostMapping("/bind")
    @Operation(summary = "账号密码绑定钉钉（首次免登引导）")
    public R<LoginVO> bind(@RequestBody Map<String, String> body) {
        return R.ok("绑定成功", dingTalkAuthService.bind(
                body.get("authCode"), body.get("username"), body.get("password")));
    }

    @PostMapping("/unbind")
    @Operation(summary = "当前用户解绑钉钉")
    public R<Void> unbind() {
        LoginUser user = LoginUser.current();
        dingTalkAuthService.unbind(user.getUserId());
        return R.ok("已解绑", null);
    }

    @GetMapping("/config")
    @Operation(summary = "查询企业应用配置（密钥脱敏，永不回显明文）")
    public R<Map<String, Object>> config() {
        return R.ok(dingTalkAuthService.configMasked());
    }

    @PostMapping("/config")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "保存企业应用配置（appSecret/验签密钥 AES-GCM 加密落库）")
    public R<Map<String, Object>> saveConfig(@RequestBody Map<String, String> body) {
        dingTalkAuthService.saveConfig(
                body.get("corpId"), body.get("appKey"), body.get("appSecret"),
                body.get("agentId"), body.get("auditSignKey"));
        return R.ok("配置已保存", dingTalkAuthService.configMasked());
    }
}
