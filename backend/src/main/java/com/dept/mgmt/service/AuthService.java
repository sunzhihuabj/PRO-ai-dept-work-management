package com.dept.mgmt.service;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dto.LoginDTO;
import com.dept.mgmt.dto.LoginVO;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.JwtUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 认证服务
 * 处理登录、登出、密码修改、获取用户信息等
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${wechat.miniapp.appid:}")
    private String wxAppId;

    @Value("${wechat.miniapp.secret:}")
    private String wxAppSecret;

    /**
     * 登录
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        // 1. 根据用户名查询用户
        SysUser user = sysUserMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            log.warn("登录失败-用户不存在: {}", dto.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 校验密码（前端传输明文，后端用BCrypt比对密文）
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败-密码错误: {}", dto.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 校验账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        // 4-7. 签发 Token 并构建响应
        LoginVO vo = issueToken(user);
        String loginIp = getClientIp(request);
        sysUserMapper.updateLoginInfo(user.getId(), loginIp);
        log.info("用户登录成功: {} ({}), IP: {}", user.getUsername(), user.getRealName(), loginIp);
        return vo;
    }

    /**
     * 微信小程序授权登录
     * 业务码 4011 = 微信未绑定账号（前端引导走绑定流程）
     */
    public LoginVO wxLogin(String code) {
        String openid = resolveOpenid(code);
        SysUser user = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getWxOpenid, openid));
        if (user == null) {
            throw new BusinessException(4011, "该微信尚未绑定账号");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        LoginVO vo = issueToken(user);
        log.info("小程序授权登录成功: {} openid={}", user.getUsername(), openid);
        return vo;
    }

    /**
     * 账号密码绑定微信（首次授权登录时引导）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO wxBind(String code, String username, String password) {
        String openid = resolveOpenid(code);
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        if (StringUtils.hasText(user.getWxOpenid()) && !user.getWxOpenid().equals(openid)) {
            throw new BusinessException(409, "该账号已绑定其他微信");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setWxOpenid(openid);
        sysUserMapper.updateById(update);
        LoginVO vo = issueToken(user);
        log.info("微信绑定成功: {} openid={}", username, openid);
        return vo;
    }

    /**
     * code 换 openid；未配置 appid/secret 时进入开发模式（code 即 openid）
     */
    private String resolveOpenid(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(400, "code 不能为空");
        }
        if (!StringUtils.hasText(wxAppId) || !StringUtils.hasText(wxAppSecret)) {
            log.debug("微信开发模式：未配置 appid/secret，code 直接作为 openid");
            return "dev_" + code;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wxAppId
                    + "&secret=" + wxAppSecret + "&js_code=" + code + "&grant_type=authorization_code";
            ResponseEntity<com.fasterxml.jackson.databind.JsonNode> resp =
                    restTemplate.getForEntity(url, com.fasterxml.jackson.databind.JsonNode.class);
            com.fasterxml.jackson.databind.JsonNode body = resp.getBody();
            if (body == null || body.has("errcode") && body.get("errcode").asInt() != 0) {
                throw new BusinessException(502, "微信登录失败: " + (body != null ? body.toString() : "空响应"));
            }
            return body.get("openid").asText();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("jscode2session 调用失败", e);
            throw new BusinessException(502, "微信服务不可用: " + e.getMessage());
        }
    }

    /**
     * 签发 Token 并构建统一响应（登录/微信登录/绑定共用）
     */
    private LoginVO issueToken(SysUser user) {
        List<String> roleList = parseRoleCodes(user.getRoleCodes());
        String roles = String.join(",", roleList);
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRealName(), roles);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(jwtUtils.getExpiration());

        LoginVO.UserInfoVO userInfo = new LoginVO.UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setRoles(roleList);
        userInfo.setPermissions(roleList);
        vo.setUser(userInfo);
        return vo;
    }

    /**
     * 获取当前登录用户信息
     */
    public LoginVO.UserInfoVO getCurrentUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectByIdWithRoles(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        LoginVO.UserInfoVO info = new LoginVO.UserInfoVO();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setRoles(parseRoleCodes(user.getRoleCodes()));
        info.setPermissions(parseRoleCodes(user.getRoleCodes()));
        return info;
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectByIdWithRoles(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "旧密码错误");
        }

        if (newPassword.length() < 6) {
            throw new BusinessException(400, "新密码长度不能少于6位");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setPwdUpdateTime(java.time.LocalDateTime.now());
        sysUserMapper.updateById(update);
        log.info("用户修改密码成功: {}", user.getUsername());
    }

    /**
     * 解析角色编码JSON数组
     */
    private List<String> parseRoleCodes(String roleCodes) {
        if (!StringUtils.hasText(roleCodes)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(roleCodes, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析角色编码失败: {}, 原始值: {}", e.getMessage(), roleCodes);
            String clean = roleCodes.replaceAll("[\\[\\]\"]", "");
            List<String> result = new ArrayList<>();
            for (String r : clean.split(",")) {
                if (!r.isBlank()) {
                    result.add(r.trim());
                }
            }
            return result;
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
