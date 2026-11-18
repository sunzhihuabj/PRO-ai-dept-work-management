package com.dept.mgmt.aiworkspace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hermes Agent 运行参数配置服务（AI 能力 Hermes 化迁移 M-2026-11-18）
 *
 * <p>配置存 DB（hermes_agent_config 表），前端参数面板可视化调整；
 * 读取带 10s 缓存——面板保存后即时生效（缓存 TTL 过期后生效上限 10s）。
 * DB 不可达时回落内置默认值，保证 AI 主流程不因配置存储故障中断。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class HermesAgentConfigService {

    private final JdbcTemplate jdbcTemplate;

    /** 内置默认值（DB 缺行/缺表时兜底） */
    private static final Map<String, String> DEFAULTS = Map.of(
            "migrate.chatViaHermes", "true",
            "migrate.syncTimeoutSec", "180",
            "run.pollIntervalMs", "2000",
            "run.autoStopOnTimeout", "true",
            "delegate.enabled", "true",
            "delegate.triggerWords", "委托AI Agent员工,委托AI员工,委托Agent,交给AI员工,让AI员工跟进",
            "delegate.minLength", "25",
            "output.language", "中文",
            "output.maxResultChars", "2000"
    );

    private volatile long cachedAt = 0;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public HermesAgentConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 读取字符串配置（DB 优先 → 默认值兜底） */
    public String get(String key) {
        refreshIfNeeded();
        String v = cache.get(key);
        if (v == null) {
            v = DEFAULTS.get(key);
        }
        return v == null ? "" : v;
    }

    public boolean getBool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (Exception e) {
            String d = DEFAULTS.get(key);
            return d == null ? 0 : Integer.parseInt(d);
        }
    }

    public long getLong(String key) {
        try {
            return Long.parseLong(get(key).trim());
        } catch (Exception e) {
            String d = DEFAULTS.get(key);
            return d == null ? 0L : Long.parseLong(d);
        }
    }

    /** 保存配置（upsert），并立即刷新缓存 */
    public void save(String key, String value, String updatedBy) {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) conn -> {
            try (var ps = conn.prepareStatement(
                    "UPDATE hermes_agent_config SET config_value=?, updated_by=? WHERE config_key=?")) {
                ps.setString(1, value);
                ps.setString(2, updatedBy);
                ps.setString(3, key);
                if (ps.executeUpdate() == 0) {
                    try (var ins = conn.prepareStatement(
                            "INSERT INTO hermes_agent_config(config_key,config_value,updated_by) VALUES(?,?,?)")) {
                        ins.setString(1, key);
                        ins.setString(2, value);
                        ins.setString(3, updatedBy);
                        ins.executeUpdate();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return true;
        });
        refreshNow();
        log.info("Hermes Agent 配置已保存: key={}, value={}, by={}", key, value, updatedBy);
    }

    /** 全量配置（供面板回显；含默认值兜底） */
    public Map<String, String> getAll() {
        refreshIfNeeded();
        Map<String, String> all = new java.util.LinkedHashMap<>(DEFAULTS);
        cache.forEach(all::put);
        return all;
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 10_000) {
            return;
        }
        refreshNow();
    }

    private synchronized void refreshNow() {
        try {
            var rows = jdbcTemplate.queryForList(
                    "SELECT config_key, config_value FROM hermes_agent_config");
            Map<String, String> fresh = new ConcurrentHashMap<>();
            for (var row : rows) {
                fresh.put(String.valueOf(row.get("config_key")), String.valueOf(row.get("config_value")));
            }
            cache.clear();
            cache.putAll(fresh);
            cachedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("Hermes 配置读取失败（使用默认/旧缓存）: {}", e.getMessage());
        }
    }
}
