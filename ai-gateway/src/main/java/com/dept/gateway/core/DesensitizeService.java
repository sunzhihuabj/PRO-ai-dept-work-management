package com.dept.gateway.core;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 出网脱敏服务：正则规则替换敏感信息（仅对商用 OPENAI_COMPAT 通道强制）
 * 覆盖：手机号 / 身份证号 / 银行卡号；规则后续可扩展为配置表
 *
 * @author dept-mgmt
 */
@Component
public class DesensitizeService {

    /** 手机号：1 开头 11 位（容忍分隔符 - 空格） */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9][\\d\\s-]{9,14}(?!\\d)");

    /** 身份证：18 位（含末位 X） */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");

    /** 银行卡：13~19 位数字 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");

    /**
     * 脱敏提示词：先身份证/手机号等强模式，再兜底长数字串（银行卡）
     */
    public String desensitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = ID_CARD.matcher(text).replaceAll("**身份证**");
        result = MOBILE.matcher(result).replaceAll("**手机号**");
        result = BANK_CARD.matcher(result).replaceAll("**卡号**");
        return result;
    }
}
