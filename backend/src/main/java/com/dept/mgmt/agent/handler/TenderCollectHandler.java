package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.tender.collect.entity.TenderCollectRun;
import com.dept.mgmt.tender.collect.service.TenderCollectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标讯联网采集登记处理器（AGT-27，T-415，Auto）
 *
 * <p>AI Agent 联网采集入口：按平台配置自动登录 → 列表/详情采集 → LLM 提取校验 →
 * 自动登记 TN 台账 → 结果通知。登记为内部台账写入且带完整溯源，可自动执行。
 *
 * <p>params：platformId（必填，目标平台）、resume（可选，true 断点续采）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderCollectHandler implements CapabilityHandler {

    private final TenderCollectService collectService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-27";
    }

    /** 采集+登记为可复核的台账写入（含溯源与告警），Auto 级自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        return execute(instruction, userId, Map.of());
    }

    @Override
    public String execute(String instruction, Long userId, Map<String, Object> params) throws Exception {
        Long platformId = params.get("platformId") == null ? null : num(params.get("platformId"));
        if (platformId == null && instruction != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("platformId=(\\d+)").matcher(instruction);
            if (m.find()) {
                platformId = Long.parseLong(m.group(1));
            }
        }
        if (platformId == null) {
            throw new BusinessException(400, "未指定采集平台（params.platformId 或指令 platformId=）");
        }
        boolean resume = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resume", "false")));
        TenderCollectRun run = collectService.runCollect(platformId, userId, "AGENT", resume);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-27 标讯联网采集登记");
        out.put("runId", run.getId());
        out.put("status", run.getStatus());
        out.put("pagesFetched", run.getPagesFetched());
        out.put("itemsFound", run.getItemsFound());
        out.put("registerOk", run.getRegisterOk());
        out.put("registerDup", run.getRegisterDup());
        out.put("extractFailed", run.getExtractFailed());
        out.put("lastErrorType", run.getLastErrorType());
        return objectMapper.writeValueAsString(out);
    }

    private Long num(Object v) {
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
