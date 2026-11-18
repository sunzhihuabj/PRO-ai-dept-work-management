package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.service.HermesAgentConfigService;
import com.dept.mgmt.common.client.HermesService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hermes Agent 参数配置面板接口（AI 能力 Hermes 化迁移 M-2026-11-18）
 *
 * <p>支持前端直接调整 Hermes Agent 运行参数（性能/运行模式/输出规则），
 * 保存即时生效（10s 配置缓存）；同时暴露 Hermes 服务健康探活。
 */
@Slf4j
@RestController
@RequestMapping("/v1/ai/hermes")
@RequiredArgsConstructor
@Tag(name = "Hermes Agent 配置", description = "运行参数面板、健康探活")
public class HermesAgentConfigController {

    private final HermesAgentConfigService configService;
    private final HermesService hermesService;

    /** 可编辑键白名单（防任意键写入） */
    private static final Map<String, String> EDITABLE_KEYS = Map.of(
            "migrate.chatViaHermes", "chat 出口经 Hermes 原生 run（总迁移开关）",
            "migrate.syncTimeoutSec", "同步等待 Hermes run 终态超时（秒）",
            "run.pollIntervalMs", "run 终态轮询间隔（毫秒）",
            "run.autoStopOnTimeout", "同步等待超时后自动 stopRun",
            "delegate.enabled", "Hermes 委托路由开关",
            "delegate.triggerWords", "委托触发词（逗号分隔）",
            "delegate.minLength", "多步串联判定的最短消息长度",
            "output.language", "输出语言规则",
            "output.maxResultChars", "结果摘要最大字符数"
    );

    @GetMapping("/config")
    @Operation(summary = "读取全部运行参数（含说明）")
    public R<Map<String, Object>> getConfig() {
        Map<String, String> values = configService.getAll();
        Map<String, Object> items = new LinkedHashMap<>();
        EDITABLE_KEYS.forEach((key, desc) -> items.put(key, Map.of(
                "value", values.getOrDefault(key, ""),
                "description", desc
        )));
        return R.ok(items);
    }

    @PutMapping("/config")
    @Operation(summary = "保存运行参数（白名单键）")
    public R<Void> saveConfig(@RequestBody ConfigSaveRequest req) {
        LoginUser loginUser = LoginUser.current();
        String by = loginUser != null ? loginUser.getUsername() : "system";
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return R.fail(400, "未提供任何配置项");
        }
        for (var e : req.getItems().entrySet()) {
            String key = e.getKey();
            if (!EDITABLE_KEYS.containsKey(key)) {
                return R.fail(400, "非法配置键: " + key);
            }
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
            // 轻量合法性校验
            if (("migrate.syncTimeoutSec".equals(key) || "run.pollIntervalMs".equals(key)
                    || "delegate.minLength".equals(key) || "output.maxResultChars".equals(key))
                    && !value.isBlank()) {
                try {
                    int v = Integer.parseInt(value.trim());
                    if (v < 0) {
                        return R.fail(400, key + " 不能为负数");
                    }
                } catch (NumberFormatException nfe) {
                    return R.fail(400, key + " 必须为整数");
                }
            }
            if (("migrate.chatViaHermes".equals(key) || "run.autoStopOnTimeout".equals(key)
                    || "delegate.enabled".equals(key)) && !value.isBlank()) {
                if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                    return R.fail(400, key + " 必须为 true/false");
                }
            }
            configService.save(key, value.isBlank() ? EDITABLE_KEYS.get(key) : value.trim(), by);
        }
        return R.ok("配置已保存并即时生效", null);
    }

    @GetMapping("/health")
    @Operation(summary = "Hermes 服务健康探活（面板实时状态）")
    public R<Map<String, Object>> health() {
        boolean ok = hermesService.available();
        return R.ok(Map.of(
                "hermesAvailable", ok,
                "checkedAt", java.time.LocalDateTime.now().toString()
        ));
    }

    @Data
    public static class ConfigSaveRequest {
        /** key -> value */
        private Map<String, String> items;
    }
}
