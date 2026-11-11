package com.dept.mgmt.dingtalk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dingtalk.entity.DingTalkConfig;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.DingTalkConfigMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.service.AuthService;
import com.dept.mgmt.dto.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉免登服务（T-309 统一入口）
 * 流程：钉钉端免登码 authCode → 换 dingtalkUserId → 匹配 sys_user.dingtalk_user_id → 签发 JWT
 * 未配置企业凭证时：dev-mode=true（显式开关）下 authCode 即 dingtalkUserId（沙箱联调）；
 * 否则 503 拒绝（生产红线：与微信登录同模式）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkAuthService {

    private final DingTalkConfigMapper configMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;

    /** 钉钉免登开发模式开关：默认关闭；仅本地开发联调可显式打开，生产环境严禁开启 */
    @Value("${dingtalk.dev-mode:false}")
    private boolean devMode;

    /** 业务码 4012 = 钉钉未绑定账号（前端引导走绑定流程） */
    public static final int CODE_NOT_BOUND = 4012;

    /**
     * 免登换 JWT：authCode → dingtalkUserId → 已绑定用户签发；未绑定抛 4012
     */
    public LoginVO login(String authCode) {
        String dingtalkUserId = resolveDingtalkUserId(authCode);
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDingtalkUserId, dingtalkUserId));
        if (user == null) {
            throw new BusinessException(CODE_NOT_BOUND, "该钉钉账号尚未绑定系统用户，请使用账号密码完成绑定");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        log.info("钉钉免登成功: {} dingtalkUserId={}", user.getUsername(), dingtalkUserId);
        return authService.issueTokenFor(user);
    }

    /**
     * 账号密码绑定钉钉（首次免登时引导；与微信绑定同模式）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO bind(String authCode, String username, String password) {
        String dingtalkUserId = resolveDingtalkUserId(authCode);
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        if (StringUtils.hasText(user.getDingtalkUserId()) && !user.getDingtalkUserId().equals(dingtalkUserId)) {
            throw new BusinessException(409, "该账号已绑定其他钉钉账号");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setDingtalkUserId(dingtalkUserId);
        sysUserMapper.updateById(update);
        log.info("钉钉绑定成功: {} dingtalkUserId={}", username, dingtalkUserId);
        return authService.issueTokenFor(user);
    }

    /**
     * 当前用户解绑钉钉
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setDingtalkUserId("");
        sysUserMapper.updateById(update);
        log.info("钉钉解绑成功: {}", user.getUsername());
    }

    /**
     * authCode 换 dingtalkUserId：
     * 已配置企业凭证 → 真实链路（access_token → oauth2 user 接口）；
     * 未配置且 dev-mode=true → 沙箱（authCode 即 dingtalkUserId）；
     * 未配置且 dev-mode=false → 503
     */
    private String resolveDingtalkUserId(String authCode) {
        if (!StringUtils.hasText(authCode)) {
            throw new BusinessException(400, "authCode 不能为空");
        }
        DingTalkConfig config = activeConfig();
        if (config == null || !StringUtils.hasText(cryptoService.decrypt(config.getAppSecretEnc()))) {
            if (devMode) {
                log.warn("钉钉免登开发模式已开启（dingtalk.dev-mode=true），authCode 直接作为 dingtalkUserId，严禁在生产环境使用");
                return authCode;
            }
            throw new BusinessException(503, "钉钉免登未配置：请配置企业应用凭证（appKey/appSecret）");
        }
        try {
            String appKey = config.getAppKey();
            String appSecret = cryptoService.decrypt(config.getAppSecretEnc());
            RestClient client = RestClient.create();
            // 1. 获取 access_token
            Map<String, Object> tokenResp = client.get()
                    .uri("https://api.dingtalk.com/v1.0/oauth2/accessToken?appKey={k}&appSecret={s}", appKey, appSecret)
                    .retrieve()
                    .body(Map.class);
            String accessToken = tokenResp == null ? null : (String) tokenResp.get("accessToken");
            if (!StringUtils.hasText(accessToken)) {
                throw new BusinessException(502, "钉钉 accessToken 获取失败");
            }
            // 2. 免登码换用户 userid
            Map<String, Object> userResp = client.get()
                    .uri("https://api.dingtalk.com/v1.0/oauth2/user/{code}", authCode)
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .retrieve()
                    .body(Map.class);
            String userId = userResp == null ? null : (String) userResp.get("userId");
            if (!StringUtils.hasText(userId)) {
                throw new BusinessException(502, "钉钉免登码校验失败");
            }
            return userId;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("钉钉免登调用失败", e);
            throw new BusinessException(502, "钉钉服务不可用: " + e.getClass().getSimpleName());
        }
    }

    // ===== 配置管理 =====

    public DingTalkConfig activeConfig() {
        return configMapper.selectOne(new LambdaQueryWrapper<DingTalkConfig>()
                .eq(DingTalkConfig::getStatus, 1)
                .orderByAsc(DingTalkConfig::getId).last("LIMIT 1"));
    }

    /** 配置脱敏视图（密钥永不回显明文） */
    public Map<String, Object> configMasked() {
        DingTalkConfig config = activeConfig();
        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = config != null
                && StringUtils.hasText(config.getAppKey())
                && StringUtils.hasText(cryptoService.decrypt(config.getAppSecretEnc()));
        out.put("configured", configured);
        out.put("corpId", config == null ? "" : config.getCorpId());
        out.put("appKey", config == null ? "" : config.getAppKey());
        out.put("appKeyMasked", config == null || !StringUtils.hasText(config.getAppKey())
                ? "" : CryptoService.mask(config.getAppKey()));
        out.put("auditSignKeyConfigured", config != null
                && StringUtils.hasText(cryptoService.decrypt(config.getAuditSignKeyEnc())));
        out.put("devMode", devMode);
        return out;
    }

    /**
     * 保存企业应用配置（部门领导；appSecret/验签密钥 AES-GCM 加密落库）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(String corpId, String appKey, String appSecret, String agentId, String auditSignKey) {
        DingTalkConfig config = activeConfig();
        if (config == null) {
            config = new DingTalkConfig();
            config.setStatus(1);
        }
        if (StringUtils.hasText(corpId)) {
            config.setCorpId(corpId);
        }
        if (StringUtils.hasText(appKey)) {
            config.setAppKey(appKey);
        }
        if (StringUtils.hasText(appSecret)) {
            config.setAppSecretEnc(cryptoService.encrypt(appSecret));
        }
        if (StringUtils.hasText(agentId)) {
            config.setAgentId(agentId);
        }
        if (StringUtils.hasText(auditSignKey)) {
            config.setAuditSignKeyEnc(cryptoService.encrypt(auditSignKey));
        }
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        log.info("钉钉企业应用配置已保存: corpId={}, appKey={}", config.getCorpId(), config.getAppKey());
    }

    /** 解密后的审批回调验签密钥（未配置返回 null → 回调拒绝） */
    public String auditSignKey() {
        DingTalkConfig config = activeConfig();
        return config == null ? null : cryptoService.decrypt(config.getAuditSignKeyEnc());
    }
}
