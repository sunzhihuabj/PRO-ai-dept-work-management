package com.dept.gateway.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AES-GCM 加解密测试
 *
 * @author dept-mgmt
 */
class AesGcmCipherTest {

    @Test
    void roundTrip() {
        AesGcmCipher cipher = new AesGcmCipher("test-secret-key-for-unit-test-32bytes!");
        String plain = "sk-abc123XYZ456defGHI789";
        String cipherText = cipher.encrypt(plain);
        assertNotEquals(plain, cipherText);
        assertEquals(plain, cipher.decrypt(cipherText));
    }

    @Test
    void differentIv_differentCipherText() {
        AesGcmCipher cipher = new AesGcmCipher("test-secret-key-for-unit-test-32bytes!");
        assertNotEquals(cipher.encrypt("same-key"), cipher.encrypt("same-key"), "随机 IV 应产生不同密文");
    }

    @Test
    void wrongSecret_decryptFails() {
        AesGcmCipher c1 = new AesGcmCipher("secret-one-32-bytes-long-enough!!!");
        AesGcmCipher c2 = new AesGcmCipher("secret-two-32-bytes-long-enough!!!");
        String cipherText = c1.encrypt("sk-my-api-key");
        assertThrows(Exception.class, () -> c2.decrypt(cipherText), "密钥不匹配应解密失败");
    }
}
