package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.mapper.CustomerDictMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 出网脱敏器（V2.0 铁律四）：任何将要发往外部网络（联网检索等）的文本，
 * 必须先经本脱敏器——客户名称（主数据字典）、金额、手机号、身份证、银行卡、邮箱
 * 一律替换为占位符；原始敏感文本不出网。
 *
 * <p>脱敏结果携带命中类型，供 outbound_log 留痕审计。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebDesensitizer {

    /** 客户名字典刷新间隔（惰性刷新） */
    private static final long DICT_REFRESH_MS = 5 * 60 * 1000L;
    /** 字典中参与匹配的最小名称长度（避免单字别名误伤） */
    private static final int MIN_NAME_LEN = 3;

    private final CustomerDictMapper customerDictMapper;

    // 模式脱敏（顺序：长模式优先，避免短模式破坏长模式；证件号要求独立数字串边界，防止吃掉更长的卡号）
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)|(?<!\\d)\\d{15}(?!\\d)");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern AMOUNT = Pattern.compile(
            "([¥￥]\\s*\\d+([\\.,]\\d+)?)|(\\d+([\\.,]\\d+)?\\s*(万\\s*元|亿\\s*元|万元|亿元|万|亿|元))|((USD|EUR|CNY|RMB)\\s*\\d+([\\.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    /** 客户名字典（长度降序，避免短名先命中破坏长名） */
    private volatile List<String> customerNames = List.of();
    private volatile long dictLoadedAt = 0;

    /** 脱敏结果 */
    public record SanitizeResult(String sanitized, List<String> hitTypes) {
        public boolean hasHit() {
            return !hitTypes.isEmpty();
        }
    }

    @PostConstruct
    public void init() {
        reloadDict();
    }

    /** 惰性刷新客户名字典（5 分钟一次；失败不阻塞，用旧字典） */
    private void ensureDict() {
        if (System.currentTimeMillis() - dictLoadedAt > DICT_REFRESH_MS) {
            reloadDict();
        }
    }

    private synchronized void reloadDict() {
        try {
            List<String> names = new ArrayList<>(customerDictMapper.selectAllNames());
            names.removeIf(n -> n == null || n.length() < MIN_NAME_LEN);
            names.sort((a, b) -> b.length() - a.length());
            this.customerNames = List.copyOf(names);
            this.dictLoadedAt = System.currentTimeMillis();
            log.info("脱敏客户字典已加载: {} 个名称", names.size());
        } catch (Exception e) {
            log.warn("脱敏客户字典加载失败（沿用旧字典 {} 个）: {}", customerNames.size(), e.getMessage());
        }
    }

    /** 供测试显式触发字典重载（生产路径走 ensureDict 惰性刷新） */
    public void reloadDictForTest() {
        reloadDict();
    }

    /**
     * 对将要出网的文本做脱敏。
     * 顺序：身份证 → 手机 → 邮箱 → 金额 → 银行卡（长数字段在证件/手机替换后）→ 客户名（长名优先）。
     */
    public SanitizeResult sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return new SanitizeResult(text == null ? "" : text, List.of());
        }
        ensureDict();
        List<String> hits = new ArrayList<>();
        String out = text;

        out = replaceAll(out, ID_CARD, "[证件号]", "ID_CARD", hits);
        out = replaceAll(out, MOBILE, "[手机号]", "MOBILE", hits);
        out = replaceAll(out, EMAIL, "[邮箱]", "EMAIL", hits);
        out = replaceAll(out, AMOUNT, "[金额]", "AMOUNT", hits);
        out = replaceAll(out, BANK_CARD, "[卡号]", "BANK_CARD", hits);

        // 客户名占位（同名统一占位号，保持语义可读）
        Map<String, String> placeholderByCustomer = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger(1);
        for (String name : customerNames) {
            if (out.contains(name)) {
                String ph = placeholderByCustomer.computeIfAbsent(name, k -> "[客户" + seq.getAndIncrement() + "]");
                out = out.replace(name, ph);
                if (!hits.contains("CUSTOMER")) {
                    hits.add("CUSTOMER");
                }
            }
        }
        return new SanitizeResult(out, List.copyOf(hits));
    }

    private String replaceAll(String input, Pattern p, String placeholder, String hitType, List<String> hits) {
        var m = p.matcher(input);
        if (!m.find()) {
            return input;
        }
        hits.add(hitType);
        return m.reset().replaceAll(placeholder);
    }
}
