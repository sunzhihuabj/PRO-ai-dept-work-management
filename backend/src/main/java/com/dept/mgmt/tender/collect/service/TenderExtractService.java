package com.dept.mgmt.tender.collect.service;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 标讯关键信息智能提取服务（AGT-27，R4 基于 LLM 的信息抽取）
 *
 * <p>从标讯全文中抽取 9 个核心商务字段，输出 JSON 契约经网关约束；
 * 提取结果过确定性校验规则（日期/金额/编号格式），关键字段缺失自动触发
 * <b>二次核验</b>（聚焦缺失字段的补充提问，单轮），仍缺失则标记 PENDING_VERIFY
 * 转人工补全，不阻塞同批次其他标讯登记。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderExtractService {

    /** 关键字段：缺失即触发二次核验 */
    private static final List<String> CRITICAL_FIELDS = List.of("project_name", "deadline", "budget");

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    /** LLM 输出 JSON 契约 */
    static final Map<String, Object> JSON_SCHEMA = Map.ofEntries(
            Map.entry("required", List.of("project_name", "deadline", "budget")),
            Map.entry("project_name", "string"),
            Map.entry("tender_code", "string"),
            Map.entry("purchaser", "string"),
            Map.entry("budget", "number"),
            Map.entry("deadline", "string"),
            Map.entry("open_time", "string"),
            Map.entry("contacts", "string"),
            Map.entry("requirements", "string"),
            Map.entry("qualifications", "string"));

    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    /**
     * 提取结果：fields 为规范化字段表（null=确认缺失）；verifyStatus 见 TenderNotice
     */
    @Data
    public static class ExtractResult {
        private Map<String, String> fields = new LinkedHashMap<>();
        /** NONE/PASSED/REPAIRED/PENDING_VERIFY */
        private String verifyStatus;
        /** 校验明细（字段→结果说明，监控用） */
        private Map<String, String> checkLog = new LinkedHashMap<>();
        /** 原始 budget 数值（校验通过才有值） */
        private String budget;
    }

    public ExtractResult extract(String rawText, Long userId) {
        String prompt = "你是招标信息提取助手。请从以下标讯全文中精准提取核心商务信息，只输出 JSON，不要任何解释文字。\n"
                + "字段定义（未提及的一律填 null，禁止编造、禁止推测）：\n"
                + "project_name（项目名称）、tender_code（招标编号）、purchaser（采购方/招标人全称）、"
                + "budget（预算金额/最高限价，纯数字，单位元，找不到为 null）、deadline（投标截止日期 yyyy-MM-dd）、"
                + "open_time（开标时间，原文口径）、contacts（项目联系人及联系方式，含电话/邮箱原文）、"
                + "requirements（投标要求要点，多项用分号；分隔）、qualifications（资质门槛要求，多项用分号；分隔）\n\n"
                + "标讯全文：\n" + truncate(rawText, 12000);
        Map<String, String> fields = callLlm(prompt, userId);

        // 第一轮确定性校验
        ExtractResult result = normalize(fields);
        boolean missingCritical = CRITICAL_FIELDS.stream().anyMatch(f -> isBlank(result.getFields().get(f)));
        result.setVerifyStatus(missingCritical ? "PENDING_VERIFY" : "PASSED");

        // 二次核验：关键字段缺失时聚焦补全（单轮，结果覆盖）
        if (missingCritical) {
            List<String> missing = CRITICAL_FIELDS.stream()
                    .filter(f -> isBlank(result.getFields().get(f))).toList();
            log.info("AGT-27 关键字段缺失触发二次核验: {}", missing);
            String repairPrompt = "以下标讯原文中，此前提取未能确认字段：" + missing + "。\n"
                    + "请仅针对这些字段在原文中仔细查找（注意『截止时间/递交截止/报名截止』『预算金额/最高限价/控制价』等表述），"
                    + "只输出 JSON，键为字段名，值找不到仍填 null，禁止编造。\n\n标讯全文：\n" + truncate(rawText, 12000);
            Map<String, String> repair = callLlm(repairPrompt, userId);
            Map<String, String> normalizedRepair = normalize(repair).getFields();
            for (String f : missing) {
                if (!isBlank(normalizedRepair.get(f))) {
                    result.getFields().put(f, normalizedRepair.get(f));
                    // 回填校验通过的结构化值（金额等），否则登记时丢失
                    if ("budget".equals(f)) {
                        result.setBudget(normalizedRepair.get(f));
                    }
                    result.getCheckLog().put(f, "二次核验补全");
                } else {
                    result.getCheckLog().put(f, "确认缺失（转人工）");
                }
            }
            boolean stillMissing = CRITICAL_FIELDS.stream().anyMatch(f -> isBlank(result.getFields().get(f)));
            result.setVerifyStatus(stillMissing ? "PENDING_VERIFY" : "REPAIRED");
        }
        return result;
    }

    /** 调网关 LLM 并解析 JSON（容忍代码围栏）；模型输出属外部输入，数值型字段（如 budget）防御性转为字符串 */
    private Map<String, String> callLlm(String prompt, Long userId) {
        String content = aiGatewayClient.chat("AGT-27", prompt, userId, JSON_SCHEMA);
        content = content == null ? "" : content.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
        try {
            Map<?, ?> parsed = objectMapper.readValue(content, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : parsed.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            return out;
        } catch (Exception e) {
            log.warn("AGT-27 提取结果非法 JSON（{}），按全缺失处理", e.getMessage());
            return Map.of();
        }
    }

    /** 字段规范化 + 确定性校验（日期/金额格式；非法值视同缺失，防止脏数据入库） */
    private ExtractResult normalize(Map<String, String> raw) {
        ExtractResult result = new ExtractResult();
        for (String f : JSON_SCHEMA.keySet()) {
            if ("required".equals(f)) {
                continue;
            }
            String v = raw.get(f);
            if (v == null || v.isBlank() || "null".equalsIgnoreCase(v)) {
                result.getFields().put(f, null);
                continue;
            }
            v = v.trim();
            if ("budget".equals(f)) {
                String money = v.replaceAll("[,，\\s元万元]", "");
                if (MONEY_PATTERN.matcher(money).matches()) {
                    result.setBudget(money);
                    result.getFields().put(f, money);
                } else {
                    result.getFields().put(f, null);
                    result.getCheckLog().put(f, "金额格式非法（" + v + "）视同缺失");
                }
            } else if ("deadline".equals(f)) {
                var m = DATE_PATTERN.matcher(v);
                if (m.find()) {
                    result.getFields().put(f, m.group());
                } else {
                    result.getFields().put(f, null);
                    result.getCheckLog().put(f, "日期格式非法（" + v + "）视同缺失");
                }
            } else {
                result.getFields().put(f, v);
            }
        }
        return result;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n…（超长截断）";
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
