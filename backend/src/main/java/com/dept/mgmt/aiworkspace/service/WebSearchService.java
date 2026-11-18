package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.OutboundLog;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.OutboundLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网检索服务（V2.0 M5 · 铁律四脱敏 proxy 唯一出网口）。
 *
 * <p>链路：原始检索词 → {@link WebDesensitizer} 脱敏（客户名/金额/证件号不出网）
 * → 外部搜索引擎（Bing 国内主源 → DuckDuckGo Lite 备源）→ 结果结构化
 * → {@code outbound_log} 留痕（原始词仅存 SHA-256 摘要，绝不落库原文）。
 * 任何绕过本服务直接出网检索的实现均违反铁律四。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class WebSearchService {

    private static final String PURPOSE = "WEB_SEARCH";
    private static final int MAX_RESULTS = 8;
    private static final int MAX_QUERY_LEN = 200;

    /** Bing 结果解析：<li class="b_algo">…<h2><a href="URL">TITLE</a></h2>…snippet */
    private static final Pattern BING_RESULT = Pattern.compile(
            "<li class=\"b_algo\"[^>]*>.*?<h2[^>]*><a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a></h2>(.*?)</li>",
            Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    /** DuckDuckGo Lite 结果解析：<a rel="nofollow" href="URL" class='result-link'>TITLE</a> */
    private static final Pattern DDG_RESULT = Pattern.compile(
            "<a[^>]+href=\"(https?://[^\"]+)\"[^>]*class='result-link'[^>]*>(.*?)</a>", Pattern.DOTALL);

    private final WebDesensitizer webDesensitizer;
    private final OutboundLogMapper outboundLogMapper;
    private final RestClient restClient;

    public WebSearchService(WebDesensitizer webDesensitizer, OutboundLogMapper outboundLogMapper) {
        this.webDesensitizer = webDesensitizer;
        this.outboundLogMapper = outboundLogMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(12));
        f.setOutputStreaming(true);
        this.restClient = RestClient.builder()
                .requestFactory(f)
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .build();
    }

    /** 单条检索结果 */
    public record WebResult(String title, String url, String snippet) {
    }

    /**
     * 联网检索（脱敏 + 留痕）。
     *
     * @param rawQuery 原始检索词（敏感内容允许出现，但不出网）
     * @param userId   发起用户（留痕）
     * @return 检索上下文（原始词/脱敏词/命中类型/结果列表）
     */
    public Map<String, Object> search(String rawQuery, Long userId) {
        if (!StringUtils.hasText(rawQuery)) {
            throw new BusinessException(400, "检索词不能为空");
        }
        String query = rawQuery.trim();
        if (query.length() > MAX_QUERY_LEN) {
            query = query.substring(0, MAX_QUERY_LEN);
        }
        long start = System.currentTimeMillis();
        String digest = sha256_16(query);

        // 铁律四：脱敏后才可出网
        WebDesensitizer.SanitizeResult sr = webDesensitizer.sanitize(query);
        String sanitized = sr.sanitized();

        String host = "";
        List<WebResult> results = new ArrayList<>();
        String status = "FAILED";
        String error = null;
        try {
            // 主源：Bing 国内
            host = "cn.bing.com";
            try {
                results = fetchBing(sanitized);
            } catch (Exception e) {
                log.info("Bing 检索失败，切 DuckDuckGo: {}", e.getMessage());
            }
            // 备源：DuckDuckGo Lite
            if (results.isEmpty()) {
                host = "lite.duckduckgo.com";
                results = fetchDuckDuckGo(sanitized);
            }
            if (results.isEmpty()) {
                error = "两个检索源均无结果";
            } else {
                status = "SUCCESS";
            }
        } catch (Exception e) {
            error = abbreviate(e.getMessage(), 200);
            log.warn("联网检索失败: queryDigest={}, host={}", digest, host, e);
        }

        long cost = System.currentTimeMillis() - start;
        auditLog(userId, host, purpose(), digest, sanitized, sr.hitTypes(), results.size(), status, error, cost);

        if (!"SUCCESS".equals(status)) {
            throw new BusinessException(503, "联网检索暂不可用（" + (error == null ? "网络异常" : error) + "），请稍后重试");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("sanitized", sanitized);
        out.put("hitTypes", sr.hitTypes());
        out.put("results", results);
        out.put("durationMs", cost);
        return out;
    }

    private static String purpose() {
        return PURPOSE;
    }

    private List<WebResult> fetchBing(String sanitized) throws Exception {
        String html = restClient.get()
                .uri("https://cn.bing.com/search?q=" + urlEncode(sanitized) + "&count=10&setlang=zh-CN")
                .retrieve()
                .body(String.class);
        List<WebResult> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        Matcher m = BING_RESULT.matcher(html);
        while (m.find() && out.size() < MAX_RESULTS) {
            String url = decodeHtml(m.group(1));
            String title = stripTags(m.group(2));
            String rest = m.group(3);
            String snippet = extractSnippet(rest);
            if (StringUtils.hasText(title) && StringUtils.hasText(url)) {
                out.add(new WebResult(title, url, snippet));
            }
        }
        return out;
    }

    private List<WebResult> fetchDuckDuckGo(String sanitized) throws Exception {
        String html = restClient.get()
                .uri("https://lite.duckduckgo.com/lite/?q=" + urlEncode(sanitized))
                .retrieve()
                .body(String.class);
        List<WebResult> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        Matcher m = DDG_RESULT.matcher(html);
        while (m.find() && out.size() < MAX_RESULTS) {
            String url = decodeHtml(m.group(1));
            String title = stripTags(m.group(2));
            if (StringUtils.hasText(title) && StringUtils.hasText(url) && !url.contains("duckduckgo.com")) {
                out.add(new WebResult(title, url, ""));
            }
        }
        return out;
    }

    /** 结果页 snippet 提取：取 h2 之后第一个 <p> 内容 */
    private String extractSnippet(String segment) {
        Matcher p = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL).matcher(segment);
        if (p.find()) {
            return abbreviate(stripTags(p.group(1)), 160);
        }
        return "";
    }

    private void auditLog(Long userId, String host, String purpose, String digest, String sanitized,
                          List<String> hitTypes, int resultCount, String status, String error, long costMs) {
        try {
            OutboundLog logRow = new OutboundLog();
            logRow.setUserId(userId);
            logRow.setTargetHost(host);
            logRow.setPurpose(purpose);
            logRow.setQueryDigest(digest);
            logRow.setSanitizedQuery(abbreviate(sanitized, 300));
            logRow.setHitTypes(String.join(",", hitTypes));
            logRow.setResultCount(resultCount);
            logRow.setStatus(status);
            logRow.setErrorBrief(error);
            logRow.setCostMs(costMs);
            logRow.setCreateTime(LocalDateTime.now());
            outboundLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("outbound_log 留痕失败（不阻塞检索）: {}", e.getMessage());
        }
    }

    // ==================== 工具 ====================

    static String sha256_16(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String stripTags(String html) {
        return TAG.matcher(html).replaceAll("").trim();
    }

    private static String decodeHtml(String s) {
        return s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** 留痕查询（管理端排查用） */
    public List<OutboundLog> recentLogs(int limit) {
        return outboundLogMapper.selectList(new LambdaQueryWrapper<OutboundLog>()
                .orderByDesc(OutboundLog::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }
}
