package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hermes Agent 原生配置服务（V2.0 全功能嵌入补齐）：
 * 读取/修改 Hermes config.yaml（模型切换 / agent 参数 / 可用模型清单），
 * 写操作采用<b>锚点行替换</b>（不全量重写，保留注释与未管理段落），写前自动备份（.bak-时间戳），
 * 仅部门领导可操作（Controller 层 @PreAuthorize + @RequirePerm 双声明）。
 *
 * <p>生效说明：Hermes gateway 进程在 run 提交时读取配置；修改后无需立即重启，
 * 若模型通道切换未即时生效可重启 DepSvc-svc-hermes 计划任务。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class HermesConfigService {

    @Value("${hermes.config-path:C:\\Users\\sunzh\\AppData\\Local\\hermes\\config.yaml}")
    private String configPath;

    /** 读取配置摘要（供前端原生配置面板；api_key 一律脱敏） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readSummary() {
        Path path = Path.of(configPath);
        if (!Files.exists(path)) {
            throw new BusinessException(404, "Hermes 配置文件不存在: " + configPath);
        }
        Map<String, Object> root;
        try {
            root = new Yaml().load(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BusinessException(500, "Hermes 配置读取失败: " + e.getMessage());
        }
        if (root == null) {
            throw new BusinessException(500, "Hermes 配置为空（YAML 解析失败）");
        }
        Map<String, Object> model = (Map<String, Object>) root.getOrDefault("model", Map.of());
        Map<String, Object> agent = (Map<String, Object>) root.getOrDefault("agent", Map.of());

        // providers 清单（dept-gateway/zcode-free/...，default_model + models 键）
        List<Map<String, Object>> providers = new ArrayList<>();
        Object provRaw = root.get("providers");
        if (provRaw instanceof Map<?, ?> provMap) {
            for (Map.Entry<?, ?> e : provMap.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> p)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", String.valueOf(e.getKey()));
                item.put("baseUrl", p.get("base_url"));
                item.put("defaultModel", p.get("default_model"));
                Object models = p.get("models");
                item.put("models", models instanceof Map<?, ?> m
                        ? new ArrayList<String>(((Map<String, Object>) m).keySet()) : List.of());
                providers.add(item);
            }
        }
        // custom_providers（自定义外连，api_key 脱敏）
        List<Map<String, Object>> customs = new ArrayList<>();
        Object customRaw = root.get("custom_providers");
        if (customRaw instanceof Collection<?> col) {
            for (Object o : col) {
                if (o instanceof Map<?, ?> c) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", c.get("name"));
                    item.put("baseUrl", c.get("base_url"));
                    item.put("model", c.get("model"));
                    item.put("apiKeyMasked", mask(c.get("api_key") == null ? "" : String.valueOf(c.get("api_key"))));
                    customs.add(item);
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("path", configPath);
        summary.put("modelDefault", model.get("default"));
        summary.put("modelProvider", model.get("provider"));
        summary.put("contextLength", model.get("context_length"));
        summary.put("maxTurns", agent.get("max_turns"));
        summary.put("apiMaxRetries", agent.get("api_max_retries"));
        summary.put("imageInputMode", agent.get("image_input_mode"));
        summary.put("providers", providers);
        summary.put("customProviders", customs);
        return summary;
    }

    /** 模型切换：改 model: 块下的 default 与 provider 行（锚点替换，写前备份） */
    public Map<String, Object> switchModel(String provider, String model) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(model)) {
            throw new BusinessException(400, "provider 与 model 均不能为空");
        }
        List<String> lines = readLines();
        int modelBlock = findBlock(lines, "model:", 0);
        if (modelBlock < 0) {
            throw new BusinessException(500, "config.yaml 缺少 model: 段");
        }
        replaceInBlock(lines, modelBlock, "  default:", "  default: " + model);
        replaceInBlock(lines, modelBlock, "  provider:", "  provider: " + provider);
        writeWithBackup(lines);
        log.info("Hermes 模型切换完成: provider={}, model={}", provider, model);
        return Map.of("modelDefault", model, "modelProvider", provider);
    }

    /** agent 参数修改：max_turns / api_max_retries（null 跳过） */
    public Map<String, Object> updateAgentParams(Integer maxTurns, Integer apiMaxRetries) {
        if (maxTurns == null && apiMaxRetries == null) {
            throw new BusinessException(400, "至少提供一个待修改参数");
        }
        if (maxTurns != null && (maxTurns < 1 || maxTurns > 200)) {
            throw new BusinessException(400, "max_turns 取值范围 1-200");
        }
        if (apiMaxRetries != null && (apiMaxRetries < 0 || apiMaxRetries > 10)) {
            throw new BusinessException(400, "api_max_retries 取值范围 0-10");
        }
        List<String> lines = readLines();
        int agentBlock = findBlock(lines, "agent:", 0);
        if (agentBlock < 0) {
            throw new BusinessException(500, "config.yaml 缺少 agent: 段");
        }
        if (maxTurns != null) {
            replaceInBlock(lines, agentBlock, "  max_turns:", "  max_turns: " + maxTurns);
        }
        if (apiMaxRetries != null) {
            replaceInBlock(lines, agentBlock, "  api_max_retries:", "  api_max_retries: " + apiMaxRetries);
        }
        writeWithBackup(lines);
        log.info("Hermes agent 参数修改完成: maxTurns={}, apiMaxRetries={}", maxTurns, apiMaxRetries);
        return readSummary();
    }

    // ==================== 内部：锚点行定位与备份写回 ====================

    private List<String> readLines() {
        Path path = Path.of(configPath);
        if (!Files.exists(path)) {
            throw new BusinessException(404, "Hermes 配置文件不存在: " + configPath);
        }
        try {
            return new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BusinessException(500, "配置读取失败: " + e.getMessage());
        }
    }

    /** 找顶层块起始行（行内容等于 anchor，如 "model:"） */
    private int findBlock(List<String> lines, String anchor, int fromIdx) {
        for (int i = fromIdx; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(anchor.replaceFirst(":$", ""))
                    || lines.get(i).trim().equals(anchor)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在块内（从 blockIdx+1 到下一个顶层键，即列 0 非空行）替换首个以 prefix 开头的行；
     * 找不到时在块内首个二级缩进位置插入（防配置项缺失）。
     */
    private void replaceInBlock(List<String> lines, int blockIdx, String prefix, String replacement) {
        int end = lines.size();
        for (int i = blockIdx + 1; i < lines.size(); i++) {
            String t = lines.get(i);
            if (StringUtils.hasText(t) && !t.startsWith(" ") && !t.startsWith("#")) {
                end = i; // 顶层键 → 块结束
                break;
            }
        }
        for (int i = blockIdx + 1; i < end; i++) {
            if (lines.get(i).startsWith(prefix)) {
                lines.set(i, replacement);
                return;
            }
        }
        // 未找到：插入到块内（缩进对齐 prefix）
        lines.add(end, replacement);
    }

    /** 写回前备份 config.yaml.bak-YYYYMMDDHHmmss，保留最近 5 份 */
    private void writeWithBackup(List<String> lines) {
        Path path = Path.of(configPath);
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Path backup = path.resolveSibling(path.getFileName() + ".bak-" + stamp);
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            pruneBackups(path.getParent(), path.getFileName() + ".bak-");
        } catch (IOException e) {
            throw new BusinessException(500, "配置写回失败（未生效，备份未丢）: " + e.getMessage());
        }
    }

    /** 只保留最近 5 份备份 */
    private void pruneBackups(Path dir, String prefix) {
        try (var stream = Files.list(dir)) {
            List<Path> baks = stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted().toList();
            for (int i = 0; i < baks.size() - 5; i++) {
                Files.deleteIfExists(baks.get(i));
            }
        } catch (IOException e) {
            log.warn("备份清理失败（忽略）: {}", e.getMessage());
        }
    }

    private String mask(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        return key.length() <= 8 ? "****" : key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
