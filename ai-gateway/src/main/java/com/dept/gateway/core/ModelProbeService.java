package com.dept.gateway.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型列表主动探测服务（AI 服务配置模块「探测模型」功能核心）
 *
 * <p>基于已知 API 地址主动获取该端点可用的模型数量与名称：
 * <ul>
 *   <li>OpenAI 兼容协议：GET {endpoint}/models（Bearer 鉴权），解析 data[].id；</li>
 *   <li>Ollama 原生协议：GET {base}/api/tags，解析 models[].name（endpoint 剥离 /v1 尾部）；</li>
 *   <li>自适应：OPENAI_COMPAT 探测遇 404/结构不匹配时追加尝试 Ollama，命中则提示切换通道类型。</li>
 * </ul>
 *
 * <p>重试策略（与 OpenAiCompatProvider.translate 设计对齐）：仅读超时/429/5xx 重试 1 次；
 * 连接建立失败、鉴权失败（401/403）、404 不重试（重试只会翻倍等待）。
 * 探测为元数据查询，不消耗模型推理配额、不经 Provider 链、不影响对话限流。
 *
 * <p>安全：API Key 仅经 Authorization 头传输，日志只记 endpoint 与错误分类；
 * 结果缓存键含 Key 摘要（hex(hashCode)），不存 Key 明文。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProbeService {

    /** 连接超时（探测为轻请求，远短于对话 60s） */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** 读取超时 */
    private static final int READ_TIMEOUT_MS = 10_000;
    /** 缓存 TTL（模型列表会随服务端变化，5 分钟内免重复外呼） */
    private static final long CACHE_TTL_MS = 5 * 60_000L;
    /** 缓存容量上限（LRU 淘汰） */
    private static final int CACHE_MAX = 32;

    private final ObjectMapper objectMapper;

    /** 探测结果缓存：key = providerType|endpoint|keyHash → entry */
    private final LinkedHashMap<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    private record CacheEntry(Map<String, Object> result, long at) {
    }

    /**
     * 探测模型列表（自适应协议）
     *
     * @param providerType 通道类型（OPENAI_COMPAT/OLLAMA/MOCK，空则纯自适应）
     * @param endpoint     API 地址（OpenAI 兼容惯例含 /v1；Ollama 为根地址）
     * @param apiKey       API Key（可空：Ollama 本地服务无鉴权）
     * @return 结构化结果 {success, protocol, endpointUsed, count, durationMs, models[], cached, errorType, errorMessage, note}
     */
    public Map<String, Object> probe(String providerType, String endpoint, String apiKey) {
        long start = System.currentTimeMillis();
        String type = providerType == null ? "" : providerType.trim();

        // MOCK 通道：固定返回内置模型，无需外呼（放在参数校验之前：MOCK 无需 endpoint）
        if ("MOCK".equals(type)) {
            return ok(start, "MOCK", "mock://models", List.of(mapModel("mock-model", "gateway", null)), false,
                    "Mock 测试通道固定返回 mock-model，无需探测");
        }

        // ---- 参数校验 ----
        String ep = normalize(endpoint);
        if (ep == null) {
            return fail(start, "PARAM_INVALID", "API 地址必须以 http:// 或 https:// 开头");
        }

        // ---- 缓存命中（仅成功结果） ----
        String cacheKey = type + "|" + ep + "|" + (apiKey == null || apiKey.isBlank() ? "-" : Integer.toHexString(apiKey.trim().hashCode()));
        synchronized (cache) {
            CacheEntry e = cache.get(cacheKey);
            if (e != null && System.currentTimeMillis() - e.at() < CACHE_TTL_MS) {
                Map<String, Object> hit = new LinkedHashMap<>(e.result());
                hit.put("cached", true);
                return hit;
            }
        }

        // ---- 协议探测 ----
        Map<String, Object> result;
        if ("OLLAMA".equals(type)) {
            result = tryOllama(ep, start);
        } else {
            // OPENAI_COMPAT 或未指定：OpenAI 优先；404/结构不匹配时智能追加 Ollama 尝试（帮用户发现类型配错）
            result = tryOpenAi(ep, apiKey, start);
            if (!(Boolean) result.get("success")) {
                String errType = String.valueOf(result.get("errorType"));
                if ("HTTP_404".equals(errType) || "PROTOCOL_MISMATCH".equals(errType)) {
                    Map<String, Object> ollama = tryOllama(ep, start);
                    if ((Boolean) ollama.get("success")) {
                        ollama.put("note", "该端点实为 Ollama 协议，建议将通道类型切换为「本地 Ollama」");
                        return ollama;
                    }
                }
            }
        }

        // ---- 成功结果写入缓存（LRU 淘汰） ----
        if ((Boolean) result.get("success")) {
            synchronized (cache) {
                if (cache.size() >= CACHE_MAX) {
                    cache.remove(cache.keySet().iterator().next());
                }
                cache.put(cacheKey, new CacheEntry(new LinkedHashMap<>(result), System.currentTimeMillis()));
            }
            log.info("模型探测成功: endpoint={}, protocol={}, count={}, cost={}ms",
                    ep, result.get("protocol"), result.get("count"), System.currentTimeMillis() - start);
        } else {
            log.warn("模型探测失败: endpoint={}, type={}, errorType={}, cost={}ms",
                    ep, type, result.get("errorType"), System.currentTimeMillis() - start);
        }
        return result;
    }

    // ==================== 协议实现 ====================

    /** OpenAI 兼容：GET {endpoint}/models，解析 data[].id */
    private Map<String, Object> tryOpenAi(String endpoint, String apiKey, long start) {
        RestClient client = buildClient();
        try {
            String body = client.get()
                    .uri(endpoint + "/models")
                    .headers(h -> {
                        if (StringUtils.hasText(apiKey)) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
                        }
                    })
                    .retrieve()
                    .body(String.class);
            return parseOpenAi(body, endpoint + "/models", start);
        } catch (Exception e) {
            return failWithTranslate(e, start);
        }
    }

    /** OpenAI 结构解析：data[].id（防 HTML 误配/脏数据行），去重 + 字典序排序 */
    private Map<String, Object> parseOpenAi(String body, String endpointUsed, long start) {
        try {
            if (!StringUtils.hasText(body)) {
                return fail(start, "PROTOCOL_MISMATCH", "响应为空，非 OpenAI 模型列表结构");
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return fail(start, "PROTOCOL_MISMATCH", "响应非 OpenAI 模型列表结构（data 字段缺失），请确认该地址为模型服务端点");
            }
            Set<String> seen = new LinkedHashSet<>();
            List<Map<String, Object>> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id == null || id.isBlank() || !seen.add(id)) {
                    continue; // 缺 id 脏数据过滤 + 重复 id 去重
                }
                models.add(mapModel(id, item.path("owned_by").asText(""), jsonLong(item, "context_window")));
            }
            models.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));
            return ok(start, "OPENAI_COMPAT", endpointUsed, models, false, null);
        } catch (Exception e) {
            return fail(start, "PROTOCOL_MISMATCH", "响应解析失败（非 JSON 模型列表）：" + brief(e.getMessage()));
        }
    }

    /** Ollama 原生：GET {base}/api/tags（endpoint 剥离 /v1 尾部），解析 models[].name */
    private Map<String, Object> tryOllama(String endpoint, long start) {
        String base = endpoint.matches("^.*/v1/?$") ? endpoint.replaceAll("/v1/?$", "") : endpoint;
        RestClient client = buildClient();
        try {
            String body = client.get().uri(base + "/api/tags").retrieve().body(String.class);
            return parseOllama(body, base + "/api/tags", start);
        } catch (Exception e) {
            return failWithTranslate(e, start);
        }
    }

    /** Ollama 结构解析：models[].name（回退 .model），去重 + 字典序排序 */
    private Map<String, Object> parseOllama(String body, String endpointUsed, long start) {
        try {
            if (!StringUtils.hasText(body)) {
                return fail(start, "PROTOCOL_MISMATCH", "响应为空，非 Ollama 模型列表结构");
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode modelsNode = root.path("models");
            if (!modelsNode.isArray()) {
                return fail(start, "PROTOCOL_MISMATCH", "响应非 Ollama 模型列表结构（models 字段缺失）");
            }
            Set<String> seen = new LinkedHashSet<>();
            List<Map<String, Object>> models = new ArrayList<>();
            for (JsonNode item : modelsNode) {
                String name = item.path("name").asText(item.path("model").asText(null));
                if (name == null || name.isBlank() || !seen.add(name)) {
                    continue;
                }
                Map<String, Object> m = mapModel(name, item.path("details").path("family").asText(""), null);
                String size = item.path("details").path("parameter_size").asText("");
                if (StringUtils.hasText(size)) {
                    m.put("parameterSize", size);
                }
                models.add(m);
            }
            models.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));
            return ok(start, "OLLAMA", endpointUsed, models, false, null);
        } catch (Exception e) {
            return fail(start, "PROTOCOL_MISMATCH", "响应解析失败（非 JSON 模型列表）：" + brief(e.getMessage()));
        }
    }

    // ==================== 异常分类转译 ====================

    /** 网络/HTTP 异常 → 统一错误分类（重试已在 exchange 内受控完成，此处只做最终翻译） */
    private Map<String, Object> failWithTranslate(Exception e, long start) {
        if (e instanceof HttpStatusCodeException http) {
            int sc = http.getStatusCode().value();
            if (sc == 401 || sc == 403) {
                return fail(start, "HTTP_" + sc, "鉴权失败(HTTP " + sc + ")：API Key 无效或缺少权限");
            }
            if (sc == 404) {
                return fail(start, "HTTP_404", "端点不存在(HTTP 404)：请核对 API 地址版本段（如 /v1）");
            }
            if (sc == 429) {
                return fail(start, "HTTP_429", "上游限流(HTTP 429)，请稍后重试");
            }
            return fail(start, "HTTP_" + sc, "上游服务异常(HTTP " + sc + ")：" + brief(http.getResponseBodyAsString()));
        }
        if (e instanceof ResourceAccessException) {
            String msg = rootMessage(e);
            if (msg != null && msg.toLowerCase().contains("timed out")) {
                return fail(start, "TIMEOUT", "连接超时（" + CONNECT_TIMEOUT_MS + "ms 内未建立连接），目标服务负载高或网络不可达");
            }
            return fail(start, "CONNECTION", "无法连接目标地址（服务未启动或端口错误）");
        }
        return fail(start, "IO", "请求异常：" + brief(rootMessage(e)));
    }

    // ==================== 私有辅助 ====================

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));
        return RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** endpoint 归一化：trim、去尾部 /、协议校验；非法返回 null */
    private String normalize(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        String ep = endpoint.trim();
        if (ep.endsWith("/")) {
            ep = ep.replaceAll("/+$", "");
        }
        return (ep.startsWith("http://") || ep.startsWith("https://")) ? ep : null;
    }

    private Map<String, Object> mapModel(String id, String ownedBy, Long contextWindow) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("ownedBy", ownedBy == null ? "" : ownedBy);
        if (contextWindow != null) {
            m.put("contextLength", contextWindow);
        }
        return m;
    }

    private Long jsonLong(JsonNode item, String field) {
        JsonNode n = item.path(field);
        return n.canConvertToLong() ? n.asLong() : null;
    }

    private Map<String, Object> ok(long start, String protocol, String endpointUsed,
                                   List<Map<String, Object>> models, boolean cached, String note) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("protocol", protocol);
        r.put("endpointUsed", endpointUsed);
        r.put("count", models.size());
        r.put("durationMs", System.currentTimeMillis() - start);
        r.put("models", models);
        r.put("cached", cached);
        r.put("errorType", null);
        r.put("errorMessage", null);
        r.put("note", note);
        return r;
    }

    private Map<String, Object> fail(long start, String errorType, String errorMessage) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("protocol", null);
        r.put("endpointUsed", null);
        r.put("count", 0);
        r.put("durationMs", System.currentTimeMillis() - start);
        r.put("models", List.of());
        r.put("cached", false);
        r.put("errorType", errorType);
        r.put("errorMessage", errorMessage);
        r.put("note", null);
        return r;
    }

    private String brief(String msg) {
        if (msg == null) {
            return "";
        }
        String s = msg.replaceAll("\\s+", " ").trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
