package com.dept.mgmt.aiworkspace.ops;

import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 系统操作 golden set 回归基线测试（P0-2）。
 *
 * <p>从 ops-golden-set.json 读取 63 个用例，在纯 Mockito 环境下逐例走
 * tryOps 全链路（判定→声明式校验→语义校验→权限→执行/待确认→渲染），
 * 断言回复文案、待确认状态、是否转通用对话与 prompt 注入内容。
 * 任何主链路改动导致的回复文案/流程回归都会在此第一时间暴露。
 *
 * @author dept-mgmt
 */
class OpsGoldenSetTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path GOLDEN = Path.of("src/test/resources/ops-golden-set.json");

    @TestFactory
    List<DynamicTest> goldenSet() throws Exception {
        JsonNode root = JSON.readTree(Files.readString(GOLDEN, StandardCharsets.UTF_8));
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            String id = c.get("id").asText();
            tests.add(DynamicTest.dynamicTest(id, () -> runCase(c)));
        }
        return tests;
    }

    private void runCase(JsonNode c) throws Exception {
        long convId = c.path("convId").asLong(9000L);
        OpsTestHarness h = new OpsTestHarness(c.path("role").asText("ROLE_USER"), 1L);
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(h.user, null, h.user.getAuthorities()));
            h.applyFixtures(c.get("fixtures"));
            h.applyDefaultOwnershipStub(); // H2 归属校验默认桩（显式 stubs 可覆盖）
            h.applyStubs(c.get("stubs"));

            // 网关应答链（按调用顺序消费）：首轮判定 → 写入兜底重判定 → followup 轮判定
            List<String> responses = new ArrayList<>();
            if (c.path("decision").isObject()) {
                responses.add(JSON.writeValueAsString(c.get("decision")));
            }
            if (c.path("decisionRetry").isObject()) {
                responses.add(JSON.writeValueAsString(c.get("decisionRetry")));
            }
            JsonNode fu = c.path("followup");
            if (fu.isObject() && fu.path("decision").isObject()) {
                responses.add(JSON.writeValueAsString(fu.get("decision")));
            }
            if (c.path("gatewayThrows").asBoolean(false)) {
                when(h.gateway.chat(anyString(), anyString(), any(), any())).thenThrow(new RuntimeException("网关超时"));
            } else if (c.hasNonNull("decisionRaw")) {
                when(h.gateway.chat(anyString(), anyString(), any(), any())).thenReturn(c.get("decisionRaw").asText());
            } else if (!responses.isEmpty()) {
                when(h.gateway.chat(anyString(), anyString(), any(), any()))
                        .thenReturn(responses.get(0), responses.subList(1, responses.size()).toArray(new String[0]));
            }

            // 用户长期记忆注入（P0-3）
            JsonNode memories = c.path("userMemories");
            if (memories.isArray() && memories.size() > 0) {
                List<String> ms = new ArrayList<>();
                memories.forEach(m -> ms.add(m.asText()));
                when(h.memoryService.recall(1L)).thenReturn(ms);
            }

            AiConversation conv = new AiConversation();
            conv.setId(convId);
            conv.setUserId(1L);
            if (c.hasNonNull("convSummary")) {
                conv.setSummary(c.get("convSummary").asText());
            }

            AiMessage msg = h.service.tryOps(conv, c.get("utterance").asText(), h.user);
            assertExpect(c.get("expect"), msg, h, convId);
            assertPrompts(c.get("expect"), h);

            // 第二轮（确认/取消/澄清）
            if (fu.isObject()) {
                h.applyStubs(fu.get("stubs"));
                AiMessage msg2 = h.service.tryOps(conv, fu.get("utterance").asText(), h.user);
                assertExpect(fu.get("expect"), msg2, h, convId);
                assertPrompts(fu.get("expect"), h);
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 文案 / 待确认 / 转通用对话断言 */
    private void assertExpect(JsonNode e, AiMessage msg, OpsTestHarness h, long convId) {
        if (e.path("pending").isTextual()) {
            OpsPendingStore.PendingOp op = h.pendingStore.peek(convId);
            assertNotNull(op, "预期进入待确认状态");
            assertEquals(e.get("pending").asText(), op.toolName(), "待确认工具名不符");
        }
        if (e.path("pendingNone").asBoolean(false)) {
            assertNull(h.pendingStore.peek(convId), "预期待确认队列已清空");
        }
        if (e.path("isNull").asBoolean(false)) {
            assertNull(msg, "预期转通用对话（tryOps 返回 null）");
            return;
        }
        assertNotNull(msg, "预期产生助手回复");
        String content = msg.getContent();
        for (JsonNode s : e.path("contains")) {
            String want = s.asText();
            assertTrue(content.contains(want), () -> "回复应包含「" + want + "」，实际回复：" + content);
        }
        for (JsonNode s : e.path("notContains")) {
            String banned = s.asText();
            assertFalse(content.contains(banned), () -> "回复不应包含「" + banned + "」，实际回复：" + content);
        }
    }

    /** decide() prompt 注入断言（渐进披露与记忆注入的行为验证） */
    private void assertPrompts(JsonNode e, OpsTestHarness h) {
        if (e.path("promptContains").size() == 0 && e.path("promptNotContains").size() == 0) {
            return;
        }
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(h.gateway, atLeastOnce()).chat(anyString(), cap.capture(), any(), any());
        String allPrompts = String.join("\n--\n", cap.getAllValues());
        for (JsonNode s : e.path("promptContains")) {
            assertTrue(allPrompts.contains(s.asText()), () -> "decide prompt 应包含「" + s.asText() + "」");
        }
        for (JsonNode s : e.path("promptNotContains")) {
            assertFalse(allPrompts.contains(s.asText()), () -> "decide prompt 不应包含「" + s.asText() + "」");
        }
    }
}
