package com.dept.mgmt.dingtalk.service;

import com.dept.mgmt.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 轻量对称加解密服务（AES-256-GCM，T-309 钉钉密钥存储）
 * 密钥由服务端配置派生（sha256(configKey)），仅用于钉钉 appSecret/验签密钥落库加密；
 * 接口层永不回显明文（脱敏为掩码）
 *
 * @author dept-mgmt
 */
@Component
public class CryptoService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${app.crypto.config-key:dept-mgmt-config-crypto-key-2026}") String configKey) {
        try {
            byte[] raw = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(configKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(raw, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("加密密钥初始化失败", e);
        }
    }

    /** 加密（输出格式：base64(iv || cipherText)） */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BusinessException(500, "密钥加密失败");
        }
    }

    /** 解密（解密失败返回 null，避免坏数据阻塞主流程） */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 脱敏掩码（前 4 后 2，中间星号） */
    public static String mask(String text) {
        if (text == null || text.length() <= 6) {
            return "******";
        }
        return text.substring(0, 4) + "******" + text.substring(text.length() - 2);
    }
}
