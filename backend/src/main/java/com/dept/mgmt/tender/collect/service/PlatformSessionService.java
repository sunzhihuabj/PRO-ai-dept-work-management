package com.dept.mgmt.tender.collect.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dingtalk.service.CryptoService;
import com.dept.mgmt.mapper.TenderPlatformSessionMapper;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import com.dept.mgmt.tender.collect.entity.TenderPlatformSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Random;

/**
 * 平台登录会话服务（AGT-27，R2 平台自动登录）
 *
 * <p>支持三种认证方式：
 * <ul>
 *   <li>NONE —— 免登录公开平台</li>
 *   <li>FORM —— 账号密码表单登录：模拟真实用户操作（拟人化随机间隔），可选验证码 AI 识别
 *       （验证码图片经网关多模态通道识别；通道不支持时明确报错并引导改用 COOKIE 模式，
 *       绝不重试轰炸验证码接口）</li>
 *   <li>COOKIE —— 会话复用：用户在界面粘贴浏览器 Cookie，加密存储并定期校验有效性</li>
 * </ul>
 *
 * <p>安全与反爬策略：凭据 AES-256-GCM 加密落库（复用 T-309 CryptoService），
 * Cookie 明文仅存在于单次请求内存中；请求间隔 = 平台配置 interval + 随机抖动；
 * 连续校验失败 ≥3 次标记 BLOCKED（疑似封禁）并停止对该平台的自动访问。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSessionService {

    /** 默认桌面 Chrome UA（平台未配置时使用） */
    static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** 会话连续失败阈值：达到即 BLOCKED（疑似封禁），停止自动访问 */
    private static final int BLOCK_THRESHOLD = 3;

    private final TenderPlatformSessionMapper sessionMapper;
    private final CryptoService cryptoService;
    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    // ===== 会话获取（采集入口调用） =====

    /**
     * 获取平台可用会话（Cookie 串）。优先复用有效会话；失效/缺失则按认证方式重新登录。
     *
     * @return Cookie 串（NONE 模式返回 null）
     */
    public String ensureSession(TenderPlatform platform) {
        if ("NONE".equalsIgnoreCase(platform.getAuthType())) {
            return null;
        }
        TenderPlatformSession session = loadSession(platform.getId());
        if (session != null && "ACTIVE".equals(session.getStatus()) && notBlank(session.getCookiesCipher())) {
            String cookie = cryptoService.decrypt(session.getCookiesCipher());
            if (validateCookie(platform, cookie)) {
                touchSession(session, "COOKIE_OK");
                return cookie;
            }
            log.info("平台 {} 存量会话已失效，重新登录", platform.getPlatformName());
        }
        return login(platform, session);
    }

    /** 手动登录测试（R1 平台管理"测试登录"按钮） */
    public Map<String, Object> testLogin(TenderPlatform platform) {
        String cookie = login(platform, loadSession(platform.getId()));
        boolean ok;
        if ("NONE".equalsIgnoreCase(platform.getAuthType())) {
            ok = probeOk(platform, null);
        } else {
            ok = cookie != null && validateCookie(platform, cookie);
        }
        return Map.of("success", ok, "authType", platform.getAuthType());
    }

    // ===== 登录实现 =====

    private String login(TenderPlatform platform, TenderPlatformSession session) {
        String authType = platform.getAuthType() == null ? "NONE" : platform.getAuthType().toUpperCase();
        String cookie = switch (authType) {
            case "COOKIE" -> loginByCookie(platform);
            case "FORM" -> loginByForm(platform);
            case "NONE" -> null;
            default -> throw new BusinessException(400, "不支持的认证方式: " + authType);
        };
        saveSession(platform, session, cookie, authType);
        return cookie;
    }

    /** COOKIE 模式：直接复用配置中的会话串 */
    private String loginByCookie(TenderPlatform platform) {
        Map<String, Object> auth = readAuthParams(platform);
        String cookie = str(auth.get("cookie"));
        if (cookie == null || cookie.isBlank()) {
            throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」未配置 Cookie，请在采集规则中粘贴浏览器会话 Cookie");
        }
        humanDelay(platform, "登录前置间隔");
        if (!validateCookie(platform, cookie)) {
            throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」Cookie 校验失败（已失效或被风控），请重新粘贴");
        }
        return cookie.trim();
    }

    /** FORM 模式：账号密码表单登录（拟人间隔 + 可选验证码 AI 识别） */
    private String loginByForm(TenderPlatform platform) {
        Map<String, Object> auth = readAuthParams(platform);
        String username = str(auth.get("username"));
        String password = str(auth.get("password"));
        if (username == null || password == null) {
            throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」未配置登录账号或密码");
        }
        Map<String, Object> loginRule = loginRule(platform);
        String loginUrl = platform.getLoginUrl();
        if (loginUrl == null || loginUrl.isBlank()) {
            throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」未配置登录入口 URL");
        }

        // 1. 访问登录页（获取初始 Cookie，模拟用户先打开页面）
        humanDelay(platform, "打开登录页");
        HttpResponse page = get(loginUrl, null, platform);
        String pageBody = body(page);
        String pageCookie = cookieHeader(page);

        // 2. 验证码 AI 识别（可选；单次尝试不重试，避免轰炸验证码接口）
        String captcha = null;
        String captchaField = str(loginRule.get("captchaField"));
        if (notBlank(captchaField)) {
            captcha = recognizeCaptcha(platform, pageBody, pageCookie, str(loginRule.get("captchaImgSrc")));
        }

        // 3. 拟人间隔后提交表单（模拟用户填写耗时）
        humanDelay(platform, "填写表单");
        Map<String, Object> form = new java.util.LinkedHashMap<>();
        Object hidden = loginRule.get("hiddenFields");
        if (hidden instanceof Map<?, ?> hm) {
            hm.forEach((k, v) -> form.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        }
        form.put(strOr(loginRule.get("usernameField"), "username"), username);
        form.put(strOr(loginRule.get("passwordField"), "password"), password);
        if (captcha != null) {
            form.put(captchaField, captcha);
        }
        HttpResponse resp;
        try {
            resp = HttpRequest.post(loginUrl)
                    .header("User-Agent", userAgent(platform))
                    .header("Referer", loginUrl)
                    .header("Cookie", pageCookie == null ? "" : pageCookie)
                    .form(form)
                    .setConnectionTimeout(5000)
                    .setReadTimeout(15000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException(502, "平台「" + platform.getPlatformName() + "」登录请求失败: " + rootMessage(e));
        }
        String body = body(resp);
        String cookie = mergeCookies(pageCookie, resp);

        // 4. 登录结果判定（successKeyword / failKeyword）
        String failKeyword = str(loginRule.get("failKeyword"));
        String successKeyword = str(loginRule.get("successKeyword"));
        if (notBlank(failKeyword) && body.contains(failKeyword)) {
            throw new BusinessException(401, "平台「" + platform.getPlatformName() + "」登录失败（命中失败标识: " + failKeyword + "），请检查账号配置");
        }
        if (notBlank(successKeyword) && !body.contains(successKeyword)) {
            throw new BusinessException(401, "平台「" + platform.getPlatformName() + "」登录结果校验未通过（未命中成功标识），请检查账号配置或改用 COOKIE 模式");
        }
        log.info("平台 {} FORM 登录成功", platform.getPlatformName());
        return cookie;
    }

    /**
     * 验证码识别：抓取验证码图片 → base64 → 网关多模态通道识别（单次尝试）。
     * 通道不支持图片识别时抛出明确错误，引导使用 COOKIE 会话复用模式。
     */
    private String recognizeCaptcha(TenderPlatform platform, String pageBody, String cookie, String imgSrc) {
        if (imgSrc == null || imgSrc.isBlank()) {
            throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」配置了验证码字段但未配置验证码图片地址 captchaImgSrc");
        }
        String captchaUrl = imgSrc.startsWith("http") ? imgSrc : resolveUrl(platform.getLoginUrl(), imgSrc);
        humanDelay(platform, "验证码查看");
        HttpResponse resp = get(captchaUrl, cookie, platform);
        if (resp.getStatus() != 200) {
            throw new BusinessException(502, "验证码图片获取失败: HTTP " + resp.getStatus());
        }
        String base64 = Base64.getEncoder().encodeToString(resp.bodyBytes());
        String prompt = "这是一张验证码图片（base64 PNG/JPG）：data:image/png;base64," + base64
                + "\n请直接输出图中的字符（通常为4-5位字母数字），不要输出任何其他内容。";
        try {
            String answer = aiGatewayClient.chat("AGT-27", prompt, 1L, null).trim();
            if (answer.length() >= 3 && answer.length() <= 6 && answer.matches("[A-Za-z0-9]+")) {
                log.info("平台 {} 验证码 AI 识别成功（长度 {}）", platform.getPlatformName(), answer.length());
                return answer;
            }
            throw new BusinessException(502, "验证码识别结果异常（" + answer + "），请改用 COOKIE 会话复用模式");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "验证码 AI 识别不可用（当前模型通道不支持多模态），请改用 COOKIE 会话复用模式");
        }
    }

    // ===== 会话校验与存储 =====

    /** 会话有效性校验：列表页可访问且（如配置）命中登录成功标识 */
    private boolean validateCookie(TenderPlatform platform, String cookie) {
        try {
            humanDelay(platform, "会话校验");
            HttpResponse resp = get(platform.getListUrl().replace("{page}", "1"), cookie, platform);
            if (resp.getStatus() != 200) {
                return false;
            }
            String successKeyword = str(loginRule(platform).get("successKeyword"));
            return !notBlank(successKeyword) || body(resp).contains(successKeyword);
        } catch (Exception e) {
            log.warn("平台 {} 会话校验异常: {}", platform.getPlatformName(), rootMessage(e));
            return false;
        }
    }

    private TenderPlatformSession loadSession(Long platformId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<TenderPlatformSession>()
                .eq(TenderPlatformSession::getPlatformId, platformId));
    }

    private void saveSession(TenderPlatform platform, TenderPlatformSession session, String cookie, String loginType) {
        LocalDateTime now = LocalDateTime.now();
        if (session == null) {
            session = new TenderPlatformSession();
            session.setPlatformId(platform.getId());
        }
        session.setCookiesCipher(cookie == null ? null : cryptoService.encrypt(cookie));
        session.setLoginType(loginType);
        session.setStatus("ACTIVE");
        session.setLastLoginAt(now);
        session.setFailCount(0);
        session.setRemark(loginType + " 登录成功");
        if (session.getId() == null) {
            sessionMapper.insert(session);
        } else {
            sessionMapper.updateById(session);
        }
    }

    /** 校验失败：连续失败计数，达阈值标记 BLOCKED（疑似封禁，停止自动访问） */
    private void touchSession(TenderPlatformSession session, String okType) {
        session.setStatus("ACTIVE");
        session.setFailCount(0);
        session.setLastLoginAt(LocalDateTime.now());
        session.setRemark(okType);
        sessionMapper.updateById(session);
    }

    public void markSessionFailure(TenderPlatform platform) {
        TenderPlatformSession session = loadSession(platform.getId());
        if (session == null) {
            return;
        }
        int fails = (session.getFailCount() == null ? 0 : session.getFailCount()) + 1;
        session.setFailCount(fails);
        session.setStatus(fails >= BLOCK_THRESHOLD ? "BLOCKED" : "EXPIRED");
        session.setLastLoginAt(LocalDateTime.now());
        session.setRemark("连续校验失败 " + fails + " 次" + (fails >= BLOCK_THRESHOLD ? "（疑似封禁，已停止自动访问，请人工处理）" : ""));
        sessionMapper.updateById(session);
    }

    // ===== HTTP 基础设施（限频 + 重试 + 反爬头） =====

    /** 拟人化延迟：interval + [0, jitter) 随机抖动 */
    public void humanDelay(TenderPlatform platform, String phase) {
        int interval = platform.getRequestIntervalMs() == null ? 1500 : platform.getRequestIntervalMs();
        int jitter = platform.getJitterMs() == null ? 800 : platform.getJitterMs();
        long sleep = interval + (jitter <= 0 ? 0 : random.nextInt(jitter));
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "采集被中断");
        }
        log.debug("AGT-27 拟人延迟 {}ms（{}）", sleep, phase);
    }

    /** GET（含重试：3 次指数退避），返回最终响应 */
    public HttpResponse getWithRetry(String url, String cookie, TenderPlatform platform) {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse resp = get(url, cookie, platform);
                if (resp.getStatus() >= 500) {
                    throw new IllegalStateException("HTTP " + resp.getStatus());
                }
                return resp;
            } catch (Exception e) {
                last = e;
                log.warn("AGT-27 请求失败（第 {} 次）: {} - {}", attempt, url, rootMessage(e));
                if (attempt < 3) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new BusinessException(502, "平台访问失败（重试 3 次均异常）: " + rootMessage(last));
    }

    private HttpResponse get(String url, String cookie, TenderPlatform platform) {
        try {
            return HttpRequest.get(url)
                    .header("User-Agent", userAgent(platform))
                    .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", platform.getLoginUrl() == null ? url : platform.getLoginUrl())
                    .header("Cookie", cookie == null ? "" : cookie)
                    .setConnectionTimeout(5000)
                    .setReadTimeout(15000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException(502, "网络请求失败: " + rootMessage(e));
        }
    }

    private String userAgent(TenderPlatform platform) {
        try {
            String ua = str(objectMapper.readTree(platform.getCollectRules()).path("userAgent").asText(null));
            return notBlank(ua) ? ua : DEFAULT_UA;
        } catch (Exception e) {
            return DEFAULT_UA;
        }
    }

    private String cookieHeader(HttpResponse resp) {
        return resp.getCookies().stream()
                .map(c -> c.getName() + "=" + c.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);
    }

    /** 合并登录页与登录响应的 Cookie（登录响应覆盖同名键） */
    private String mergeCookies(String pageCookie, HttpResponse resp) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (pageCookie != null) {
            for (String pair : pageCookie.split(";\\s*")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    map.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
        }
        resp.getCookies().forEach(c -> map.put(c.getName(), c.getValue()));
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);
    }

    // ===== 工具 =====

    @SuppressWarnings("unchecked")
    public Map<String, Object> readAuthParams(TenderPlatform platform) {
        if (platform.getAuthParamsCipher() == null || platform.getAuthParamsCipher().isBlank()) {
            return Map.of();
        }
        try {
            String plain = cryptoService.decrypt(platform.getAuthParamsCipher());
            return plain == null || plain.isBlank() ? Map.of() : objectMapper.readValue(plain, Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "平台凭据解密失败，请重新保存认证配置");
        }
    }

    private Map<String, Object> loginRule(TenderPlatform platform) {
        try {
            var node = objectMapper.readTree(platform.getCollectRules() == null ? "{}" : platform.getCollectRules());
            var login = node.path("login");
            return objectMapper.convertValue(login, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean probeOk(TenderPlatform platform, String cookie) {
        try {
            HttpResponse resp = get(platform.getListUrl().replace("{page}", "1"), cookie, platform);
            return resp.getStatus() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 相对 URL 解析 */
    public String resolveUrl(String base, String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        link = link.trim();
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link;
        }
        try {
            return new java.net.URL(new java.net.URL(base), link).toString();
        } catch (Exception e) {
            return link;
        }
    }

    private String body(HttpResponse resp) {
        try {
            return resp.body() == null ? "" : new String(resp.bodyBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String strOr(Object value, String dft) {
        return value == null || String.valueOf(value).isBlank() ? dft : String.valueOf(value);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
