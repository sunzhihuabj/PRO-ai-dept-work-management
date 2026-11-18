package com.dept.mgmt.agent.collab.mcp;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.agent.handler.CapabilityHandler;
import com.dept.mgmt.service.ProjectService;
import com.dept.mgmt.service.TaskService;
import com.dept.mgmt.tender.collect.service.TenderPlatformService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MCP 工具注册表（阶段一 WP1-3：业务工具经 MCP Streamable HTTP 暴露）
 *
 * <p>工具分级：只读查询类（task/project/tender/advice 查询）+ 受控写类（tender.collect 经
 * Agent 任务队列异步执行，AGT-27 已为自动级；morning_brief 只读）。后续新增工具在此注册。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class McpToolRegistry {

    private final TaskService taskService;
    private final ProjectService projectService;
    private final TenderPlatformService tenderPlatformService;
    private final DecisionAdviceService adviceService;
    private final com.dept.mgmt.agent.service.MasterAgentService masterAgentService;
    private final McpOpsBridge opsBridge;
    private final com.dept.mgmt.aiworkspace.service.WebSearchService webSearchService;
    private final com.dept.mgmt.aiworkspace.service.ProcessGovernanceService governanceService;
    private final Map<String, CapabilityHandler> handlers;

    private final Map<String, McpTool> tools;

    public McpToolRegistry(TaskService taskService, ProjectService projectService,
                           TenderPlatformService tenderPlatformService,
                           DecisionAdviceService adviceService,
                           com.dept.mgmt.agent.service.MasterAgentService masterAgentService,
                           McpOpsBridge opsBridge,
                           com.dept.mgmt.aiworkspace.service.WebSearchService webSearchService,
                           com.dept.mgmt.aiworkspace.service.ProcessGovernanceService governanceService,
                           List<CapabilityHandler> handlerBeans) {
        this.taskService = taskService;
        this.projectService = projectService;
        this.tenderPlatformService = tenderPlatformService;
        this.adviceService = adviceService;
        this.masterAgentService = masterAgentService;
        this.opsBridge = opsBridge;
        this.webSearchService = webSearchService;
        this.governanceService = governanceService;
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(CapabilityHandler::capabilityCode, Function.identity()));
        // A3：手写核心 8 工具 + Ops 只读工具自动桥接（McpOpsBridge 内部已去重）
        List<McpTool> merged = new ArrayList<>(buildTools());
        try {
            merged.addAll(opsBridge.bridgeTools());
        } catch (Exception e) {
            log.warn("Ops 工具桥接失败（仅核心 8 工具可用）: {}", e.getMessage());
        }
        // V2.0 铁律二：全部工具 schema 统一注入 actingUserId 必填身份参数（含手写+桥接）
        merged = injectIdentity(merged);
        this.tools = merged.stream()
                .collect(Collectors.toMap(McpTool::name, t -> t, (a, b) -> a, LinkedHashMap::new));
        log.info("MCP 工具注册完成（核心 {} + Ops 桥接 {}）: {}",
                buildTools().size(), merged.size() - buildTools().size(), tools.keySet());
    }

    /**
     * 铁律二：统一给全部工具 inputSchema 注入 actingUserId（required）。
     * Hermes 等外部 Agent 按 schema required 回填委托发起人身份；
     * 身份真正生效在 McpProtocolService.toolsCall 统一解析（fail-closed）。
     */
    private static List<McpTool> injectIdentity(List<McpTool> tools) {
        return tools.stream().map(t -> {
            Map<String, Object> props = new LinkedHashMap<>(t.inputSchema().get("properties") == null
                    ? Map.of() : castProps(t.inputSchema().get("properties")));
            props.put(McpIdentityResolver.IDENTITY_PARAM, Map.of(
                    "type", "integer", "description", McpIdentityResolver.IDENTITY_PARAM_DESC));
            List<String> required = new ArrayList<>(
                    t.inputSchema().get("required") == null ? List.of()
                            : (List<String>) castStrList(t.inputSchema().get("required")));
            if (!required.contains(McpIdentityResolver.IDENTITY_PARAM)) {
                required.add(McpIdentityResolver.IDENTITY_PARAM);
            }
            Map<String, Object> schema = McpTool.objectSchema(props, required);
            return new McpTool(t.name(), t.description(), schema, t.requiresApproval(), t.handler());
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castProps(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStrList(Object o) {
        return (List<String>) o;
    }

    public List<McpTool> all() {
        return List.copyOf(tools.values());
    }

    public McpTool get(String name) {
        return tools.get(name);
    }

    private List<McpTool> buildTools() {
        return List.of(
                // ---- 任务域：只读 ----
                new McpTool("task.query", "按条件查询任务列表（返回 id/名称/状态/负责人/截止时间）",
                        McpTool.objectSchema(Map.of(
                                "taskId", Map.of("type", "integer", "description", "任务ID，精确查询"),
                                "projectId", Map.of("type", "integer", "description", "项目ID过滤"),
                                "status", Map.of("type", "integer", "description", "状态：0待办/1进行中/2待审核/3已完成/4已关闭"),
                                "keyword", Map.of("type", "string", "description", "任务名关键字")), null),
                        args -> taskService.list(1, 20,
                                longArg(args, "projectId"), null, intArg(args, "status"),
                                null, longArg(args, "taskId"), strArg(args, "keyword"))),
                new McpTool("task.my_todo", "查询指定用户的待办任务（未完成：待办/进行中/待审核）",
                        McpTool.objectSchema(Map.of(
                                "userId", Map.of("type", "integer", "description", "用户ID（缺省时取 actingUserId 调用身份）")), null),
                        args -> taskService.myTodoTasks(identityOr(args))),

                // ---- 项目域：只读 ----
                new McpTool("project.query", "按条件查询项目列表（编号/名称/负责人/状态）",
                        McpTool.objectSchema(Map.of(
                                "projectName", Map.of("type", "string", "description", "项目名关键字"),
                                "status", Map.of("type", "integer", "description", "状态：0待启动/1进行中/2已完成/3已关闭/4暂停")), null),
                        args -> projectService.list(1, 20, strArg(args, "projectName"),
                                intArg(args, "status"), null)),
                new McpTool("project.phases", "查询项目阶段列表（阶段名/状态/计划与实际起止）",
                        McpTool.objectSchema(Map.of(
                                "projectId", Map.of("type", "integer", "description", "项目ID")), List.of("projectId")),
                        args -> projectService.listPhases(requireLong(args, "projectId"))),

                // ---- 投标域：查询只读 + 采集走 Agent 任务队列（异步） ----
                new McpTool("tender.platforms", "查询启用中的标讯采集平台清单",
                        McpTool.objectSchema(Map.of(), null),
                        args -> tenderPlatformService.listEnabled()),
                new McpTool("tender.collect", "触发指定平台的标讯联网采集登记任务（异步入 Agent 队列执行，立即返回任务号）",
                        McpTool.objectSchema(Map.of(
                                "platformId", Map.of("type", "integer", "description", "采集平台ID"),
                                "userId", Map.of("type", "integer", "description", "任务归属用户ID（缺省时取 actingUserId）")), List.of("platformId")),
                        args -> {
                            long platformId = requireLong(args, "platformId");
                            long userId = identityOr(args);
                            return masterAgentService.submitAs("采集平台 platformId=" + platformId + " 的最新标讯",
                                    "AGT-27", Map.of("platformId", platformId), userId);
                        }),

                // ---- 协同域：建议与报告 ----
                new McpTool("advice.pending", "查询当前待处理的 Agent 决策建议清单",
                        McpTool.objectSchema(Map.of(), null),
                        args -> adviceService.page("PENDING", null, 1, 20)),
                new McpTool("report.morning_brief", "生成指定用户的晨报摘要（指标/风险/重点事项）",
                        McpTool.objectSchema(Map.of(
                                "userId", Map.of("type", "integer", "description", "用户ID（缺省时取 actingUserId）")), null),
                        args -> {
                            CapabilityHandler h = handlers.get("AGT-26");
                            if (h == null) {
                                throw new IllegalStateException("晨报能力点未注册（AGT-26）");
                            }
                            try {
                                return h.execute("生成晨报", identityOr(args), Map.of());
                            } catch (IllegalArgumentException | IllegalStateException e) {
                                throw e;
                            } catch (Exception e) {
                                throw new IllegalStateException("晨报生成失败: " + e.getMessage(), e);
                            }
                        }),

                // ---- Agent 能力点统一入口（B4 二期：AGT 全系列经任务队列异步执行，统一治理） ----
                new McpTool("agent.capability.run",
                        "提交 Agent 能力点任务（capabilityCode=AGT 编码，如 AGT-26 晨报/AGT-27 标讯采集；异步队列执行，立即返回任务号，结果经任务卡片/事件流回写）",
                        McpTool.objectSchema(Map.of(
                                "capabilityCode", Map.of("type", "string", "description", "能力点编码（AGT-XX）"),
                                "instruction", Map.of("type", "string", "description", "任务指令（自然语言）"),
                                "params", Map.of("type", "object", "description", "扩展参数（可选，对象）")), List.of("capabilityCode", "instruction")),
                        args -> masterAgentService.submitAs(strArg(args, "instruction"),
                                strArg(args, "capabilityCode"), args.get("params"), identityOr(args))),

                // ---- V2.0 M5 联网检索（铁律四：脱敏 proxy 唯一出网口，服务端强制脱敏 + outbound_log 留痕） ----
                new McpTool("web.search",
                        "联网检索（V2.0 M5 脱敏出网）：返回 {query, sanitized, hitTypes, results[{title,url,snippet}]}；检索词含客户名/金额/证件号时服务端自动脱敏后才出网，全程留痕",
                        McpTool.objectSchema(Map.of(
                                "query", Map.of("type", "string", "description", "检索词（可含敏感信息，服务端强制脱敏后出网）")), List.of("query")),
                        args -> webSearchService.search(requireStr(args, "query"), identityOr(args))),

                // ---- V2.0 M4 流程管控（催办/健康度只读） ----
                new McpTool("task.overdue.list",
                        "查询超期未完成任务清单（deadline 已过且状态未完成，含超期天数明细；V2.0 M4 催办数据源）",
                        McpTool.objectSchema(Map.of(), null),
                        args -> governanceService.slaReport()),
                new McpTool("process.sla.report",
                        "流程健康度报告（超期任务统计 + 在途审批超 72h SLA 统计 + 明细 top20；工作日巡检同源数据）",
                        McpTool.objectSchema(Map.of(), null),
                        args -> governanceService.slaReport()));
    }

    private static long requireLong(Map<String, Object> args, String key) {
        Long v = longArg(args, key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return v;
    }

    /**
     * 身份优先取值（铁律二）：actingUserId（调用身份）→ userId 参数回落 → 缺失抛错。
     * 调用身份由 McpProtocolService.toolsCall 统一解析并已 fail-closed 校验，
     * 此处回落保证手写工具对旧客户端的兼容。
     */
    private static long identityOr(Map<String, Object> args) {
        Long acting = longArg(args, McpIdentityResolver.IDENTITY_PARAM);
        if (acting != null && acting > 0) {
            return acting;
        }
        return requireLong(args, "userId");
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数类型错误（应为整数）: " + key);
        }
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        Long v = longArg(args, key);
        return v == null ? null : v.intValue();
    }

    private static String strArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String requireStr(Map<String, Object> args, String key) {
        String v = strArg(args, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return v;
    }
}
