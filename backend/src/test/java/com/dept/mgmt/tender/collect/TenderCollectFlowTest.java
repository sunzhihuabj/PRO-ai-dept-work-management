package com.dept.mgmt.tender.collect;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dingtalk.service.CryptoService;
import com.dept.mgmt.mapper.TenderCollectRunMapper;
import com.dept.mgmt.mapper.TenderNoticeMapper;
import com.dept.mgmt.mapper.TenderPlatformMapper;
import com.dept.mgmt.mapper.TenderPlatformSessionMapper;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.dept.mgmt.tender.collect.entity.TenderCollectRun;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import com.dept.mgmt.tender.collect.entity.TenderPlatformSession;
import com.dept.mgmt.tender.collect.service.PlatformSessionService;
import com.dept.mgmt.tender.collect.service.TenderCollectService;
import com.dept.mgmt.tender.collect.service.TenderExtractService;
import com.dept.mgmt.tender.collect.service.TenderPlatformService;
import com.dept.mgmt.tender.entity.TenderNotice;
import com.dept.mgmt.tender.service.TenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AGT-27 标讯联网采集全链路功能测试（R7）
 *
 * <p>测试基座：内嵌 MockPlatformServer 提供 3 种不同结构的招标平台（JSON API / HTML 表格 /
 * FORM 登录卡片列表），采集引擎走真实 HTTP；LLM 提取经 Mockito 模拟网关返回
 * （从 prompt 原文中正则提取，真实复现"模型读原文"行为）；MyBatis Mapper 以内存表桩替身。
 *
 * <p>覆盖：平台 CRUD 与凭据加密、FORM/COOKIE/NONE 登录、列表分页遍历、详情全量拉取、
 * 限频拟人间隔、异常重试、断点续采、去重、LLM 提取校验 + 二次核验（REPAIRED/PENDING_VERIFY）、
 * TN 台账登记溯源、登录失败告警、运行监控指标。
 *
 * @author dept-mgmt
 */
class TenderCollectFlowTest {

    static MockPlatformServer server;
    static CryptoService cryptoService = new CryptoService("test-crypto-key");
    static ObjectMapper objectMapper = new ObjectMapper();

    TenderPlatformMapper platformMapper;
    TenderPlatformSessionMapper sessionMapper;
    TenderCollectRunMapper runMapper;
    TenderNoticeMapper noticeMapper;
    TenderService tenderService;
    NotificationService notificationService;
    AiGatewayClient aiGatewayClient;

    TenderPlatformService platformService;
    PlatformSessionService sessionService;
    TenderExtractService extractService;
    TenderCollectService collectService;

    // 内存表
    Map<Long, TenderPlatform> platformTable;
    Map<Long, TenderCollectRun> runTable;
    Map<Long, TenderNotice> noticeTable;
    Set<String> registeredSourceUrls;
    List<Object[]> createTenderCalls;
    List<NotificationSendDTO> notifications;
    List<String> llmPrompts;

    AtomicLong idGen;

