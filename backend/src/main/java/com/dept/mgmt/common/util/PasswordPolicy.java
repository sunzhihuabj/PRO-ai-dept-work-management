package com.dept.mgmt.common.util;

import com.dept.mgmt.common.exception.BusinessException;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 密码策略（M5 弱密码治理：复杂度校验 + 随机初始密码生成）
 *
 * <p>统一规则：长度 ≥8 位，且同时包含字母与数字（与既有 resetPassword 规则对齐）。
 * 常见弱口令黑名单直接拒绝。
 *
 * @author dept-mgmt
 */
public final class PasswordPolicy {

    /** 弱口令黑名单（命中即拒，含 seed/历史默认） */
    private static final java.util.Set<String> WEAK_PASSWORDS = java.util.Set.of(
            "12345678", "123456789", "password", "password1", "qwerty123", "abc12345", "11111111", "00000000");

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    /** 随机初始密码字符集（去除易混淆字符 0/O/1/l/I） */
    private static final String GEN_CHARS = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度（不合法抛 BusinessException）
     */
    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(400, "密码长度不能少于8位");
        }
        if (!HAS_LETTER.matcher(password).matches() || !HAS_DIGIT.matcher(password).matches()) {
            throw new BusinessException(400, "密码必须同时包含字母和数字");
        }
        if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
            throw new BusinessException(400, "密码过于简单（常见弱口令），请更换");
        }
    }

    /**
     * 生成 12 位随机初始密码（含字母和数字，去除易混淆字符），创建用户未显式指定密码时使用
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(GEN_CHARS.charAt(RANDOM.nextInt(GEN_CHARS.length())));
        }
        // 保证满足字母+数字组合
        if (!HAS_DIGIT.matcher(sb).matches()) {
            sb.setCharAt(0, GEN_CHARS.charAt(RANDOM.nextInt(8)));
        }
        return sb.toString();
    }

    /**
     * 是否为已知初始弱密码（登录时检测存量账号是否仍使用默认口令）
     */
    public static boolean isKnownInitialWeak(String rawPassword) {
        return "123456".equals(rawPassword);
    }
}
