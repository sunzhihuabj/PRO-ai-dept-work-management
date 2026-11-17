package com.dept.mgmt.agent.collab.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 审批参数 HMAC-SHA256 工具（阶段一 HITL 护栏）
 *
 * <p>审批通过执行前，服务端以相同密钥重算摘要比对，防止建议参数在审批间隙被篡改
 * （对齐业界 HITL 审批流 "approval payload HMAC 锁定" 模式）。
 *
 * @author dept-mgmt
 */
public final class HmacUtils {

    private HmacUtils() {
    }

    /** HMAC-SHA256 摘要（hex 小写） */
    public static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /** 摘要比对（常数时间比较，防时序侧信道） */
    public static boolean verify(String secret, String data, String expectedHex) {
        if (expectedHex == null || expectedHex.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(sign(secret, data).getBytes(StandardCharsets.UTF_8),
                expectedHex.trim().getBytes(StandardCharsets.UTF_8));
    }
}