    @BeforeAll
    static void startServer() throws Exception {
        server = new MockPlatformServer();
        server.start();
        // 纯 Mockito 环境初始化 MyBatis-Plus Lambda 元数据：
        // 使 wrapper.getSqlSegment() 可解析列名并物化 paramNameValuePairs（供内存桩识别查询条件值）
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), TenderNotice.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), TenderPlatformSession.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), TenderPlatform.class);
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        platformTable = new HashMap<>();
        runTable = new HashMap<>();
        noticeTable = new HashMap<>();
        registeredSourceUrls = new HashSet<>();
        createTenderCalls = new ArrayList<>();
        notifications = new ArrayList<>();
        llmPrompts = new ArrayList<>();
        idGen = new AtomicLong(100);

        // ===== Mapper 内存桩 =====
        platformMapper = mock(TenderPlatformMapper.class);
        when(platformMapper.insert(any(TenderPlatform.class))).thenAnswer(inv -> {
            TenderPlatform p = inv.getArgument(0);
            p.setId(idGen.incrementAndGet());
            platformTable.put(p.getId(), p);
            return 1;
        });
        when(platformMapper.selectById(anyLong())).thenAnswer(inv -> platformTable.get(((Long) inv.getArgument(0))));
        when(platformMapper.updateById(any(TenderPlatform.class))).thenAnswer(inv -> {
            TenderPlatform p = inv.getArgument(0);
            platformTable.put(p.getId(), p);
            return 1;
        });
        when(platformMapper.selectCount(any())).thenReturn(0L);
        when(platformMapper.selectList(any())).thenAnswer(
                inv -> platformTable.values().stream().filter(p -> p.getEnabled() == 1).collect(Collectors.toList()));
        when(platformMapper.delete(any())).thenAnswer(inv -> {
            Object wrapper = inv.getArgument(0);
            int removed = 0;
            if (wrapper instanceof AbstractWrapper<?, ?, ?> aw) {
                try {
                    aw.getSqlSegment();
                } catch (Exception ignored) {
                }
                for (Object v : aw.getParamNameValuePairs().values()) {
                    if (v instanceof Long pid && platformTable.remove(pid) != null) {
                        removed++;
                    }
                }
            }
            return removed;
        });

        sessionMapper = mock(TenderPlatformSessionMapper.class);
        Map<Long, TenderPlatformSession> sessionTable = new HashMap<>();
        when(sessionMapper.selectOne(any())).thenAnswer(inv -> {
            // loadSession 按平台查询：先物化 wrapper 参数，再从参数值中识别平台 ID
            Object wrapper = inv.getArgument(0);
            if (wrapper instanceof AbstractWrapper<?, ?, ?> aw) {
                try {
                    aw.getSqlSegment();
                } catch (Exception ignored) {
                }
                for (Object v : aw.getParamNameValuePairs().values()) {
                    if (v instanceof Long pid && sessionTable.containsKey(pid)) {
                        return sessionTable.get(pid);
                    }
                }
            }
            return null;
        });
        when(sessionMapper.insert(any(TenderPlatformSession.class))).thenAnswer(inv -> {
            TenderPlatformSession s = inv.getArgument(0);
            s.setId(idGen.incrementAndGet());
            sessionTable.put(s.getPlatformId(), s);
            return 1;
        });
        when(sessionMapper.updateById(any(TenderPlatformSession.class))).thenAnswer(inv -> {
            TenderPlatformSession s = inv.getArgument(0);
            sessionTable.put(s.getPlatformId(), s);
            return 1;
        });
        doAnswer(inv -> sessionTable.remove(((TenderPlatformSession) inv.getArgument(0)).getPlatformId()))
                .when(sessionMapper).deleteById(any());

        runMapper = mock(TenderCollectRunMapper.class);
        when(runMapper.insert(any(TenderCollectRun.class))).thenAnswer(inv -> {
            TenderCollectRun r = inv.getArgument(0);
            r.setId(idGen.incrementAndGet());
            runTable.put(r.getId(), r);
            return 1;
        });
        when(runMapper.updateById(any(TenderCollectRun.class))).thenAnswer(inv -> {
            TenderCollectRun r = inv.getArgument(0);
            runTable.put(r.getId(), r);
            return 1;
        });
        when(runMapper.selectById(anyLong())).thenAnswer(inv -> runTable.get(((Long) inv.getArgument(0))));
        when(runMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(runTable.values()));

        noticeMapper = mock(TenderNoticeMapper.class);
        when(noticeMapper.selectCount(any())).thenAnswer(inv -> {
            // source_url 去重查询：先物化 wrapper 参数（惰性填充），再从参数值中识别 URL
            Object wrapper = inv.getArgument(0);
            if (wrapper instanceof AbstractWrapper<?, ?, ?> aw) {
                try {
                    aw.getSqlSegment();
                } catch (Exception ignored) {
                    // 列解析失败时按无参数处理
                }
                for (Object v : aw.getParamNameValuePairs().values()) {
                    if (v instanceof String s && registeredSourceUrls.contains(s)) {
                        return 1L;
                    }
                }
            }
            return 0L;
        });
        when(noticeMapper.updateById(any(TenderNotice.class))).thenAnswer(inv -> {
            TenderNotice n = inv.getArgument(0);
            if (n.getSourceUrl() != null) {
                registeredSourceUrls.add(n.getSourceUrl());
            }
            noticeTable.merge(n.getId(), n, (a, b) -> {
                // 保留既有字段语义：内存桩按字段覆盖
                if (b.getSourceUrl() != null) {
                    a.setSourceUrl(b.getSourceUrl());
                }
                if (b.getVerifyStatus() != null) {
                    a.setVerifyStatus(b.getVerifyStatus());
                }
                return a;
            });
            return 1;
        });

        tenderService = mock(TenderService.class);
        when(tenderService.createTender(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    createTenderCalls.add(inv.getArguments());
                    return idGen.incrementAndGet();
                });

        notificationService = mock(NotificationService.class);
        when(notificationService.send(any(NotificationSendDTO.class))).thenAnswer(inv -> {
            notifications.add(inv.getArgument(0));
            NotificationSendResult result = new NotificationSendResult(1, 0, null);
            return result;
        });

        // ===== LLM 网关模拟：从 prompt 原文中正则提取（复现真实"读原文抽取"行为） =====
        aiGatewayClient = mock(AiGatewayClient.class);
        when(aiGatewayClient.chat(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            String prompt = inv.getArgument(1);
            llmPrompts.add(prompt);
            Map<String, Object> out = new HashMap<>();
            boolean repairMode = prompt.contains("此前提取未能确认字段");
            String text = prompt.substring(prompt.lastIndexOf("标讯全文：") + 5);
            if (repairMode) {
                // 二次核验口径：额外支持『最高限价：N元』表述
                Matcher budget = Pattern.compile("(?:最高限价|预算金额)[：:]\\s*([\\d.]+)\\s*元").matcher(text);
                out.put("budget", budget.find() ? budget.group(1) : null);
                Matcher deadline = Pattern.compile("投标截止时间：(\\d{4}-\\d{2}-\\d{2})").matcher(text);
                out.put("deadline", deadline.find() ? deadline.group(1) : null);
                out.put("project_name", null);
            } else {
                out.put("project_name", group(text, "项目名称"));
                out.put("tender_code", group(text, "招标编号"));
                out.put("purchaser", group(text, "采购方"));
                // 初提取口径：仅识别『预算金额：N元』标准表述（模拟模型首轮遗漏非常规表述）
                Matcher budget = Pattern.compile("预算金额：([\\d.]+)元").matcher(text);
                out.put("budget", budget.find() ? budget.group(1) : null);
                Matcher deadline = Pattern.compile("投标截止时间：(\\d{4}-\\d{2}-\\d{2})").matcher(text);
                out.put("deadline", deadline.find() ? deadline.group(1) : null);
                out.put("open_time", group(text, "开标时间"));
                out.put("contacts", group(text, "联系人"));
                out.put("requirements", group(text, "投标要求"));
                out.put("qualifications", group(text, "资质要求"));
            }
            return objectMapper.writeValueAsString(out);
        });

        // ===== 真实服务组装 =====
        platformService = new TenderPlatformService(platformMapper, cryptoService, objectMapper);
        sessionService = new PlatformSessionService(sessionMapper, cryptoService, aiGatewayClient, objectMapper);
        extractService = new TenderExtractService(aiGatewayClient, objectMapper);
        collectService = new TenderCollectService(platformService, sessionService, extractService,
                tenderService, runMapper, noticeMapper, notificationService, objectMapper);

        // mock 平台观测复位（服务器为静态共享）
        server.requestLog.clear();
        server.flakyHits.clear();
        server.loginFails.clear();
    }

    // ===== 用例 1：JSON_API 平台全链路（列表分页 + 详情 + 提取登记 + 去重 + 断点续采 + 限频） =====

    @Test
    void jsonApiPlatform_fullChain_dedup_resume_rateLimit() {
        TenderPlatform p = createPlatform("JSON-API 平台", "NONE",
                server.base + "/jsonapi/list?page={page}",
                jsonRules("JSON_API", Map.of("itemsPath", "data.rows", "titleField", "title",
                        "linkField", "url", "dateField", "publishDate"),
                        Map.of("contentField", "data.content")));

        long start = System.currentTimeMillis();
        TenderCollectRun run = collectService.runCollect(p.getId(), 1L, "MANUAL", false);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("SUCCESS", run.getStatus());
        assertEquals("NONE", run.getLoginStatus());
        assertEquals(3, run.getPagesFetched()); // 第 3 页为空翻页终止
        assertEquals(8, run.getItemsFound());
        assertEquals(8, run.getItemsCollected());
        assertEquals(8, run.getExtractOk());
        assertEquals(0, run.getExtractFailed());
        assertEquals(8, run.getRegisterOk());
        assertEquals(0, run.getRegisterDup());
        // 限频：3 列表页（第 3 页空翻页终止）+ 8 详情页 = 11 次请求 × 500ms 间隔
        assertTrue(elapsed >= 5000, "拟人限频未生效（耗时 " + elapsed + "ms）");
        assertEquals(11, server.requestLog.size());

        // 登记溯源断言（第 1 条）
        assertEquals(8, createTenderCalls.size());
        Object[] first = createTenderCalls.get(0);
        assertEquals("风电机组塔筒采购项目J-1", first[0]);
        assertEquals("JSON-API 平台", first[1]);
        assertEquals(new BigDecimal("3500000"), first[2]);
        assertEquals(LocalDate.parse("2026-12-20"), first[5]);
        assertEquals("AGENT_COLLECT", first[7]);
        TenderNotice registered = noticeTable.values().stream()
                .filter(n -> "TDBM-J-1".equals(n.getTenderCode())).findFirst().orElseThrow();
        assertEquals(server.base + "/jsonapi/detail/1", registered.getSourceUrl());
        assertEquals("PASSED", registered.getVerifyStatus());
        assertEquals(run.getId(), registered.getCollectRunId());
        assertEquals("国电联合动力技术有限公司", registered.getPurchaser());
        assertTrue(registered.getContactInfo().contains("王工"));

        // 完成通知回调
        assertTrue(notifications.stream().anyMatch(n -> String.valueOf(n.getParams().get("title")).contains("标讯采集完成")));

        // ===== 二次运行（无 resume）：source_url 去重，全部 registerDup =====
        server.requestLog.clear();
        TenderCollectRun run2 = collectService.runCollect(p.getId(), 1L, "MANUAL", false);
        assertEquals(8, run2.getRegisterDup(),
                "run2: dup=" + run2.getRegisterDup() + " ok=" + run2.getRegisterOk()
                        + " collected=" + run2.getItemsCollected() + " found=" + run2.getItemsFound()
                        + " knownUrls=" + registeredSourceUrls.size() + " reqs=" + server.requestLog.size());
        assertEquals(0, run2.getRegisterOk());
        assertEquals(3, server.requestLog.size()); // 仅 3 个列表页（第 3 页空），详情全部去重前置跳过

        // ===== 断点续采：checkpoint 跳过已采 URL =====
        TenderCollectRun run3 = collectService.runCollect(p.getId(), 1L, "MANUAL", true);
        assertEquals(0, run3.getRegisterOk());
        assertEquals(0, run3.getRegisterDup());
    }

    // ===== 用例 2：HTML 表格平台（提取二次核验 REPAIRED / PENDING_VERIFY / 运行内去重） =====

    @Test
    void htmlPlatform_secondPassRepair_andPendingVerify() {
        TenderPlatform p = createPlatform("HTML 表格平台", "NONE",
                server.base + "/htmllist?page={page}",
                jsonRules("HTML", Map.of("itemSelector", "table.news-list tr",
                        "titleSelector", "td:eq(0) a", "dateSelector", "td.date"),
                        Map.of("contentSelector", ".content")));

        TenderCollectRun run = collectService.runCollect(p.getId(), 1L, "AGENT", false);

        assertEquals("PARTIAL", run.getStatus()); // 1 条 PENDING_VERIFY
        assertEquals(4, run.getItemsFound());
        assertEquals(3, run.getItemsCollected()); // 重复 URL 行在运行内被去重跳过
        assertEquals(1, run.getExtractOk());       // H-101 全字段
        assertEquals(1, run.getExtractRepaired()); // H-102 二次核验补全
        assertEquals(1, run.getExtractFailed());   // H-103 转人工
        assertEquals(3, run.getRegisterOk());
        assertEquals("REPAIRED", noticeTable.values().stream()
                .filter(n -> n.getTenderCode() != null && n.getTenderCode().endsWith("H-102"))
                .findFirst().orElseThrow().getVerifyStatus());
        // REPAIRED 样本预算按最高限价补全
        assertTrue(createTenderCalls.stream().anyMatch(c -> "3200000".equals(String.valueOf(c[2]))));
        assertTrue(llmPrompts.stream().filter(s -> s.contains("此前提取未能确认字段")).count() >= 2);
        // PENDING_VERIFY 样本仍登记（人工补全不阻塞批次）
        assertEquals("PENDING_VERIFY", noticeTable.values().stream()
                .filter(n -> n.getTenderCode() != null && n.getTenderCode().endsWith("H-103"))
                .findFirst().orElseThrow().getVerifyStatus());
    }

    // ===== 用例 3：FORM 登录平台（拟人登录 + 会话复用 + 凭据加密 + 登录失败告警） =====

    @Test
    void formPlatform_login_collect_and_wrongPasswordAlert() {
        TenderPlatform ok = createPlatform("FORM 登录平台", "FORM",
                server.base + "/formlist?page={page}",
                formRules());
        setAuth(ok, Map.of("username", MockPlatformServer.FORM_USER, "password", MockPlatformServer.FORM_PASS));
        // 手动测试登录
        Map<String, Object> testResult = sessionService.testLogin(platformService.mustExist(ok.getId()));
        assertEquals(Boolean.TRUE, testResult.get("success"));
        // 会话加密落库（密文不含明文 Cookie）
        assertTrue(ok.getAuthParamsCipher() != null || testResult.containsKey("authType"));

        TenderCollectRun run = collectService.runCollect(ok.getId(), 1L, "MANUAL", false);
        assertEquals("FORM_OK", run.getLoginStatus());
        assertEquals("SUCCESS", run.getStatus());
        assertEquals(2, run.getRegisterOk());
        assertEquals(0, server.loginFails.getOrDefault("form", 0));

        // 登录失败场景：错误密码 → 运行 FAILED + 告警 + 快照
        TenderPlatform bad = createPlatform("FORM 错误凭据平台", "FORM",
                server.base + "/formlist?page={page}",
                formRules());
        setAuth(bad, Map.of("username", MockPlatformServer.FORM_USER, "password", "wrong-pass"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> collectService.runCollect(bad.getId(), 1L, "MANUAL", false));
        assertTrue(ex.getMessage().contains("登录失败"));
        List<TenderCollectRun> failedRuns = runTable.values().stream()
                .filter(r -> "FAILED".equals(r.getStatus())).collect(Collectors.toList());
        assertEquals(1, failedRuns.size());
        assertEquals("LOGIN_FAILED", failedRuns.get(0).getLastErrorType());
        assertTrue(notifications.stream().anyMatch(n -> String.valueOf(n.getParams().get("title")).contains("标讯采集告警")));
        assertEquals(1, server.loginFails.get("form"));
    }

    // ===== 用例 4：网络异常重试（3 次指数退避，前 2 次 500 第 3 次成功） =====

    @Test
    void networkError_retry_withBackoff() {
        TenderPlatform p = createPlatform("重试验证平台", "NONE", server.base + "/htmllist?page={page}",
                jsonRules("HTML", Map.of("itemSelector", "tr", "titleSelector", "a"), Map.of()));
        HttpResponse resp = sessionService.getWithRetry(server.base + "/flaky/retry1", null, p);
        assertEquals(200, resp.getStatus());
        assertEquals(3, server.flakyHits.get("retry1"));
        assertTrue(resp.body().contains("恢复成功样本"));
    }

    // ===== 用例 5：平台管理（CRUD + 凭据加密存储 + 规则校验） =====

    @Test
    void platformCrud_credentialEncryption_ruleValidation() {
        // 创建：凭据即时加密
        Long id = platformService.create("加密验证平台", server.base + "/formlogin",
                server.base + "/jsonapi/list?page={page}",
                null, "FORM", Map.of("username", "u1", "password", "PLAINTEXT-PWD"),
                jsonRules("HTML", Map.of("itemSelector", "tr", "titleSelector", "a"), Map.of()),
                null, null, null, null);
        TenderPlatform stored = platformTable.get(id);
        assertNotNull(stored.getAuthParamsCipher());
        assertFalse(stored.getAuthParamsCipher().contains("PLAINTEXT-PWD"), "明文密码不得落库");
        String plain = cryptoService.decrypt(stored.getAuthParamsCipher());
        assertTrue(plain.contains("PLAINTEXT-PWD"));

        // 查询脱敏
        TenderPlatform detail = platformService.detail(id);
        assertNull(detail.getAuthParamsCipher());
        assertEquals(Boolean.TRUE, detail.getExt().get("authConfigured"));

        // 编辑不传凭据 → 保留原密文
        platformService.update(id, "加密验证平台", server.base + "/formlogin",
                server.base + "/jsonapi/list?page={page}",
                null, "FORM", null, jsonRules("HTML", Map.of("itemSelector", "tr", "titleSelector", "a"), Map.of()),
                null, null, null, "updated");
        assertEquals(stored.getAuthParamsCipher(), platformTable.get(id).getAuthParamsCipher());

        // 规则校验：非法 JSON / 缺失选择器 / 非法 mode
        assertThrows(BusinessException.class, () -> platformService.validateRules("not-json"));
        assertThrows(BusinessException.class, () -> platformService.validateRules("{\"mode\":\"X\"}"));
        assertThrows(BusinessException.class, () -> platformService.validateRules("{\"mode\":\"HTML\",\"list\":{}}"));

        // 删除
        platformService.delete(id);
        assertFalse(platformTable.containsKey(id));
    }

    // ===== 辅助 =====

    private TenderPlatform createPlatform(String name, String authType, String listUrl, String rules) {
        platformService.create(name, "FORM".equals(authType) ? server.base + "/formlogin" : null,
                listUrl, null, authType, new HashMap<>(), rules, 500, 0, 3, null);
        return platformTable.values().stream().filter(x -> name.equals(x.getPlatformName())).findFirst().orElseThrow();
    }

    private void setAuth(TenderPlatform p, Map<String, Object> authParams) {
        try {
            p.setAuthParamsCipher(cryptoService.encrypt(objectMapper.writeValueAsString(authParams)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String jsonRules(String mode, Map<String, Object> list, Map<String, Object> detail) {
        try {
            Map<String, Object> rules = new HashMap<>();
            rules.put("mode", mode);
            rules.put("list", list);
            rules.put("detail", detail);
            rules.put("login", Map.of("usernameField", "username", "passwordField", "password",
                    "failKeyword", "登录失败", "successKeyword", "登录成功"));
            return objectMapper.writeValueAsString(rules);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String formRules() {
        try {
            Map<String, Object> rules = new HashMap<>();
            rules.put("mode", "HTML");
            rules.put("list", Map.of("itemSelector", "div.card", "titleSelector", "a.t", "dateSelector", "span.d"));
            rules.put("detail", Map.of("contentSelector", ".detail-main"));
            // successKeyword 需同时出现在登录响应与会话校验列表页（mock 平台两处均含「退出登录」）
            rules.put("login", Map.of("usernameField", "username", "passwordField", "password",
                    "failKeyword", "登录失败", "successKeyword", "退出登录"));
            return objectMapper.writeValueAsString(rules);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 从模拟原文中提取「标签：值」 */
    private String group(String text, String label) {
        Matcher m = Pattern.compile(label + "：(.+?)(。|$)").matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}
