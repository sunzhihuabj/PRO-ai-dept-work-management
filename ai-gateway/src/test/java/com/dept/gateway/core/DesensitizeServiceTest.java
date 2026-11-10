package com.dept.gateway.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出网脱敏测试
 *
 * @author dept-mgmt
 */
class DesensitizeServiceTest {

    private final DesensitizeService service = new DesensitizeService();

    @Test
    void mobile_masked() {
        String out = service.desensitize("联系人张三，电话 13812345678，请尽快联系。");
        assertFalse(out.contains("13812345678"));
        assertTrue(out.contains("**手机号**"));
    }

    @Test
    void idCard_masked() {
        String out = service.desensitize("身份证号：110101199001011234，请核对。");
        assertFalse(out.contains("110101199001011234"));
        assertTrue(out.contains("**身份证**"));
    }

    @Test
    void bankCard_masked() {
        String out = service.desensitize("收款账号 6222021234567890123 请查收。");
        assertFalse(out.contains("6222021234567890123"));
        assertTrue(out.contains("**卡号**"));
    }

    @Test
    void normalText_untouched() {
        String text = "本项目合同金额 1280.00 万元，工期 180 天，验收标准按 GB50254 执行。";
        assertEquals(text, service.desensitize(text), "正常业务文本不应被误伤");
    }

    @Test
    void nullAndEmpty_safe() {
        assertNull(service.desensitize(null));
        assertEquals("", service.desensitize(""));
    }
}
