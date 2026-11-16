package com.dept.gateway.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模型列表探测服务测试（内置 Mock Server，覆盖多协议解析/去重/降级/异常分类/缓存）
 *
 * @author dept-mgmt
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelProbeServiceTest {

    private HttpServer server;
    private String base;
    private final ModelProbeService service = new ModelProbeService(new ObjectMapper());

    @BeforeAll
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        int port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;

        // OpenAI 标准结构：6 条记录含 1 个重复 id + 1 个缺 id 脏数据 → 期望去重后 4 个
        server.createContext("/openai/v1/models", ex -> {
            if (!"Bearer sk-ok".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                respond(ex, 401, "{\"error\":\"bad key\"}");
                return;
            }
            respond(ex, 200, """
                    {"object":"list","data":[
                      {"id":"gpt-4o-mini","object":"model","owned_by":"system","context_window":128000},
                      {"id":"deepseek-chat","object":"model","owned_by":"deepseek"},
                      {"id":"gpt-4o-mini","object":"model","owned_by":"system"},
                      {"id":"qwen2.5:7b","object":"model","owned_by":"library"},
                      {"object":"model","owned_by":"broken"},
                      {"id":"text-embedding-3-small","object":"model","owned_by":"system"}
                    ]}""");
        });
        // Ollama 原生结构
        server.createContext("/ollama-host/api/tags", ex -> respond(ex, 200, """
                {"models":[
                  {"name":"qwen2.5:7b","details":{"family":"qwen2","parameter_size":"7.2B"}},
                  {"name":"llama3.2:3b","details":{"family":"llama","parameter_size":"3.2B"}},
                  {"name":"qwen2.5:7b"}
                ]}"""));
        // 仅 Ollama 协议可达：OpenAI 路径 404（自适应降级场景）
        server.createContext("/ollama-like/v1/models", ex -> respond(ex, 404, "{\"error\":\"not found\"}"));
        server.createContext("/ollama-like/api/tags", ex -> respond(ex, 200, """
                {"models":[{"name":"nomic-embed-text","details":{"family":"nomic-bert"}}]}"""));
        // HTML 垃圾响应（代理误配）
        server.createContext("/htmlpage/v1/models", ex -> respond(ex, 200, "<html>502 Bad Gateway</html>"));
        // 空列表
        server.createContext("/empty/v1/models", ex -> respond(ex, 200, "{\"object\":\"list\",\"data\":[]}"));
        server.start();
    }

    @AfterAll
    void tearDown() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> models(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("models");
    }

    @Test
    void OpenAI结构解析去重排序与Key鉴权() {
        Map<String, Object> r = service.probe("OPENAI_COMPAT", base + "/openai/v1", "sk-ok");
        assertTrue((Boolean) r.get("success"), String.valueOf(r.get("errorMessage")));
        assertEquals("OPENAI_COMPAT", r.get("protocol"));
        assertEquals(4, r.get("count"), "6 条原始记录去重+过滤脏数据后应为 4");
        List<Map<String, Object>> ms = models(r);
        assertEquals("deepseek-chat", ms.get(0).get("id"), "应按字典序排序");
        assertEquals(128000L, ms.get(1).get("contextLength"), "context_window 元数据应透出（gpt-4o-mini 排序后在 index 1）");
        assertEquals("deepseek", ms.get(0).get("ownedBy"));
    }

    @Test
    void OpenAI鉴权失败快速返回401() {
        Map<String, Object> r = service.probe("OPENAI_COMPAT", base + "/openai/v1", "sk-wrong");
        assertFalse((Boolean) r.get("success"));
        assertEquals("HTTP_401", r.get("errorType"));
        assertTrue(String.valueOf(r.get("errorMessage")).contains("API Key"));
    }

    @Test
    void Ollama原生协议解析() {
        Map<String, Object> r = service.probe("OLLAMA", base + "/ollama-host", null);
        assertTrue((Boolean) r.get("success"), String.valueOf(r.get("errorMessage")));
        assertEquals("OLLAMA", r.get("protocol"));
        assertEquals(2, r.get("count"), "重复 name 去重后应为 2");
        assertEquals("7.2B", models(r).get(1).get("parameterSize"));
        assertEquals("llama", models(r).get(0).get("ownedBy"), "family 作为 ownedBy 输出");
    }

    @Test
    void 自适应降级_OpenAI404后命中Ollama并提示切换类型() {
        Map<String, Object> r = service.probe("OPENAI_COMPAT", base + "/ollama-like/v1", null);
        assertTrue((Boolean) r.get("success"), String.valueOf(r.get("errorMessage")));
        assertEquals("OLLAMA", r.get("protocol"));
        assertNotNull(r.get("note"), "应提示切换通道类型");
        assertTrue(String.valueOf(r.get("note")).contains("Ollama"));
    }

    @Test
    void HTML垃圾响应识别为协议不匹配() {
        Map<String, Object> r = service.probe("OPENAI_COMPAT", base + "/htmlpage/v1", null);
        assertFalse((Boolean) r.get("success"));
        assertEquals("PROTOCOL_MISMATCH", r.get("errorType"));
    }

    @Test
    void 空列表返回成功但计数为零() {
        Map<String, Object> r = service.probe("OPENAI_COMPAT", base + "/empty/v1", null);
        assertTrue((Boolean) r.get("success"));
        assertEquals(0, r.get("count"));
    }

    @Test
    void 非法地址前置校验() {
        Map<String, Object> r = service.probe("OPENAI_COMPAT", "ftp://x/y", null);
        assertFalse((Boolean) r.get("success"));
        assertEquals("PARAM_INVALID", r.get("errorType"));
    }

    @Test
    void Mock通道固定返回内置模型() {
        Map<String, Object> r = service.probe("MOCK", null, null);
        assertTrue((Boolean) r.get("success"));
        assertEquals("MOCK", r.get("protocol"));
        assertEquals(1, r.get("count"));
        assertEquals("mock-model", models(r).get(0).get("id"));
    }

    @Test
    void 成功结果缓存命中() {
        String ep = base + "/empty/v1"; // 复用轻端点验证缓存
        service.probe("OPENAI_COMPAT", ep, null);
        Map<String, Object> second = service.probe("OPENAI_COMPAT", ep, null);
        assertTrue((Boolean) second.get("cached"), "5 分钟内重复探测应命中缓存");
    }

    private void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception ignored) {
        }
    }
}
