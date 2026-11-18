package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.mapper.CustomerDictMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M5 出网脱敏器单测（铁律四核心）
 */
class WebDesensitizerTest {

    private CustomerDictMapper dictMapper;
    private WebDesensitizer desensitizer;

    @BeforeEach
    void setUp() {
        dictMapper = mock(CustomerDictMapper.class);
        when(dictMapper.selectAllNames()).thenReturn(List.of(
                "华信电力设备有限公司", "华信电力", "国网北京供电公司", "ABC集团"));
        desensitizer = new WebDesensitizer(dictMapper);
        desensitizer.init();
    }

    @Test
    void shouldMaskCustomerNameWithPlaceholder() {
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize("华信电力设备有限公司的项目进度如何");
        assertTrue(r.hitTypes().contains("CUSTOMER"));
        assertTrue(r.sanitized().contains("[客户"));
        assertFalse(r.sanitized().contains("华信电力设备有限公司"));
    }

    @Test
    void shouldPreferLongerCustomerName() {
        // 长名优先：不能被短名"华信电力"先行破坏
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize("华信电力设备有限公司合同评审");
        assertTrue(r.sanitized().contains("[客户"));
        assertFalse(r.sanitized().contains("有限公司"));
    }

    @Test
    void shouldUseSamePlaceholderForSameCustomer() {
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize("华信电力付款，华信电力开票");
        // 同名占位一致
        long count = r.sanitized().codePoints().filter(c -> c == '1').count();
        assertTrue(r.sanitized().contains("[客户1]"));
        assertFalse(r.sanitized().contains("华信电力"));
        assertTrue(count >= 2);
    }

    @Test
    void shouldMaskAmountMobileIdCardEmail() {
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize(
                "合同金额 350万元，联系人 13812345678，身份证 110101199001011234，邮箱 a.b@corp.com");
        assertTrue(r.hitTypes().contains("AMOUNT"));
        assertTrue(r.hitTypes().contains("MOBILE"));
        assertTrue(r.hitTypes().contains("ID_CARD"));
        assertTrue(r.hitTypes().contains("EMAIL"));
        assertFalse(r.sanitized().contains("350"));
        assertFalse(r.sanitized().contains("13812345678"));
        assertFalse(r.sanitized().contains("110101199001011234"));
        assertFalse(r.sanitized().contains("corp.com"));
    }

    @Test
    void shouldMaskBankCardNotShortNumbers() {
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize("卡号 6222020200112233445");
        assertTrue(r.hitTypes().contains("BANK_CARD"));
        assertFalse(r.sanitized().contains("6222020200112233445"));
        // 短数字（订单号等）不误伤
        WebDesensitizer.SanitizeResult r2 = desensitizer.sanitize("订单号 20240815");
        assertFalse(r2.hitTypes().contains("BANK_CARD"));
        assertTrue(r2.sanitized().contains("20240815"));
    }

    @Test
    void shouldReturnEmptyHitsWhenNoSensitive() {
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize("项目管理最佳实践有哪些");
        assertTrue(r.hitTypes().isEmpty());
        assertEquals("项目管理最佳实践有哪些", r.sanitized());
    }

    @Test
    void shouldHandleBlankInput() {
        assertEquals("", desensitizer.sanitize("").sanitized());
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize(null);
        assertEquals("", r.sanitized());
    }

    @Test
    void shouldKeepDictWhenDictSourceFails() {
        // 字典源异常时沿用已加载字典，脱敏不阻塞
        Mockito.doThrow(new RuntimeException("db down")).when(dictMapper).selectAllNames();
        desensitizer.reloadDictForTest();
        WebDesensitizer.SanitizeResult r = desensitizer.sanitize("华信电力项目");
        assertTrue(r.sanitized().contains("[客户"));
    }
}
