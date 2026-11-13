package com.dept.mgmt.aiworkspace.ops;

import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.fineperm.service.FinePermissionService;
import com.dept.mgmt.mapper.AiMessageMapper;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话驱动系统操作服务（T-411）
 *
 * <p>链路：用户自然语言 → LLM 工具选择+参数抽取（JSON 契约校验，走网关）
 * → 声明式参数校验（必填/枚举/类型，仅索要业务必需字段）
 * → 权限校验（角色/精细权限点）→ 只读同步执行 / 写入待确认 → 结果按系统数据规范渲染。
 * LLM 失败/判定为聊天时优雅降级，不阻塞对话主流程。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemOpsService {

    /** 携带的对话历史条数 */
    private static final int CONTEXT_MESSAGES = 6;

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final Pattern CONFIRM = Pattern.compile("^(确认|确定|执行|是的|对|没错|ok|yes|是)[!！。.\\s]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCEL = Pattern.compile("^(取消|算了|不用了|否|no|不要)[!！。.\\s]*$", Pattern.CASE_INSENSITIVE);
    /** 写入类意图词（硬兜底：LLM 误判为 chat/agent 时强制重判定，杜绝"假执行"幻觉回复） */
    private static final Pattern WRITE_INTENT = Pattern.compile(
            "创建|新建|登记|录入|审批|批准|驳回|推进|流转|借阅|归还|绑定|解绑|处理|发起|提交|关闭|销毁|派发|指派");

    private final OpsToolRegistry registry;
    private final OpsPendingStore pendingStore;
    private final AiGatewayClient aiGatewayClient;
    private final AiMessageMapper messageMapper;
    private final FinePermissionService finePermissionService;
    private final ObjectMapper objectMapper;

    /** 输出 JSON 契约（网关侧校验 + 失败重生成一次） */
    private final Map<String, Object> decisionSchema = Map.of(
            "type", "object",
            "required", List.of("action"),
            "properties", Map.of(
                    "action", Map.of("enum", List.of("tool", "ask", "chat", "agent")),
                    "tool", Map.of("type", "string"),
                    "params", Map.of("type", "object"),
                    "ask", Map.of("type", "string"),
                    "reply", Map.of("type", "string")));

    /**
     * 系统操作路由入口：命中则返回助手消息；返回 null 表示与系统操作无关（交由通用对话）。
     * 由 AiChatService.routeAndReply 在 Agent 意图之后调用。
     */
    public AiMessage tryOps(AiConversation conv, String content, LoginUser user) {
        // 1. 待确认操作：确认/取消优先处理
        OpsPendingStore.PendingOp pending = pendingStore.peek(conv.getId());
        if (pending != null) {
            if (CONFIRM.matcher(content.trim()).matches()) {
                return executePending(conv, pending, user);
            }
            if (CANCEL.matcher(content.trim()).matches()) {
                pendingStore.remove(conv.getId());
                return saveMessage(conv.getId(), "已取消该操作。如仍需执行，请重新描述需求。");
            }
        }

        // 2. LLM 判定意图并抽取参数
        LlmDecision decision;
        try {
            decision = decide(conv, content, false);
            // 写入意图硬兜底：模型把写入请求误判为 chat/agent 时（会产生"假执行"幻觉回复），强制重判定
            if (decision != null && ("chat".equals(decision.action()) || "agent".equals(decision.action()))
                    && WRITE_INTENT.matcher(content).find()) {
                log.info("写入意图被误判为 {}，强制重判定: conv={}", decision.action(), conv.getId());
                decision = decide(conv, content, true);
                if (decision == null || !"tool".equals(decision.action())) {
                    return saveMessage(conv.getId(),
                            "识别到写入类操作意图，但未能确定具体操作与参数。请用更明确的表述，例如「创建任务：项目「X」任务名称「Y」」或「查一下任务」先核对信息。");
                }
            }
        } catch (Exception e) {
            log.warn("系统操作意图判定失败，转通用对话: conv={}", conv.getId(), e);
            return null;
        }
        if (decision == null) {
            return null;
        }
        return switch (decision.action()) {
            case "chat" -> StringUtils.hasText(decision.reply())
                    ? saveMessage(conv.getId(), decision.reply())
                    : null; // 模型未给答复则转通用对话兜底
            case "agent" -> null; // AI 分析类能力（知识库检索/报告生成/资格比对等）转交 Agent 体系
            case "ask" -> handleTool(conv, decision, user); // ask 与 tool 统一处理，追问文本由后端按声明生成
            case "tool" -> handleTool(conv, decision, user);
            default -> null;
        };
    }

    // ==================== 工具处理 ====================

    private AiMessage handleTool(AiConversation conv, LlmDecision decision, LoginUser user) {
        OpsTool tool = registry.find(decision.tool());
        if (tool == null) {
            return saveMessage(conv.getId(), "该操作暂不支持对话执行。你可以让我查询任务/项目/合同/标讯/档案等，或创建任务、推进状态、录入工时等。");
        }
        Map<String, Object> params = decision.params() == null ? Map.of() : decision.params();

        // 声明式参数校验：必填/枚举/类型（缺失追问由后端按参数声明生成，只问业务必需字段）
        String missing;
        try {
            missing = validate(tool, params);
        } catch (BusinessException e) {
            return saveMessage(conv.getId(), "「" + tool.getTitle() + "」参数有误：" + e.getMessage());
        }
        if (missing != null) {
            return saveMessage(conv.getId(), "执行「" + tool.getTitle() + "」还需要补充：" + missing);
        }

        // 权限校验
        String denied = checkPerm(tool, user);
        if (denied != null) {
            return saveMessage(conv.getId(), denied);
        }

        // 只读：同步执行；写入：待确认
        if (tool.isReadOnly()) {
            return execute(conv, tool, params);
        }
        pendingStore.put(conv.getId(), tool.getName(), params);
        return saveMessage(conv.getId(), "将执行「**" + tool.getTitle() + "**」（" + tool.getModule()
                + "），参数如下：\n\n" + describeParams(tool, params)
                + "\n\n回复「**确认**」执行，「**取消**」放弃（5 分钟内有效）。");
    }

    /** 执行待确认操作 */
    private AiMessage executePending(AiConversation conv, OpsPendingStore.PendingOp pending, LoginUser user) {
        OpsTool tool = registry.find(pending.toolName());
        pendingStore.remove(conv.getId());
        if (tool == null) {
            return saveMessage(conv.getId(), "该操作已失效，请重新描述需求。");
        }
        return execute(conv, tool, pending.params());
    }

    /** 统一执行：异常收敛为业务可读提示 */
    private AiMessage execute(AiConversation conv, OpsTool tool, Map<String, Object> params) {
        try {
            Object result = tool.getExecutor().apply(params, LoginUser.current());
            String rendered = result instanceof String s ? s : defaultRender(result);
            return saveMessage(conv.getId(), rendered);
        } catch (BusinessException e) {
            log.info("AI 操作执行被拦截: tool={}, msg={}", tool.getName(), e.getMessage());
            return saveMessage(conv.getId(), "「" + tool.getTitle() + "」未能执行：" + e.getMessage());
        } catch (Exception e) {
            log.error("AI 操作执行异常: tool={}", tool.getName(), e);
            return saveMessage(conv.getId(), "「" + tool.getTitle() + "」执行异常，请稍后重试或到对应页面人工操作。");
        }
    }

    // ==================== LLM 意图判定 ====================

    private LlmDecision decide(AiConversation conv, String content, boolean forceTool) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("今天是 ").append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                .append("。你是部门工作管理平台的 AI 操作助手，负责判定用户意图并抽取系统操作参数。\n\n");
        prompt.append(OpsToolRegistry.SYSTEM_KNOWLEDGE).append('\n');
        prompt.append("【可执行操作目录】（格式：工具名|版块|说明|参数；参数标记 必填/可选、类型、枚举值）\n")
                .append(registry.catalog()).append('\n');
        prompt.append("【对话历史】\n");
        List<AiMessage> history = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conv.getId())
                        .orderByDesc(AiMessage::getId)
                        .last("LIMIT " + (CONTEXT_MESSAGES + 1)));
        for (int i = history.size() - 1; i >= 1; i--) {
            AiMessage m = history.get(i);
            prompt.append(m.getRole().equals("user") ? "用户：" : "助手：")
                    .append(abbreviate(m.getContent(), 300)).append('\n');
        }
        prompt.append("\n【本轮用户消息】\n用户：").append(content).append('\n');
        if (forceTool) {
            prompt.append("""
                【强制要求】该消息包含明确的写入类操作意图（创建/登记/审批/借阅等），上一轮被误判。你必须从目录中选出最匹配的工具，输出 {"action":"tool","tool":"...","params":{...}}；参数只填用户已提供的，缺项留空由系统追问。禁止输出 chat/agent。只输出 JSON 本身。
                """);
        }
        prompt.append("""
                【判定规则】
                0. 用户消息含 查/查询/列表/看看/有哪些/多少/进度 等词且涉及目录版块 → 必须选对应查询工具（tool）；含 创建/新建/登记/录入/审批/推进/借阅/归还/绑定/处理 等词 → 必须选对应写入工具（tool），绝不允许用 chat 直接声称已创建/已完成（实际执行由系统完成，chat 只能输出文字）。
                1. 执行系统操作 → {"action":"tool","tool":"工具名","params":{...}}；参数取目录声明名，日期一律输出 yyyy-MM-dd（相对日期按今天换算），金额纯数字不带单位，项目用 projectName 名称；缺必填参数时仍选 tool，params 只填已知项（系统会自动追问缺项）。
                2. AI 分析类能力（日报/周报/会议纪要生成、标讯解析与资格比对、报价建议、竞品分析、方案起草、BOM 智能校验、成本自动归集、售后诊断建议、晨会解读等）→ {"action":"agent"}；知识库检索一律走 kb.search 工具。
                3. 普通咨询/闲聊/业务知识问答 → {"action":"chat","reply":"基于【系统版块知识】直接回答，500字内，禁止反问用户背景信息"}。
                4. 禁止编造工具名与参数；不确定时选 chat。只输出 JSON 本身，不要任何解释文字或 markdown 代码块。
                【示例】
                用户：查一下我的待办任务 → {"action":"tool","tool":"task.myTodo","params":{}}
                用户：查一下XX项目进行中的任务 → {"action":"tool","tool":"task.list","params":{"projectName":"XX","status":"进行中"}}
                用户：帮我创建一个任务（无其他信息） → {"action":"tool","tool":"task.create","params":{}}
                用户：帮我创建任务：项目「测试项目A」任务名「冒烟测试」优先级高 → {"action":"tool","tool":"task.create","params":{"projectName":"测试项目A","taskName":"冒烟测试","priority":"高"}}
                用户：我有哪些待审批的事件 → {"action":"tool","tool":"event.myPending","params":{}}
                用户：知识库里搜一下设备维护规范 → {"action":"tool","tool":"kb.search","params":{"query":"设备维护规范"}}
                用户：生成今天的日报 → {"action":"agent"}
                用户：合同的审批流程是什么 → {"action":"chat","reply":"合同按 草稿→会签→审批→用印→生效 流转，会签门要求四项收款比例合计100%，审批/用印仅部门领导可操作。"}
                """);

        String content1 = aiGatewayClient.chat("CAP_DOC_SUMMARY", prompt.toString(),
                LoginUser.current() == null ? null : LoginUser.current().getUserId(), decisionSchema);
        return parseDecision(content1);
    }

    private LlmDecision parseDecision(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String json = raw.trim();
        Matcher m = JSON_BLOCK.matcher(json);
        if (m.find()) {
            json = m.group(1).trim();
        }
        // 容忍前后杂文字：截取第一个 { 到最后一个 }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            String action = map.get("action") == null ? "chat" : String.valueOf(map.get("action"));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = map.get("params") instanceof Map<?, ?> p ? (Map<String, Object>) p : null;
            return new LlmDecision(action,
                    map.get("tool") == null ? null : String.valueOf(map.get("tool")),
                    params,
                    map.get("ask") == null ? null : String.valueOf(map.get("ask")),
                    map.get("reply") == null ? null : String.valueOf(map.get("reply")));
        } catch (Exception e) {
            log.warn("意图判定输出解析失败: {}", abbreviate(raw, 200));
            return null;
        }
    }

    // ==================== 校验 ====================

    /** 声明式校验；通过返回 null，否则返回缺失/错误说明 */
    private String validate(OpsTool tool, Map<String, Object> params) {
        List<String> missing = new java.util.ArrayList<>();
        for (OpsParam p : tool.getParams()) {
            Object v = params.get(p.getName());
            if (v == null || (v instanceof String s && !StringUtils.hasText(s))) {
                if (p.isRequired()) {
                    missing.add(p.getDesc() + (p.getEnums() == null ? "" : "（" + String.join("/", p.getEnums()) + "）"));
                }
                continue;
            }
            // 枚举强校验（模型输出不合规时纠正）
            if (p.getEnums() != null && !p.getEnums().contains(String.valueOf(v))) {
                throw new BusinessException(400, "参数「" + p.getDesc() + "」仅支持：" + String.join("/", p.getEnums()));
            }
        }
        return missing.isEmpty() ? null : String.join("；", missing);
    }

    /** 权限校验：部门领导兜底；角色与精细权限点双重声明 */
    private String checkPerm(OpsTool tool, LoginUser user) {
        boolean leader = user.getRoles() != null && user.getRoles().contains("ROLE_DEPT_LEADER");
        if (leader) {
            return null;
        }
        if (tool.getMinRole() != null && (user.getRoles() == null || !user.getRoles().contains(tool.getMinRole()))) {
            return "「" + tool.getTitle() + "」需要 " + roleCn(tool.getMinRole()) + " 权限，当前账号无权执行该操作。";
        }
        if (tool.getPermCode() != null && !finePermissionService.hasPerm(user.getUserId(), tool.getPermCode())) {
            return "「" + tool.getTitle() + "」需要接口权限点 " + tool.getPermCode() + "，当前账号未获授权。";
        }
        return null;
    }

    private String roleCn(String role) {
        return switch (role) {
            case "ROLE_DEPT_LEADER" -> "部门领导";
            case "ROLE_PROJECT_LEADER" -> "项目负责人";
            case "ROLE_REFORM_LEADER" -> "技改负责人";
            default -> role;
        };
    }

    // ==================== 渲染 ====================

    /** 待确认参数明细（中文名） */
    private String describeParams(OpsTool tool, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        for (OpsParam p : tool.getParams()) {
            Object v = params.get(p.getName());
            if (v != null && (!(v instanceof String s) || StringUtils.hasText(s))) {
                sb.append("- ").append(p.getDesc()).append("：").append(brief(v)).append('\n');
            }
        }
        return sb.toString();
    }

    /** 通用结果渲染兜底 */
    private String defaultRender(Object result) {
        if (result == null) {
            return "操作完成。";
        }
        return "操作结果：" + brief(result);
    }

    private String brief(Object v) {
        String s = String.valueOf(v);
        return s.length() > 200 ? s.substring(0, 200) + "…" : s.replace("\n", " ");
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private AiMessage saveMessage(Long convId, String content) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(convId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setMsgType("text");
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg;
    }

    /** LLM 判定结果 */
    record LlmDecision(String action, String tool, Map<String, Object> params, String ask, String reply) {
    }
}
