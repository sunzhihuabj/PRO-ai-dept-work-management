package com.dept.mgmt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 负责Token的生成、解析、校验
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class JwtUtils {

    /** 已随源码公开的旧默认密钥（轮换作废；生产环境检测到即拒绝启动） */
    private static final String LEGACY_DEFAULT_SECRET = "3V/8Fp0IsOdSAOqOpm6xFXZWDEu4xw8kasX737jXMH9p/JLZskFtAidjwOJfJIBj";

    /** 本地开发兜底密钥（轮换后新值；仅无 profile 本地开发使用，生产必须经 JWT_SECRET 注入） */
    private static final String DEV_FALLBACK_SECRET = "ZGVwdC1tZ210LWp3dC1kZXYtc2VjcmV0LXIyLTIwMjYxMTEzLXJvdGF0ZWQ9PQ==";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey key;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    /** 生产类 profile（prod/uat/release）判定：命中则密钥治理 fail-fast */
    private boolean isProdLikeProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "uat".equalsIgnoreCase(p) || "release".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    @PostConstruct
    public void init() {
        // 密钥治理（H4 整改）：源码默认密钥视为已泄露。
        // 1) 未配置/使用旧默认密钥时：生产类环境 fail-fast 拒绝启动；本地开发自动切换到轮换后的内置开发密钥；
        // 2) 显式配置的密钥：仅接受 Base64 编码且解码后不少于32字节，不再回退到原始字节，防止弱密钥上线
        String effective = secret;
        if (effective == null || effective.isBlank() || LEGACY_DEFAULT_SECRET.equals(effective.trim())) {
            if (isProdLikeProfile()) {
                throw new IllegalStateException("JWT 密钥治理 fail-fast：生产环境检测到"
                        + (effective == null || effective.isBlank() ? "未配置 JWT_SECRET" : "已泄露的旧默认密钥")
                        + "，必须通过环境变量 JWT_SECRET 注入强随机 Base64 密钥（≥32 字节）后启动");
            }
            effective = DEV_FALLBACK_SECRET;
            log.warn("未配置（或使用已泄露旧默认）JWT_SECRET，本地开发自动切换内置轮换开发密钥；生产环境必须显式注入 JWT_SECRET");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(effective);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT密钥配置无效：jwt.secret 必须是 Base64 编码字符串", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT密钥强度不足：jwt.secret 解码后为 "
                    + keyBytes.length + " 字节，要求不少于32字节，请通过环境变量 JWT_SECRET 注入强随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT密钥初始化完成，Token有效期: {}秒", expiration);
    }

    /**
     * 生成Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param realName 真实姓名
     * @param roles    角色编码列表
     * @return JWT Token
     */
    public String generateToken(Long userId, String username, String realName, String roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("realName", realName);
        claims.put("roles", roles);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从Token中获取真实姓名
     */
    public String getRealNameFromToken(String token) {
        return (String) parseToken(token).get("realName");
    }

    /**
     * 从Token中获取角色
     */
    public String getRolesFromToken(String token) {
        return (String) parseToken(token).get("roles");
    }

    /**
     * 校验Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("Token校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取Token过期时间（秒）
     */
    public long getExpiration() {
        return expiration;
    }
}
