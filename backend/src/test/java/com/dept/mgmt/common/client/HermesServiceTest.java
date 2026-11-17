package com.dept.mgmt.common.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * HermesService 单元测试（B1：runs 提交/轮询/审批回调/降级开关）
 *
 * <p>MockRestServiceServer 绑定 RestClient：验证请求形状（URL/方法/Bearer 头/body）与
 * 两态行为（Hermes 在线 / 离线降级）。
 */
class HermesServiceTest {

    private MockRestServiceServer server;
    private HermesService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder()
                .baseUrl("http://127.0.0.1:8642")
                .defaultHeader("Authorization", "Bearer test-key")
                .defaultHeader("Content-Type", org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new HermesService("http://127.0.0.1:8642", true, 10, 300, builder.build());
    }

    private HermesService disabledService() {
        org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder()
                .baseUrl("http://127.0.0.1:8642");
        return new HermesService("http://127.0.0.1:8642", false, 10, 300, builder.build());
    }

    @Test
    void availableShouldCacheHealthProbe() {
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertTrue(service.available());
        // 缓存生效：第二次调用不再发请求（MockRestServiceServer 期望仅一次，未验证即通过）
        assertTrue(service.available());
        server.verify();
    }

    @Test
    void availableShouldReturnFalseWhenDown() {
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withServerError());

        assertFalse(service.available());
        // 提交在不可用状态下直接降级（不发请求）
        assertNull(service.submitRun("测试任务书"));
        server.verify();
    }

    @Test
    void disabledSwitchShouldShortCircuitAll() {
        HermesService off = disabledService();
        assertFalse(off.available());
        assertNull(off.submitRun("任何任务"));
        assertNull(off.getRun("run_x"));
        assertNull(off.awaitRun("run_x"));
        off.stopRun("run_x");
        off.resolveApproval("run_x", "req_1", true);
    }

    @Test
    void submitRunShouldPostTaskBookWithBearer() {
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.input").value("查询项目A的进度并生成周报"))
                .andRespond(withSuccess("{\"run_id\":\"run_abc\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        String runId = service.submitRun("查询项目A的进度并生成周报");

        assertEquals("run_abc", runId);
        server.verify();
    }

    @Test
    void submitRunShouldReturnNullOnMissingRunId() {
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs"))
                .andRespond(withSuccess("{\"error\":{\"message\":\"busy\"}}", MediaType.APPLICATION_JSON));

        assertNull(service.submitRun("任务"));
    }

    @Test
    void submitRunShouldInvalidateCacheOnFailure() {
        // 期望必须一次性声明（SimpleRequestExpectationManager 不允许请求后再追加）
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs"))
                .andRespond(withServerError());
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertNull(service.submitRun("任务"));
        // 提交失败后缓存失效：下次 available() 重新探测
        assertTrue(service.available());
        server.verify();
    }

    @Test
    void awaitRunShouldPollUntilCompleted() {
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs"))
                .andRespond(withSuccess("{\"run_id\":\"run_abc\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs/run_abc"))
                .andRespond(withSuccess("{\"object\":\"hermes.run\",\"run_id\":\"run_abc\",\"status\":\"running\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs/run_abc"))
                .andRespond(withSuccess("{\"object\":\"hermes.run\",\"run_id\":\"run_abc\",\"status\":\"completed\","
                        + "\"output\":\"周报已生成\"}", MediaType.APPLICATION_JSON));

        String runId = service.submitRun("任务");
        Map<String, Object> result = service.awaitRun(runId);

        assertEquals("completed", result.get("status"));
        assertEquals("周报已生成", HermesService.extractOutput(result));
        assertTrue(HermesService.isSuccessful(result));
        server.verify();
    }

    @Test
    void awaitRunShouldReturnLastKnownOnTimeout() {
        server.expect(requestTo("http://127.0.0.1:8642/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs"))
                .andRespond(withSuccess("{\"run_id\":\"run_abc\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));
        // 轮询期间持续 running（次数不限），await timeout 300ms 到期返回最后已知状态
        server.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                        requestTo("http://127.0.0.1:8642/v1/runs/run_abc"))
                .andRespond(withSuccess("{\"status\":\"running\"}", MediaType.APPLICATION_JSON));

        String runId = service.submitRun("任务");
        Map<String, Object> result = service.awaitRun(runId);

        assertEquals("running", result.get("status"));
        assertFalse(HermesService.isSuccessful(result));
    }

    @Test
    void resolveApprovalShouldSendChoice() {
        server.expect(requestTo("http://127.0.0.1:8642/v1/runs/run_abc/approval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.choice").value("once"))
                .andExpect(jsonPath("$.request_id").value("req_9"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.resolveApproval("run_abc", "req_9", true);
        server.verify();
    }

    @Test
    void terminalStateHelpers() {
        assertTrue(HermesService.isTerminal(Map.of("status", "failed", "error", "boom")));
        assertFalse(HermesService.isSuccessful(Map.of("status", "failed", "error", "boom")));
        assertFalse(HermesService.isTerminal(Map.of("status", "running")));
        assertNull(HermesService.extractOutput(null));
        assertEquals("text", HermesService.extractOutput(Map.of("output", "text")));
    }

    @Test
    void consumeEventsShouldParseSsePayloadsAndStopOnClose() throws Exception {
        // JDK 内置 HttpServer 模拟 Hermes SSE 端点：2 个有效事件 + 坏行 + keepalive/注释帧 + 流关闭
        com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        http.createContext("/v1/runs/run_sse/events", exchange -> {
            byte[] body = ("id: 0\ndata: {\"event\":\"tool.started\",\"seq\":0,\"tool\":\"mcp__dept_mgmt__task_my_todo\",\"preview\":\"查询待办\"}\n\n"
                    + ": keepalive\n\n"
                    + "id: 1\ndata: {\"event\":\"tool.completed\",\"seq\":1,\"tool\":\"mcp__dept_mgmt__task_my_todo\",\"duration\":0.35,\"error\":false,\"preview\":\"2条\"}\n\n"
                    + "data: not-json\n\n"
                    + ": stream closed\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        http.start();
        try {
            HermesService sse = new HermesService("http://127.0.0.1:" + http.getAddress().getPort(),
                    true, 10, 300, org.springframework.web.client.RestClient.builder().build());
            java.util.List<Map<String, Object>> events = new java.util.ArrayList<>();
            assertTrue(sse.consumeEvents("run_sse", events::add));
            // 2 个有效 JSON 事件回调，坏行跳过，注释帧忽略，流关闭即返回
            assertEquals(2, events.size());
            assertEquals("tool.started", events.get(0).get("event"));
            assertEquals("mcp__dept_mgmt__task_my_todo", events.get(0).get("tool"));
            assertEquals("tool.completed", events.get(1).get("event"));
            assertEquals(false, events.get(1).get("error"));
            // 停用时短路降级
            assertFalse(disabledService().consumeEvents("run_x", e -> { }));
        } finally {
            http.stop(0);
        }
    }
}
