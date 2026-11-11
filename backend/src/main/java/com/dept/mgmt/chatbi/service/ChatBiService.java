package com.dept.mgmt.chatbi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.chatbi.entity.SysMetric;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.SysMetricMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ChatBI 数据问答服务（T-307 AGT-25，Ask 级只读）
 *
 * 三道禁止编造防线：
 * 1. 指标白名单：意图映射仅命中 sys_metric 注册指标（确定性别名匹配 → LLM 兜底只做"选指标"，不生成 SQL）；
 *    未命中/兜底结果不在白名单 → 明确拒绝并附白名单提示
 * 2. 参数白名单：params 必须在指标 params_schema 注册范围内，long 型严格数字校验，杜绝注入
 * 3. 模板安全：sql_template 执行前二次校验（仅 SELECT、禁危险词/注释/分号），数字全部来自 SQL 执行结果，
 *    回答摘要为模板化拼接（口径原文引用），LLM 不参与数字生成
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class ChatBiService {

    /** 模板占位符 */
    private static final String PLACEHOLDER_PREFIX = "#{";

    /** 模板安全校验：危险 SQL 关键词（词边界匹配，忽略大小写） */
    private static final Pattern DANGEROUS_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|SET|USE|LOCK|UNLOCK|RENAME|REPLACE|MERGE|EXECUTE|PREPARE|HANDLER|OUTFILE|DUMPFILE|LOAD_FILE)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 模板安全校验：注释与多语句（#(?!\{) 排除 #{param} 占位符） */
    private static final Pattern SQL_COMMENT = Pattern.compile("(--|/\\*|\\*/|;|#(?!\\{))");

    private final SysMetricMapper metricMapper;
    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public ChatBiService(SysMetricMapper metricMapper,
                         AiGatewayClient aiGatewayClient,
                         ObjectMapper objectMapper,
                         DataSource dataSource) {
        this.metricMapper = metricMapper;
        this.aiGatewayClient = aiGatewayClient;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 数据问答主流程：意图映射 → 白名单校验 → 参数校验 → 模板校验执行 → 结果组装
     *
     * @param question 自然语言问题
     * @param params   问答参数（如 projectId；可为 null）
     * @param userId   提问用户（审计/LLM 调用）
     */
    public Map<String, Object> ask(String question, Map<String, Object> params, Long userId) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(400, "问题不能为空");
        }
        // 1. 意图映射（确定性别名匹配 → LLM 兜底仅选指标）
        SysMetric metric = resolveMetric(question.trim(), userId);
        if (metric == null) {
            throw new BusinessException(400, "该问题未匹配到指标白名单，ChatBI 仅回答已注册指标，禁止编造。当前可查指标："
                    + whitelistSummary());
        }
        // 2. 参数白名单校验
        Map<String, Object> safeParams = validateParams(metric, params);
        // 3. 模板校验 + 渲染
        String sql = renderSql(metric, safeParams);
        // 4. 执行（只读 SELECT）
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("ChatBI 指标执行失败: metric={}, sql={}", metric.getMetricCode(), sql, e);
            throw new BusinessException(500, "指标查询执行失败：" + metric.getMetricName() + "（请联系管理员检查口径配置）");
        }
        // 5. 结果组装：数字直接引用 SQL 结果，摘要模板化拼接（禁止 LLM 二次生成）
        Object value = null;
        if (rows.size() == 1 && rows.get(0).size() == 1) {
            value = rows.get(0).values().iterator().next();
        }
        String valueText = value == null
                ? (rows.isEmpty() ? "暂无数据" : "共 " + rows.size() + " 行明细")
                : String.valueOf(value);
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> metricInfo = new LinkedHashMap<>();
        metricInfo.put("code", metric.getMetricCode());
        metricInfo.put("name", metric.getMetricName());
        metricInfo.put("category", metric.getCategory());
        metricInfo.put("description", metric.getDescription());
        out.put("metric", metricInfo);
        out.put("question", question.trim());
        out.put("columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
        out.put("rows", rows);
        out.put("value", value);
        out.put("summary", "根据《" + metric.getMetricName() + "》口径（" + metric.getDescription()
                + "），查询结果：" + valueText + "。数据来源：ChatBI 指标白名单（AGT-25），未经 LLM 改写。");
        out.put("fromWhitelist", true);
        log.info("ChatBI 问答: user={}, question={} → metric={}, value={}", userId, question, metric.getMetricCode(), valueText);
        return out;
    }

    /** 白名单清单（供前端展示与拒绝提示） */
    public List<SysMetric> listMetrics() {
        return metricMapper.selectList(new LambdaQueryWrapper<SysMetric>()
                .orderByAsc(SysMetric::getCategory)
                .orderByAsc(SysMetric::getId));
    }

    // ===== 意图映射 =====

    /**
     * 意图映射：确定性别名匹配（最长命中优先）→ LLM 兜底（仅做"选指标"，不生成 SQL）
     * 兜底结果不在白名单一律拒绝（禁止编造）
     */
    private SysMetric resolveMetric(String question, Long userId) {
        List<SysMetric> metrics = listMetrics().stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .toList();
        // 1. 确定性匹配：指标名称/别名包含命中，取最长命中（最精确）
        SysMetric best = null;
        int bestLen = 0;
        for (SysMetric metric : metrics) {
            if (question.contains(metric.getMetricName())) {
                if (metric.getMetricName().length() > bestLen) {
                    best = metric;
                    bestLen = metric.getMetricName().length();
                }
            }
            for (String alias : splitAliases(metric.getAliases())) {
                if (question.contains(alias) && alias.length() > bestLen) {
                    best = metric;
                    bestLen = alias.length();
                }
            }
        }
        if (best != null) {
            return best;
        }
        // 2. LLM 兜底：仅从白名单中选择指标与抽取参数，禁止生成 SQL/编造指标
        return llmResolve(question, metrics, userId);
    }

    /** LLM 意图兜底：输出仅允许白名单内 metric_code，解析失败/越白名单 → null（拒绝） */
    private SysMetric llmResolve(String question, List<SysMetric> metrics, Long userId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ChatBI 意图映射器，任务是把用户问题映射到指标白名单，禁止编造新指标、禁止生成 SQL。\n");
        prompt.append("【指标白名单】\n");
        int i = 1;
        for (SysMetric metric : metrics) {
            prompt.append(i++).append(". ").append(metric.getMetricCode())
                    .append(" ").append(metric.getMetricName())
                    .append("（别名：").append(metric.getAliases() == null ? "无" : metric.getAliases())
                    .append("；参数：").append(metric.getParamsSchema() == null ? "无" : metric.getParamsSchema())
                    .append("）\n");
        }
        prompt.append("【用户问题】").append(question).append("\n");
        prompt.append("【输出要求】仅输出 JSON（required: metric_code, params）：{\"metric_code\":\"白名单中的编码\",\"params\":{\"projectId\":123}}；无法匹配白名单时输出 {\"metric_code\":\"\"}，不得输出其它内容。");
        try {
            String content = aiGatewayClient.chat("AGT-25", prompt.toString(), userId,
                    Map.of("type", "object", "required", List.of("metric_code", "params")));
            Map<String, Object> intent = parseIntent(content);
            if (intent == null) {
                return null;
            }
            String code = String.valueOf(intent.getOrDefault("metric_code", "")).trim();
            if (!StringUtils.hasText(code)) {
                return null;
            }
            SysMetric metric = metricMapper.selectOne(new LambdaQueryWrapper<SysMetric>()
                    .eq(SysMetric::getMetricCode, code)
                    .eq(SysMetric::getStatus, "ACTIVE"));
            if (metric == null) {
                log.warn("ChatBI LLM 意图兜底返回了白名单外编码（已拒绝）: {}", code);
                return null;
            }
            return metric;
        } catch (Exception e) {
            log.warn("ChatBI LLM 意图兜底失败（按未匹配处理，禁止编造）: {}", e.getMessage());
            return null;
        }
    }

    /** 解析 LLM 输出 JSON（容错：剥离 ```json 围栏；非法输出返回 null） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseIntent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> splitAliases(String aliases) {
        if (!StringUtils.hasText(aliases)) {
            return List.of();
        }
        return List.of(aliases.split(";|；"));
    }

    private String whitelistSummary() {
        StringBuilder sb = new StringBuilder();
        for (SysMetric metric : listMetrics()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(metric.getMetricName()).append("(").append(metric.getMetricCode()).append(")");
        }
        return sb.toString();
    }

    // ===== 参数白名单校验 =====

    /**
     * 参数白名单校验：传入参数必须在 params_schema 注册；required 缺失拒绝；long 型严格整数
     */
    private Map<String, Object> validateParams(SysMetric metric, Map<String, Object> params) {
        List<Map<String, String>> schema = parseParamsSchema(metric.getParamsSchema());
        Map<String, Object> safe = new LinkedHashMap<>();
        Map<String, Object> input = params == null ? Collections.emptyMap() : params;
        // 1. 注册校验：未注册参数一律拒绝（防注入通道）
        for (String key : input.keySet()) {
            boolean registered = schema.stream().anyMatch(s -> s.get("name").equals(key));
            if (!registered) {
                throw new BusinessException(400, "参数 " + key + " 不在指标 " + metric.getMetricCode() + " 白名单内，禁止传入");
            }
        }
        // 2. required 与类型校验
        for (Map<String, String> s : schema) {
            String name = s.get("name");
            String type = s.getOrDefault("type", "string");
            boolean required = "true".equalsIgnoreCase(s.getOrDefault("required", "false"));
            Object value = input.get(name);
            if (value == null || !StringUtils.hasText(String.valueOf(value))) {
                if (required) {
                    throw new BusinessException(400, "缺少必填参数 " + name + "（" + s.getOrDefault("desc", "") + "）");
                }
                continue;
            }
            if ("long".equals(type)) {
                String text = String.valueOf(value).trim();
                if (!text.matches("^\\d{1,18}$")) {
                    throw new BusinessException(400, "参数 " + name + " 必须为正整数");
                }
                safe.put(name, Long.parseLong(text));
            } else {
                // string 参数仅允许安全字符（字母数字中文下划线短横线），杜绝引号注入
                String text = String.valueOf(value).trim();
                if (!text.matches("^[\\w\\u4e00-\\u9fa5\\-]{1,50}$")) {
                    throw new BusinessException(400, "参数 " + name + " 含非法字符");
                }
                safe.put(name, text);
            }
        }
        return safe;
    }

    private List<Map<String, String>> parseParamsSchema(String paramsSchema) {
        if (!StringUtils.hasText(paramsSchema)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(paramsSchema,
                    new TypeReference<List<Map<String, String>>>() { });
        } catch (Exception e) {
            log.warn("params_schema 解析失败，按无参数处理: {}", paramsSchema);
            return List.of();
        }
    }

    // ===== 模板校验与渲染 =====

    /**
     * 模板安全校验（执行前二次防线）：仅 SELECT、禁危险词/注释/分号 → 渲染占位符
     */
    private String renderSql(SysMetric metric, Map<String, Object> safeParams) {
        String template = metric.getSqlTemplate();
        if (!StringUtils.hasText(template)) {
            throw new BusinessException(500, "指标 " + metric.getMetricCode() + " 未配置查询模板");
        }
        String trimmed = template.trim();
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            throw new BusinessException(500, "指标模板非法（仅允许 SELECT）：" + metric.getMetricCode());
        }
        if (DANGEROUS_SQL.matcher(trimmed).find() || SQL_COMMENT.matcher(trimmed).find()) {
            throw new BusinessException(500, "指标模板含非法语句，已拦截：" + metric.getMetricCode());
        }
        String sql = trimmed;
        for (Map.Entry<String, Object> entry : safeParams.entrySet()) {
            sql = sql.replace(PLACEHOLDER_PREFIX + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        if (sql.contains(PLACEHOLDER_PREFIX)) {
            int idx = sql.indexOf(PLACEHOLDER_PREFIX);
            int end = sql.indexOf('}', idx);
            String token = end > idx ? sql.substring(idx, end + 1) : "未知占位符";
            throw new BusinessException(400, "缺少模板必填参数（占位符未填充）：" + token);
        }
        return sql;
    }
}
