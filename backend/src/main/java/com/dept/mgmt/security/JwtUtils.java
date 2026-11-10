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

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // 密钥治理：仅接受 Base64 编码且解码后不少于32字节的密钥，不再回退到原始字节，防止弱密钥上线
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
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
