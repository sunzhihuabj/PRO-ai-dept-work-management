package com.dept.mgmt.tender.collect.service;

import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.TenderCollectRunMapper;
import com.dept.mgmt.mapper.TenderNoticeMapper;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.dept.mgmt.tender.collect.entity.TenderCollectRun;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import com.dept.mgmt.tender.entity.TenderNotice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标讯联网采集编排服务（AGT-27，R3 采集 + R5 登记 + R6 监控告警）
 *
 * <p>单平台采集流水线：
 * <pre>登录会话 → 列表页遍历（HTML 选择器 / JSON_API 路径，分页 {page} 占位或 next 选择器）
 *   → 每条：断点去重 → 详情页全量拉取 → LLM 提取 9 字段 → 确定性校验（缺失二次核验）
 *   → 去重登记 TN 台账（source_url 唯一键）→ 运行指标实时落库</pre>
 *
 * <p>治理策略：平台级互斥锁（防并发双采）；请求拟人间隔 + 3 次指数退避重试；
 * 断点续采（checkpoint：lastPage + doneUrls）；异常现场快照（URL+状态码+响应摘要）
 * 留存运行表；登录失败/运行失败即时站内信告警管理员。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderCollectService {

    /** 快照摘要长度（异常现场留存，防超大响应撑爆存储） */
    private static final int SNAPSHOT_LEN = 2000;
    /** 断点 doneUrls 上限（防 checkpoint 无限膨胀） */
    private static final int CHECKPOINT_URLS_MAX = 500;
    private static final Pattern PAGE_PATTERN = Pattern.compile("\\{page}");

    /** 平台级互斥锁（防同一平台并发双采） */
    private final Map<Long, Object> platformLocks = new ConcurrentHashMap<>();

    private final TenderPlatformService platformService;
    private final PlatformSessionService sessionService;
    private final TenderExtractService extractService;
    private final com.dept.mgmt.tender.service.TenderService tenderService;
    private final TenderCollectRunMapper runMapper;
    private final TenderNoticeMapper noticeMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // ===== 入口 =====

    /**
     * 执行一次联网采集（同步）。同一平台并发调用直接抛错。
     *
     * @param resume true=从上次断点续采
     */
    public TenderCollectRun runCollect(Long platformId, Long userId, String triggerType, boolean resume) {
        TenderPlatform platform = platformService.mustExist(platformId);
        if (platform.getEnabled() == null || platform.getEnabled() != 1) {
            throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」已停用，无法采集");
        }
        Object lock = platformLocks.computeIfAbsent(platformId, k -> new Object());
        synchronized (lock) {
            try {
                return doRun(platform, userId, triggerType, resume);
            } finally {
                platformLocks.remove(platformId);
            }
        }
    }

    public TenderCollectRun getRun(Long runId) {
        TenderCollectRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "采集运行记录不存在: " + runId);
        }
        return run;
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<TenderCollectRun> pageRuns(
            int pageNum, int pageSize, Long platformId, String status) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TenderCollectRun> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TenderCollectRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(platformId != null, TenderCollectRun::getPlatformId, platformId);
        wrapper.eq(status != null && !status.isBlank(), TenderCollectRun::getStatus, status);
        wrapper.orderByDesc(TenderCollectRun::getCreateTime);
        runMapper.selectPage(page, wrapper);
        return page;
    }

    // ===== 主流程 =====

    private TenderCollectRun doRun(TenderPlatform platform, Long userId, String triggerType, boolean resume) {
        TenderCollectRun run = new TenderCollectRun();
        run.setPlatformId(platform.getId());
        run.setPlatformName(platform.getPlatformName());
        run.setTriggerType(triggerType);
        run.setTriggerUserId(userId);
        run.setStatus("RUNNING");
        run.setPagesFetched(0);
        run.setItemsFound(0);
        run.setItemsCollected(0);
        run.setExtractOk(0);
        run.setExtractRepaired(0);
        run.setExtractFailed(0);
        run.setRegisterOk(0);
        run.setRegisterDup(0);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        JsonNode rules = readRules(platform);
        Checkpoint cp = resume ? loadCheckpoint(platform.getId()) : new Checkpoint();
        try {
            // 1. 登录会话
            String cookie;
            try {
                cookie = sessionService.ensureSession(platform);
                run.setLoginStatus("NONE".equalsIgnoreCase(platform.getAuthType()) ? "NONE"
                        : "COOKIE".equalsIgnoreCase(platform.getAuthType()) ? "COOKIE_OK" : "FORM_OK");
            } catch (BusinessException e) {
                run.setLoginStatus("FAILED");
                run.setLastErrorType("LOGIN_FAILED");
                run.setErrorSnapshot(snapshot(e.getMessage()));
                sessionService.markSessionFailure(platform);
                finish(run, "FAILED");
                alert(platform, run, "登录失败", e.getMessage());
                throw new BusinessException(502, "平台登录失败: " + e.getMessage());
            }

            // 2. 列表遍历 + 逐条流水线
            int maxPages = platform.getMaxPages() == null ? 3 : platform.getMaxPages();
            String mode = rules.path("mode").asText("HTML");
            for (int p = cp.lastPage; p <= maxPages; p++) {
                List<ListItem> items = fetchListPage(platform, cookie, rules, mode, p);
                run.setPagesFetched(p);
                run.setItemsFound(run.getItemsFound() + items.size());
                runMapper.updateById(run);
                if (items.isEmpty()) {
                    break; // 翻页到头
                }
                int before = cp.doneUrls.size();
                for (ListItem item : items) {
                    if (cp.doneUrls.contains(item.sourceUrl)) {
                        continue;
                    }
                    processItem(platform, cookie, rules, run, item);
                    cp.doneUrls.add(item.sourceUrl);
                    cp.lastPage = p;
                    saveCheckpoint(run, cp);
                }
                // 整页无新增（断点已追平 / 单页配置重复内容）→ 按增量采集语义翻页结束
                if (cp.doneUrls.size() == before) {
                    break;
                }
            }
            // 3. 收尾状态
            String status = run.getExtractFailed() > 0 || run.getItemsCollected() != run.getRegisterOk() + run.getRegisterDup()
                    ? "PARTIAL" : "SUCCESS";
            finish(run, status);
            notifySummary(platform, run);
            log.info("AGT-27 采集完成: platform={} status={} pages={} found={} ok={} dup={} extractFailed={}",
                    platform.getPlatformName(), status, run.getPagesFetched(), run.getItemsFound(),
                    run.getRegisterOk(), run.getRegisterDup(), run.getExtractFailed());
            return run;
        } catch (BusinessException e) {
            if (!"FAILED".equals(run.getStatus())) {
                run.setLastErrorType("NETWORK");
                run.setErrorSnapshot(snapshot(e.getMessage()));
                finish(run, "FAILED");
                alert(platform, run, "采集运行异常", e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            run.setLastErrorType("NETWORK");
            run.setErrorSnapshot(snapshot(e.getClass().getSimpleName() + ": " + e.getMessage()));
            finish(run, "FAILED");
            alert(platform, run, "采集运行异常", e.getMessage());
            throw new BusinessException(500, "采集运行失败: " + e.getMessage());
        }
    }

    /** 单条流水线：去重前置 → 详情拉取 → 提取校验 → 登记 */
    private void processItem(TenderPlatform platform, String cookie, JsonNode rules,
                             TenderCollectRun run, ListItem item) {
        // 0. 去重前置（source_url 已登记的直接跳过，省去详情外呼）
        if (item.sourceUrl != null) {
            Long dup = noticeMapper.selectCount(new LambdaQueryWrapper<TenderNotice>()
                    .eq(TenderNotice::getSourceUrl, item.sourceUrl));
            if (dup != null && dup > 0) {
                run.setRegisterDup(run.getRegisterDup() + 1);
                runMapper.updateById(run);
                return;
            }
        }

        // 1. 详情全量拉取
        if (item.sourceUrl == null || item.sourceUrl.isBlank()) {
            run.setLastErrorType("NETWORK");
            run.setErrorSnapshot(snapshot("列表项无详情链接: " + item.title));
            runMapper.updateById(run);
            return;
        }
        String rawText;
        try {
            sessionService.humanDelay(platform, "详情页间隔 " + item.sourceUrl);
            HttpResponse detail = sessionService.getWithRetry(item.sourceUrl, cookie, platform);
            if (detail.getStatus() != 200) {
                recordNetworkError(run, item.sourceUrl, detail.getStatus());
                return;
            }
            String html = detail.body();
            rawText = extractDetailContent(rules, html);
            if (rawText == null || rawText.isBlank()) {
                recordNetworkError(run, item.sourceUrl, 200);
                return;
            }
            run.setItemsCollected(run.getItemsCollected() + 1);
        } catch (BusinessException e) {
            recordNetworkError(run, item.sourceUrl, 0);
            return;
        }

        // 2. LLM 提取 + 校验 + 二次核验
        TenderExtractService.ExtractResult extracted;
        try {
            extracted = extractService.extract(rawText, run.getTriggerUserId());
        } catch (Exception e) {
            run.setExtractFailed(run.getExtractFailed() + 1);
            run.setLastErrorType("EXTRACT");
            run.setErrorSnapshot(snapshot("提取异常 " + item.sourceUrl + ": " + e.getMessage()));
            runMapper.updateById(run);
            return;
        }
        if (extracted.getFields().get("project_name") == null) {
            run.setExtractFailed(run.getExtractFailed() + 1);
            run.setLastErrorType("EXTRACT");
            run.setErrorSnapshot(snapshot("标题提取失败 " + item.sourceUrl));
            runMapper.updateById(run);
            return;
        }
        if ("PASSED".equals(extracted.getVerifyStatus())) {
            run.setExtractOk(run.getExtractOk() + 1);
        } else if ("REPAIRED".equals(extracted.getVerifyStatus())) {
            run.setExtractRepaired(run.getExtractRepaired() + 1);
        } else {
            run.setExtractFailed(run.getExtractFailed() + 1);
        }

        // 3. 去重兜底（详情内容期 URL 可能变化） + 登记
        if (item.sourceUrl != null) {
            Long dup = noticeMapper.selectCount(new LambdaQueryWrapper<TenderNotice>()
                    .eq(TenderNotice::getSourceUrl, item.sourceUrl));
            if (dup != null && dup > 0) {
                run.setRegisterDup(run.getRegisterDup() + 1);
                runMapper.updateById(run);
                return;
            }
        }
        try {
            register(platform, run, item, rawText, extracted);
            run.setRegisterOk(run.getRegisterOk() + 1);
        } catch (Exception e) {
            run.setLastErrorType("REGISTER");
            run.setErrorSnapshot(snapshot("登记失败 " + item.sourceUrl + ": " + e.getMessage()));
            log.warn("AGT-27 标讯登记失败: {} - {}", item.sourceUrl, e.getMessage());
        }
        runMapper.updateById(run);
    }

    /** 登记：TN 编码 + 溯源字段 + 校验状态 */
    private void register(TenderPlatform platform, TenderCollectRun run, ListItem item,
                          String rawText, TenderExtractService.ExtractResult extracted) {
        Map<String, String> f = extracted.getFields();
        BigDecimal budget = extracted.getBudget() == null ? null : new BigDecimal(extracted.getBudget());
        LocalDate deadline = parseDate(f.get("deadline"));
        Long tenderId = tenderService.createTender(
                f.get("project_name"),
                platform.getPlatformName(),
                budget,
                null,
                f.get("qualifications"),
                deadline,
                item.publishDate != null ? item.publishDate : LocalDate.now(),
                "AGENT_COLLECT",
                rawText);
        // 回填 AGT-27 扩展字段（溯源 + 提取明细）
        TenderNotice notice = new TenderNotice();
        notice.setId(tenderId);
        notice.setSourceUrl(item.sourceUrl);
        notice.setCollectRunId(run.getId());
        notice.setTenderCode(f.get("tender_code"));
        notice.setPurchaser(f.get("purchaser"));
        notice.setOpenTime(f.get("open_time"));
        notice.setContactInfo(f.get("contacts"));
        notice.setBidRequirement(f.get("requirements"));
        notice.setVerifyStatus(extracted.getVerifyStatus());
        noticeMapper.updateById(notice);
        log.info("AGT-27 标讯已登记: tenderId={} title={} verify={}", tenderId, f.get("project_name"), extracted.getVerifyStatus());
    }

    // ===== 列表页解析 =====

    private List<ListItem> fetchListPage(TenderPlatform platform, String cookie, JsonNode rules,
                                         String mode, int page) {
        String url = buildListUrl(platform, rules, page);
        sessionService.humanDelay(platform, "列表页间隔 p" + page);
        HttpResponse resp = sessionService.getWithRetry(url, cookie, platform);
        if (resp.getStatus() != 200) {
            throw new BusinessException(502, "列表页访问失败（HTTP " + resp.getStatus() + "）: " + url);
        }
        JsonNode listRule = rules.path("list");
        List<ListItem> items = "JSON_API".equals(mode)
                ? parseJsonList(platform, resp.body(), listRule, url)
                : parseHtmlList(platform, resp.body(), listRule, url);
        // JSON 分页到头判定由 items.isEmpty 兜底；HTML 同
        return items;
    }

    private String buildListUrl(TenderPlatform platform, JsonNode rules, int page) {
        String url = platform.getListUrl();
        Matcher m = PAGE_PATTERN.matcher(url);
        if (m.find()) {
            return url.replace("{page}", String.valueOf(page));
        }
        String pageParam = rules.path("list").path("pageParam").asText(null);
        if (pageParam == null || pageParam.isBlank()) {
            return url; // 单页配置：反复抓同一页（配合 doneUrls 去重仍可跑全量）
        }
        return url + (url.contains("?") ? "&" : "?") + pageParam + "=" + page;
    }

    private List<ListItem> parseHtmlList(TenderPlatform platform, String html, JsonNode listRule, String pageUrl) {
        List<ListItem> items = new ArrayList<>();
        org.jsoup.nodes.Document doc = Jsoup.parse(html, pageUrl);
        Elements rows = doc.select(listRule.path("itemSelector").asText());
        for (Element row : rows) {
            Element titleEl = row.selectFirst(listRule.path("titleSelector").asText());
            if (titleEl == null) {
                continue;
            }
            String title = titleEl.text().trim();
            String link = titleEl.hasAttr("href") ? titleEl.absUrl("href") : "";
            if ((link == null || link.isBlank()) && notBlankStr(listRule.path("linkSelector").asText(null))) {
                Element linkEl = row.selectFirst(listRule.path("linkSelector").asText());
                if (linkEl != null) {
                    link = linkEl.absUrl("href");
                }
            }
            if (title.isBlank()) {
                continue;
            }
            ListItem item = new ListItem();
            item.title = title;
            item.sourceUrl = resolveDetailUrl(platform, link, pageUrl);
            Element dateEl = notBlankStr(listRule.path("dateSelector").asText(null)) ? row.selectFirst(listRule.path("dateSelector").asText()) : null;
            item.publishDate = dateEl == null ? null : parseDate(extractDate(dateEl.text()));
            items.add(item);
        }
        return items;
    }

    private List<ListItem> parseJsonList(TenderPlatform platform, String body, JsonNode listRule, String pageUrl) {
        List<ListItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode arr = root;
            for (String seg : listRule.path("itemsPath").asText("data").split("\\.")) {
                arr = arr.path(seg);
            }
            if (!arr.isArray()) {
                return items;
            }
            String titleField = listRule.path("titleField").asText("title");
            String linkField = listRule.path("linkField").asText(null);
            String dateField = listRule.path("dateField").asText(null);
            for (JsonNode node : arr) {
                String title = node.path(titleField).asText(null);
                if (title == null || title.isBlank()) {
                    continue;
                }
                ListItem item = new ListItem();
                item.title = title.trim();
                String link = linkField == null ? null : node.path(linkField).asText(null);
                item.sourceUrl = resolveDetailUrl(platform, link, pageUrl);
                item.publishDate = dateField == null ? null : parseDate(extractDate(node.path(dateField).asText(null)));
                items.add(item);
            }
        } catch (Exception e) {
            throw new BusinessException(502, "JSON 列表解析失败: " + e.getMessage());
        }
        return items;
    }

    private String extractDetailContent(JsonNode rules, String html) {
        JsonNode detail = rules.path("detail");
        // JSON_API 详情：按 contentField 路径取正文字段
        String contentField = detail.path("contentField").asText(null);
        if (contentField != null && !contentField.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(html);
                for (String seg : contentField.split("\\.")) {
                    node = node.path(seg);
                }
                return node.isMissingNode() ? null : node.asText();
            } catch (Exception e) {
                return null;
            }
        }
        String selector = detail.path("contentSelector").asText(null);
        if (selector == null || selector.isBlank()) {
            // 未配置选择器：退化为全页可读文本
            return Jsoup.parse(html).text();
        }
        Element el = Jsoup.parse(html).selectFirst(selector);
        return el == null ? null : el.text();
    }

    /**
     * 详情 URL 解析：绝对链接直用；相对链接优先拼 detailUrlPrefix，未配置时回退列表页 URL 解析
     */
    private String resolveDetailUrl(TenderPlatform platform, String link, String base) {
        if (link == null || link.isBlank()) {
            return null;
        }
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link;
        }
        if (notBlankStr(platform.getDetailUrlPrefix())) {
            return sessionService.resolveUrl(platform.getDetailUrlPrefix(), link);
        }
        return sessionService.resolveUrl(base == null ? platform.getListUrl() : base, link);
    }

    // ===== 检查点 / 监控 / 告警 =====

    private static class Checkpoint {
        int lastPage = 1;
        Set<String> doneUrls = new HashSet<>();
    }

    private Checkpoint loadCheckpoint(Long platformId) {
        // 断点续采语义：取该平台最近一次带检查点的非 RUNNING 运行
        TenderCollectRun last = runMapper.selectList(new LambdaQueryWrapper<TenderCollectRun>()
                        .eq(TenderCollectRun::getPlatformId, platformId)
                        .isNotNull(TenderCollectRun::getCheckpoint)
                        .orderByDesc(TenderCollectRun::getId).last("LIMIT 1")).stream()
                .filter(r -> !"RUNNING".equals(r.getStatus()))
                .findFirst().orElse(null);
        if (last == null || last.getCheckpoint() == null) {
            return new Checkpoint();
        }
        try {
            JsonNode node = objectMapper.readTree(last.getCheckpoint());
            Checkpoint cp = new Checkpoint();
            cp.lastPage = node.path("lastPage").asInt(1);
            JsonNode urls = node.path("doneUrls");
            if (urls.isArray()) {
                urls.forEach(u -> cp.doneUrls.add(u.asText()));
            }
            return cp;
        } catch (Exception e) {
            return new Checkpoint();
        }
    }

    private void saveCheckpoint(TenderCollectRun run, Checkpoint cp) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("lastPage", cp.lastPage);
            ArrayNode urls = node.putArray("doneUrls");
            cp.doneUrls.stream().skip(Math.max(0, cp.doneUrls.size() - CHECKPOINT_URLS_MAX)).forEach(urls::add);
            run.setCheckpoint(objectMapper.writeValueAsString(node));
            runMapper.updateById(run);
        } catch (Exception e) {
            log.warn("检查点保存失败: {}", e.getMessage());
        }
    }

    private void recordNetworkError(TenderCollectRun run, String url, int status) {
        run.setLastErrorType("NETWORK");
        run.setErrorSnapshot(snapshot("详情拉取失败 HTTP=" + status + " URL=" + url));
        runMapper.updateById(run);
        log.warn("AGT-27 详情拉取失败: HTTP={} {}", status, url);
    }

    private void finish(TenderCollectRun run, String status) {
        run.setStatus(status);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    /** 站内信告警（R6：登录失败/运行失败即时通知管理员） */
    private void alert(TenderPlatform platform, TenderCollectRun run, String title, String detail) {
        try {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTemplateCode("GENERIC_REMIND");
            dto.setParams(Map.of(
                    "title", "【标讯采集告警】" + title,
                    "content", "平台「" + platform.getPlatformName() + "」运行 #" + run.getId() + " "
                            + title + "：" + detail + "。异常现场快照已留存于采集运行记录。"));
            dto.setReceiverIds(List.of(1L));
            dto.setRelatedId(run.getId());
            NotificationSendResult result = notificationService.send(dto);
            log.info("AGT-27 告警已发送: run={} sent={}", run.getId(), result.getSent());
        } catch (Exception e) {
            log.warn("AGT-27 告警发送失败（不阻塞主流程）: {}", e.getMessage());
        }
    }

    /** 运行完成回调通知（R5：登记结果通知） */
    private void notifySummary(TenderPlatform platform, TenderCollectRun run) {
        try {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTemplateCode("GENERIC_REMIND");
            dto.setParams(Map.of(
                    "title", "标讯采集完成",
                    "content", "平台「" + platform.getPlatformName() + "」运行 #" + run.getId()
                            + " 状态 " + run.getStatus() + "：发现 " + run.getItemsFound()
                            + " 条，登记 " + run.getRegisterOk() + " 条，去重 " + run.getRegisterDup()
                            + " 条，待人工核验 " + run.getExtractFailed() + " 条。可在投标管理中查询筛选。"));
            dto.setReceiverIds(List.of(run.getTriggerUserId() == null ? 1L : run.getTriggerUserId()));
            dto.setRelatedId(run.getId());
            notificationService.send(dto);
        } catch (Exception e) {
            log.warn("AGT-27 完成通知发送失败（不阻塞主流程）: {}", e.getMessage());
        }
    }

    private String snapshot(String message) {
        String text = message == null ? "" : message;
        return text.length() <= SNAPSHOT_LEN ? text : text.substring(0, SNAPSHOT_LEN);
    }

    private JsonNode readRules(TenderPlatform platform) {
        try {
            return objectMapper.readTree(platform.getCollectRules() == null ? "{}" : platform.getCollectRules());
        } catch (Exception e) {
            throw new BusinessException(400, "平台采集规则配置非法 JSON");
        }
    }

    private String extractDate(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}").matcher(text);
        if (m.find()) {
            String[] parts = m.group().split("-");
            return String.format("%s-%02d-%02d", parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return null;
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean notBlankStr(String s) {
        return s != null && !s.isBlank();
    }

    /** 列表条目 */
    private static class ListItem {
        String title;
        String sourceUrl;
        LocalDate publishDate;
    }
}
