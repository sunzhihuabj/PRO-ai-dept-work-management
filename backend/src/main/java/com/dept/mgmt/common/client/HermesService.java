package com.dept.mgmt.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hermes 主 Agent 客户端（backend → Hermes API Server 127.0.0.1:8642）
 *
 * <p>契约（Hermes v0.22.5 api_server）：
 * <ul>
 *   <li>POST /v1/runs  body={"input":"任务书"} → 立即返回 run_id（status=queued）</li>
 *   <li>GET  /v1/runs/{id} → {"object":"hermes.run","run_id","status","output","error","usage",...}
 *       终态：completed / failed / cancelled / stopped</li>
 *   <li>POST /v1/runs/{id}/stop；POST /v1/runs/{id}/approval body={"choice":"once|deny","request_id":...}</li>
 *   <li>GET /health → {"status":"ok"}</li>
 * </ul>
 *
 * <p>降级设计（B1）：{@code app.hermes.enabled} 总开关 + available() 探测结果缓存 30s——
 * Hermes 离线时 {@link #available()} 返回 false，调用方（B2 第四级路由）回落现有三级路由，
 * 本客户端不抛异常打断业务主流程。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class HermesService {

    /** run 终态集合（与 api_server TERMINAL_STATUSES 对齐） */
    private static final java.util.Set<String> TERMINAL = java.util.Set.of(
            "completed", "failed", "cancelled", "stopped", "error");

    private final String baseUrl;
    private final String apiKey;
    private final boolean enabled;
    private final long pollIntervalMs;
    private final long awaitTimeoutMs;
    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 可用性探测缓存（30s）：B2 路由高频判可用，避免每请求打 /health */
    private volatile long availableCheckedAt;
    private volatile boolean availableCached;

    /** 生产构造（唯一 Spring 注入构造；另有包内测试构造，@Autowired 消歧） */
    @org.springframework.beans.factory.annotation.Autowired
    public HermesService(@Value("${app.hermes.base-url:http://127.0.0.1:8642}") String baseUrl,
                         @Value("${app.hermes.api-key:dept-hermes-api-2026r2-K7mR4pXwL9cT2vBn}") String apiKey,
                         @Value("${app.hermes.enabled:true}") boolean enabled,
                         @Value("${app.hermes.poll-interval-ms:2000}") long pollIntervalMs,
                         @Value("${app.hermes.await-timeout-ms:600000}") long awaitTimeoutMs) {        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.pollIntervalMs = pollIntervalMs;
        this.awaitTimeoutMs = awaitTimeoutMs;
        // 连接 2s / 读取 30s（单次 run API 调用，非长轮询——awaitRun 由 pollInterval 控制节奏）
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(2_000));
        factory.setReadTimeout(Duration.ofMillis(30_000));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 测试构造（MockRestServiceServer 经 RestClient.Builder 注入 mock requestFactory） */
    HermesService(String baseUrl, boolean enabled, long pollIntervalMs, long awaitTimeoutMs,
                  org.springframework.web.client.RestClient existingClient) {
        this.baseUrl = baseUrl;
        this.apiKey = "test-key";
        this.enabled = enabled;
        this.pollIntervalMs = pollIntervalMs;
        this.awaitTimeoutMs = awaitTimeoutMs;
        this.client = existingClient;
    }

    /** 总开关 + 可用性探测（缓存 30s）。Hermes 离线/停用时返回 false，调用方降级。 */
    public boolean available() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - availableCheckedAt < 30_000) {
            return availableCached;
        }
        boolean ok = false;
        try {
            Map<?, ?> resp = client.get().uri("/health").retrieve().body(Map.class);
            ok = resp != null && "ok".equals(resp.get("status"));
        } catch (Exception e) {
            log.debug("Hermes 健康探测失败: {}", e.getMessage());
        }
        availableCheckedAt = now;
        availableCached = ok;
        if (!ok) {
            log.info("Hermes API Server 不可用（将降级现有路由）: baseUrl={}", baseUrl);
        }
        return ok;
    }

    /** 强制刷新可用性缓存（提交失败后重探测用） */
    public void invalidateAvailability() {
        availableCheckedAt = 0;
    }

    /**
     * 提交 Agent 运行任务（异步：立即返回 run_id，执行经 {@link #getRun} 轮询）
     *
     * @param taskBook 任务书（自然语言任务描述，含目标/上下文/约束）
     * @return run_id；Hermes 不可用返回 null（调用方降级）
     */
    @SuppressWarnings("unchecked")
    public String submitRun(String taskBook) {
        if (!available()) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", taskBook);
        try {
            Map<String, Object> resp = client.post()
                    .uri("/v1/runs")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object runId = resp == null ? null : resp.get("run_id");
            if (runId == null || runId.toString().isBlank()) {
                log.warn("Hermes run 提交响应缺 run_id: {}", resp);
                return null;
            }
            log.info("Hermes run 已提交: runId={}", runId);
            return runId.toString();
        } catch (Exception e) {
            log.warn("Hermes run 提交失败（降级现有路由）: {}", e.getMessage());
            invalidateAvailability();
            return null;
        }
    }

    /** 查询运行状态（runId 无效/查询失败返回 null） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRun(String runId) {
        if (!enabled || runId == null || runId.isBlank()) {
            return null;
        }
        try {
            return client.get().uri("/v1/runs/{id}", runId).retrieve().body(Map.class);
        } catch (Exception e) {
            log.warn("Hermes run 状态查询失败: runId={}, err={}", runId, e.getMessage());
            return null;
        }
    }

    /**
     * 阻塞等待运行至终态（B1 同步语义；B2 异步投递时改用 getRun 由调度侧轮询）
     *
     * @return 终态 run 状态对象；超时/失败/中断返回最后已知状态（可能 null）
     */
    public Map<String, Object> awaitRun(String runId) {
        long deadline = System.currentTimeMillis() + awaitTimeoutMs;
        Map<String, Object> last = null;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> status = getRun(runId);
            if (status != null) {
                last = status;
                String st = String.valueOf(status.get("status"));
                if (TERMINAL.contains(st)) {
                    return status;
                }
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        log.warn("Hermes run 等待超时: runId={}, timeoutMs={}", runId, awaitTimeoutMs);
        return last;
    }

    /** 取消运行（best-effort） */
    public void stopRun(String runId) {
        if (!enabled || runId == null || runId.isBlank()) {
            return;
        }
        try {
            client.post().uri("/v1/runs/{id}/stop", runId).retrieve().toBodilessEntity();
            log.info("Hermes run 已请求停止: runId={}", runId);
        } catch (Exception e) {
            log.warn("Hermes run 停止失败: runId={}, err={}", runId, e.getMessage());
        }
    }

    /**
     * 审批回调（B5 双向闭环：本系统采纳/驳回 → Hermes 恢复或终止执行）
     *
     * @param requestId Hermes 审批请求 ID（run 状态 pending_approval 事件中获取）
     * @param allow     true=once（放行一次）；false=deny（拒绝）
     */
    public void resolveApproval(String runId, String requestId, boolean allow) {
        if (!enabled || runId == null || runId.isBlank()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("choice", allow ? "once" : "deny");
        if (requestId != null && !requestId.isBlank()) {
            body.put("request_id", requestId);
        }
        try {
            client.post().uri("/v1/runs/{id}/approval", runId).body(body).retrieve().toBodilessEntity();
            log.info("Hermes 审批回调已发送: runId={}, requestId={}, allow={}", runId, requestId, allow);
        } catch (Exception e) {
            log.warn("Hermes 审批回调失败: runId={}, err={}", runId, e.getMessage());
        }
    }

    /** 终态判定（供调用方处理 awaitRun 返回值） */
    public static boolean isTerminal(Map<String, Object> runStatus) {
        return runStatus != null && TERMINAL.contains(String.valueOf(runStatus.get("status")));
    }

    /** 运行是否成功完成（completed 且无 error） */
    public static boolean isSuccessful(Map<String, Object> runStatus) {
        return isTerminal(runStatus) && "completed".equals(runStatus.get("status"))
                && runStatus.get("error") == null;
    }

    /** 提取 run 最终输出文本（output 字段；非字符串序列化保底） */
    public static String extractOutput(Map<String, Object> runStatus) {
        if (runStatus == null) {
            return null;
        }
        Object out = runStatus.get("output");
        if (out instanceof String s) {
            return s;
        }
        return out == null ? null : out.toString();
    }

    /**
     * 消费 run 执行过程事件流（B3 过程回写）：GET /v1/runs/{id}/events SSE——
     * 连接后服务端先重放 backlog（含历史事件，Last-Event-ID 语义），随 run 生命周期推送
     * tool.started / tool.completed / approval.request / message.interim / run.{终态} 等事件，
     * 流关闭（终态后服务端发 close 哨兵）或读超时（30s 无事件）即返回。
     *
     * <p>降级语义：连接失败/非 200/解析异常一律静默返回 false——过程事件缺失不影响
     * awaitRun 终态轮询回写（调用方独立执行）。
     *
     * @param runId   run ID
     * @param onEvent 逐事件回调（payload 含 event/seq/timestamp 及事件字段）
     * @return 是否成功读到事件流
     */
    @SuppressWarnings("unchecked")
    public boolean consumeEvents(String runId, java.util.function.Consumer<Map<String, Object>> onEvent) {
        if (!enabled || runId == null || runId.isBlank()) {
            return false;
        }
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = java.net.URI.create(baseUrl + "/v1/runs/" + runId + "/events").toURL();
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty(HttpHeaders.ACCEPT, "text/event-stream");
            conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            if (conn.getResponseCode() != 200) {
                log.debug("Hermes run 事件流不可用: runId={}, http={}", runId, conn.getResponseCode());
                return false;
            }
            int count = 0;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // SSE 帧：注释行（": keepalive"）跳过，仅取 "data:" 载荷
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    try {
                        onEvent.accept(objectMapper.readValue(data, Map.class));
                        count++;
                    } catch (Exception parseEx) {
                        log.debug("Hermes run 事件解析跳过: runId={}, err={}", runId, parseEx.getMessage());
                    }
                }
            }
            log.debug("Hermes run 事件流读取完成: runId={}, events={}", runId, count);
            return true;
        } catch (Exception e) {
            log.debug("Hermes run 事件流读取结束/失败（降级继续终态轮询）: runId={}, err={}", runId, e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
