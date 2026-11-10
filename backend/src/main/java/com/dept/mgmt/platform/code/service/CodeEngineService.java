package com.dept.mgmt.platform.code.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.CodeRuleMapper;
import com.dept.mgmt.platform.code.entity.CodeRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编码规则引擎 —— 全系统唯一发号入口
 *
 * <p>并发设计（两层防线）：
 * 1. JVM 级 per-rule 串行锁：消除"新维度首次并发 INSERT"的重复键死锁窗口（单进程部署有效）；
 * 2. InnoDB 行级 X 锁：同一 (rule_code, date_part) 维度取号在数据库层串行（多进程仍正确），
 *    死锁兜底自动重试最多 3 次。
 * <p>发号运行在独立短事务（REQUIRES_NEW）中：主业务事务回滚时号不回收（跳号容忍），不重号为第一优先级。
 * <p>多进程部署边界：JVM 锁失效后仅剩"首维度高并发"极端场景可能触发死锁重试，正确性由行锁+重试保证。
 *
 * <p>模板变量：{yyyy}/{yyyyMM}/{yyyyMMdd} 系统日期；{seq:n} 流水号左补零 n 位（超位自动扩容）；
 * {xxx} 调用方自定义参数，缺失直接报错。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeEngineService {

    /** 模板变量语法：{name} 或 {seq:n} */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)(?::(\\d+))?}");

    /** 死锁重试上限 */
    private static final int MAX_DEADLOCK_RETRY = 3;

    /** per-rule 串行锁（单进程内消除初始化竞态） */
    private final Map<String, Object> ruleLocks = new ConcurrentHashMap<>();

    private final CodeRuleMapper codeRuleMapper;
    private final CodeSeqTxService codeSeqTxService;

    /**
     * 取下一个编码（per-rule 串行 + 独立短事务取号 + 死锁重试）
     *
     * @param ruleCode 规则编码
     * @param params   模板自定义变量（可为 null）
     * @return 生成的编码，如 PRJ-2026-ZJC-001
     */
    public String nextCode(String ruleCode, Map<String, String> params) {
        Object lock = ruleLocks.computeIfAbsent(ruleCode, k -> new Object());
        synchronized (lock) {
            // 1. 规则校验（必须启用）
            CodeRule rule = codeRuleMapper.selectOne(new LambdaQueryWrapper<CodeRule>()
                    .eq(CodeRule::getRuleCode, ruleCode)
                    .eq(CodeRule::getStatus, 1));
            if (rule == null) {
                throw new BusinessException(400, "编码规则不存在或已停用: " + ruleCode);
            }

            // 2. 独立短事务原子取号（死锁自动重试）
            String datePart = resolveDatePart(rule.getDatePattern());
            long seq = nextSeqWithRetry(ruleCode, datePart);

            // 3. 超位数告警（自动扩位，不阻断）
            Integer digits = extractSeqDigits(rule.getPattern());
            if (digits != null && seq >= pow10(digits)) {
                log.warn("编码流水号超出{}位已自动扩位: ruleCode={}, seq={}", digits, ruleCode, seq);
            }

            // 4. 模板渲染（不依赖 DB，放事务外）
            return render(rule.getPattern(), seq, params);
        }
    }

    /** 独立短事务取号，InnoDB 死锁时自动重试 */
    private long nextSeqWithRetry(String ruleCode, String datePart) {
        for (int attempt = 1; ; attempt++) {
            try {
                return codeSeqTxService.nextSeq(ruleCode, datePart);
            } catch (DeadlockLoserDataAccessException e) {
                if (attempt >= MAX_DEADLOCK_RETRY) {
                    log.error("发号死锁重试{}次仍失败: ruleCode={}", MAX_DEADLOCK_RETRY, ruleCode, e);
                    throw new BusinessException(500, "编码发号繁忙，请稍后重试: " + ruleCode);
                }
                log.warn("发号死锁，自动重试: ruleCode={}, attempt={}", ruleCode, attempt);
            }
        }
    }

    /** 计算日期段值；datePattern 为空返回空串（永不重置） */
    private String resolveDatePart(String datePattern) {
        if (datePattern == null || datePattern.isBlank()) {
            return "";
        }
        return LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern));
    }

    /**
     * 模板渲染：{seq:n} → 流水号；{yyyy*} → 系统日期；{xxx} → params 取值
     */
    private String render(String pattern, long seq, Map<String, String> params) {
        LocalDate today = LocalDate.now();
        Map<String, String> vars = new HashMap<>(8);
        vars.put("yyyy", String.valueOf(today.getYear()));
        vars.put("yyyyMM", today.format(DateTimeFormatter.ofPattern("yyyyMM")));
        vars.put("yyyyMMdd", today.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        if (params != null) {
            vars.putAll(params);
        }

        Matcher m = VAR_PATTERN.matcher(pattern);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String digits = m.group(2);
            String value;
            if ("seq".equals(name)) {
                int d = digits == null ? 1 : Integer.parseInt(digits);
                // %0Nd 对超位数流水自然扩位（999→"999"，1000→"1000"）
                value = String.format("%0" + d + "d", seq);
            } else {
                value = vars.get(name);
                if (value == null) {
                    throw new BusinessException(400, "编码参数缺失: " + name + "（规则模板含 {" + name + "}）");
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 从模板中提取 {seq:n} 的位数（无则返回 null） */
    private Integer extractSeqDigits(String pattern) {
        Matcher m = VAR_PATTERN.matcher(pattern);
        while (m.find()) {
            if ("seq".equals(m.group(1)) && m.group(2) != null) {
                return Integer.parseInt(m.group(2));
            }
        }
        return null;
    }

    private long pow10(int n) {
        long r = 1;
        for (int i = 0; i < n; i++) {
            r *= 10;
        }
        return r;
    }
}
