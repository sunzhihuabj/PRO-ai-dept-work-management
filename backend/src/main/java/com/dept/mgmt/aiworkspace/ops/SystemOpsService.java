package com.dept.mgmt.aiworkspace.ops;

import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.aiworkspace.service.AiMemoryService;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.SysUser;
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
 * 对话驱动系统操作服务（T-411 / P0 智能化改造）
 *
 * <p>链路：用户自然语言 → LLM 工具选择+参数抽取（JSON 契约校验，走网关）
 * → 声明式参数校验（必填/枚举/类型）→ 语义校验（P0-4：实体存在性/歧义澄清/写入前置校验）
 * → 权限校验（角色/精细权限点）→ 只读同步执行 / 写入待确认 → 结果按系统数据规范渲染。
 *
 * <p>P0 改造要点：
 * <ul>
 * <li><b>渐进披露（P0-1）</b>：decide() 只常驻注入全局口径 + 版块摘要目录（约 400 tokens），
 *     本轮消息命中版块触发词后才注入该版块完整业务口径与参数详情，token 成本降约 50%。</li>
 * <li><b>记忆注入（P0-3）</b>：decide() 携带会话滚动摘要与用户长期记忆，长会话不丢上下文。</li>
 * <li><b>语义校验（P0-4）</b>：执行前对项目名/负责人做存在性与歧义校验（命中多个列候选让用户澄清），
 *     写入类工具确认前先核对目标实体存在，杜绝「确认后才发现单号无效」。</li>
 * </ul>
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
    private final OpsSkillCards skillCards;
    private final OpsPendingStore pendingStore;
    private final AiGatewayClient aiGatewayClient;
    private final AiMessageMapper messageMapper;
    private final FinePermissionService finePermissionService;
    private final AiMemoryService aiMemoryService;
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
     * V2.0 D7 回归集纯判定入口：本地技能卡触发词预筛（零 LLM 成本），
     * 与 tryOps 的渐进披露预筛同一数据源——命中仅代表 OPS 路由的强信号。
     */
    public boolean hasOpsTrigger(String content) {
        return !skillCards.match(content).isEmpty();
    }

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
            // 工具名纠错重判定（C3 联调修复）：模型编造目录外工具名时（如 task.count），
            // 带无效名提示重判一次，仍无效才走兜底文案——避免把可正常路由的查询打成"暂不支持"
            if (decision != null && "tool".equals(decision.action())
                    && registry.find(decision.tool()) == null) {
                log.info("工具名不在目录（{}），纠错重判定: conv={}", decision.tool(), conv.getId());
                decision = decide(conv, content, false, decision.tool());
                if (decision == null || !"tool".equals(decision.action())
                        || registry.find(decision.tool()) == null) {
                    return saveMessage(conv.getId(),
                            "该操作暂不支持对话执行。你可以让我查询任务/项目/合同/标讯/档案等，或创建任务、推进状态、录入工时等。");
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
        // LLM 输出的 params 可能为不可变 Map，复制为可变以支持语义校验的规范化写回
        Map<String, Object> params = decision.params() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(decision.params());

        // 1. 声明式参数校验：必填/枚举/类型（缺失追问由后端按参数声明生成，只问业务必需字段）
        String missing;
        try {
            missing = validate(tool, params);
        } catch (BusinessException e) {
            return saveMessage(conv.getId(), "「" + tool.getTitle() + "」参数有误：" + e.getMessage());
        }
        if (missing != null) {
            return saveMessage(conv.getId(), "执行「" + tool.getTitle() + "」还需要补充：" + missing);
        }

        // 2. 语义校验（P0-4）：实体存在性/歧义澄清 → 写入前置校验；失败直接给出业务可读提示
        String semantic = semanticValidate(tool, params, user);
        if (semantic != null) {
            return saveMessage(conv.getId(), semantic);
        }

        // 3. 权限校验
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

    /**
     * 语义校验（P0-4）：对声明 resolve 的参数做实体匹配——
     * 未找到→提示核对；命中多个→列候选请用户澄清；唯一→把参数规范化为标准名称。
     * 随后对写入类工具执行前置校验（目标实体存在性），失败文案直接返回给用户。
     * 返回 null=全部通过。
     */
    String semanticValidate(OpsTool tool, Map<String, Object> params, LoginUser user) {
        for (OpsParam p : tool.getParams()) {
            if (p.getResolve() == null) {
                continue;
            }
            Object raw = params.get(p.getName());
            if (raw == null || (raw instanceof String s && !StringUtils.hasText(s))) {
                continue; // 缺省参数走必填校验追问，不在此重复
            }
            String value = String.valueOf(raw);
            switch (p.getResolve()) {
                case "project" -> {
                    OpsToolRegistry.EntityMatch<Project> match = registry.matchProject(value);
                    if (match.entity() == null) {
                        if (!match.candidates().isEmpty()) {
                            return "项目「" + value + "」匹配到多个，请指明是哪一个：\n"
                                    + numbered(match.candidates());
                        }
                        return "未找到项目「" + value + "」，请确认项目名称（可先说「查一下项目」核对）";
                    }
                    // 规范化：口语称呼 → 库内标准项目名，执行器与确认回执均使用标准名
                    params.put(p.getName(), match.entity().getProjectName());
                }
                case "user" -> {
                    OpsToolRegistry.EntityMatch<SysUser> match = registry.matchAssignee(value);
                    if (match.entity() == null) {
                        if (!match.candidates().isEmpty()) {
                            return "成员「" + value + "」匹配到多位，请指明是哪一位：\n"
                                    + numbered(match.candidates());
                        }
                        return "未找到成员「" + value + "」，请确认负责人姓名或账号";
                    }
                    params.put(p.getName(), match.entity().getUsername());
                }
                default -> {
                }
            }
        }
        // 写入类工具前置校验：确认执行前先核对目标实体存在
        if (tool.getPrecheck() != null) {
            try {
                String err = tool.getPrecheck().apply(params, user);
                if (err != null) {
                    return err;
                }
            } catch (BusinessException e) {
                return "「" + tool.getTitle() + "」无法执行：" + e.getMessage();
            }
        }
        return null;
    }

    /** 候选清单编号渲染（1. PRJ-xxx 项目名） */
    private String numbered(List<String> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(i + 1).append(". ").append(candidates.get(i)).append('\n');
        }
        return sb.toString();
    }

    /** 执行待确认操作（H2 整改：确认前二次复核权限与前置校验——待确认期间权限/目标状态可能已变化） */
    private AiMessage executePending(AiConversation conv, OpsPendingStore.PendingOp pending, LoginUser user) {
        OpsTool tool = registry.find(pending.toolName());
        if (tool == null) {
            pendingStore.remove(conv.getId());
            return saveMessage(conv.getId(), "该操作已失效，请重新描述需求。");
        }
        String denied = checkPerm(tool, user);
        if (denied != null) {
            pendingStore.remove(conv.getId());
            return saveMessage(conv.getId(), "待确认操作已取消：" + denied);
        }
        String semantic = semanticValidate(tool, pending.params(), user);
        if (semantic != null) {
            pendingStore.remove(conv.getId());
            return saveMessage(conv.getId(), "待确认操作已取消：" + semantic);
        }
        pendingStore.remove(conv.getId());
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

    // ==================== LLM 意图判定（渐进披露 P0-1 + 记忆注入 P0-3） ====================

    private LlmDecision decide(AiConversation conv, String content, boolean forceTool) {
        return decide(conv, content, forceTool, null);
    }

    /** invalidTool 非空时注入纠错提示（C3 联调修复：模型编造目录外工具名的重判定） */
    private LlmDecision decide(AiConversation conv, String content, boolean forceTool, String invalidTool) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("今天是 ").append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                .append("。你是部门工作管理平台的 AI 操作助手，负责判定用户意图并抽取系统操作参数。\n\n");
        // 第一层：全局口径 + 版块摘要目录（常驻，约 400 tokens）
        prompt.append(OpsSkillCards.GLOBAL_RULES).append('\n');
        prompt.append("【可执行操作目录】（每行：版块名（一句话能力）：该版块工具名清单；工具名自解释：版块.动作）\n")
                .append(skillCards.briefCatalog()).append('\n');
        // 第二层：命中版块触发词才注入完整业务口径 + 该版块工具参数详情
        List<OpsSkillCards.OpsSkillCard> hits = skillCards.match(content);
        if (hits.isEmpty()) {
            prompt.append("（本轮消息未命中具体版块：如需执行系统操作请指明版块，例如「查一下我的任务」；上文目录的参数详情从略）\n");
        } else {
            for (OpsSkillCards.OpsSkillCard card : hits) {
                prompt.append("【命中版块：").append(card.name()).append("】\n")
                        .append(card.knowledge()).append('\n')
                        .append("【该版块工具参数详情】（格式：工具名|版块|说明|参数；参数标记 必填/可选、类型、枚举值）\n")
                        .append(registry.catalog(card.name())).append('\n');
            }
        }
        // 记忆注入（P0-3）：会话滚动摘要 + 用户长期事实
        if (StringUtils.hasText(conv.getSummary())) {
            prompt.append("【会话此前摘要】\n").append(conv.getSummary()).append('\n');
        }
        List<String> memories = aiMemoryService.recall(conv.getUserId());
        if (!memories.isEmpty()) {
            prompt.append("【用户长期记忆】（回答与抽取参数时可参考）\n");
            for (String m : memories) {
                prompt.append("- ").append(m).append('\n');
            }
        }
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
        if (StringUtils.hasText(invalidTool)) {
            prompt.append("【纠错要求】上一轮输出的工具名「").append(invalidTool)
                    .append("""
                不在目录中。你必须从【可执行操作目录】里原样选择一个真实存在的工具名，输出 {"action":"tool","tool":"目录中的工具名","params":{...}}；若目录确实没有匹配工具才允许输出 chat。禁止编造工具名。只输出 JSON 本身。
                """);
        }
        prompt.append("""
                【判定规则】
                0. 用户消息含 查/查询/列表/看看/有哪些/多少/进度 等词且涉及目录版块 → 必须选对应查询工具（tool）；含 创建/新建/登记/录入/审批/推进/借阅/归还/绑定/处理 等词 → 必须选对应写入工具（tool），绝不允许用 chat 直接声称已创建/已完成（实际执行由系统完成，chat 只能输出文字）。
                1. 执行系统操作 → {"action":"tool","tool":"工具名","params":{...}}；参数取目录声明名，日期一律输出 yyyy-MM-dd（相对日期按今天换算），金额纯数字不带单位，项目用 projectName 名称；缺必填参数时仍选 tool，params 只填已知项（系统会自动追问缺项）。
                2. AI 分析类能力（日报/周报/会议纪要生成、标讯解析与资格比对、报价建议、竞品分析、方案起草、BOM 智能校验、成本自动归集、售后诊断建议、晨会解读等）→ {"action":"agent"}；知识库检索一律走 kb.search 工具。
                3. 普通咨询/闲聊/业务知识问答 → {"action":"chat","reply":"基于命中版块业务口径直接回答，500字内，禁止反问用户背景信息"}。
                4. 禁止编造工具名与参数；不确定时选 chat。只输出 JSON 本身，不要任何解释文字或 markdown 代码块。
                【示例】
                用户：查一下我的待办任务 → {"action":"tool","tool":"task.myTodo","params":{}}
                用户：查一下XX项目进行中的任务 → {"action":"tool","tool":"task.list","params":{"projectName":"XX","status":"进行中"}}
                用户：帮我创建一个任务（无其他信息） → {"action":"tool","tool":"task.create","params":{}}
                用户：帮我创建任务：项目「测试项目A」任务名「冒烟测试」优先级高 → {"action":"tool","tool":"task.create","params":{"projectName":"测试项目A","taskName":"冒烟测试","priority":"高"}}
                用户：我有哪些待审批的事件 → {"action":"tool","tool":"event.myPending","params":{}}
                用户：知识库里搜一下设备维护规范 → {"action":"tool","tool":"kb.search","params":{"query":"设备维护规范"}}
                用户：生成今天的日报 → {"action":"agent"}
                用户：合同的审批流程是什么 → {"action":"chat","reply":"合同按 草稿→会签→审批→用印→生效 流转，会签门要求四项收款比例合计100%，审批/印鉴仅部门领导可操作。"}
                """);

        String content1 = aiGatewayClient.chat("CAP_DOC_SUMMARY", prompt.toString(),
                LoginUser.current() == null ? null : LoginUser.current().getUserId(), decisionSchema);
        return parseDecision(content1);
    }

    LlmDecision parseDecision(String raw) {
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
    String validate(OpsTool tool, Map<String, Object> params) {
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

    /**
     * 权限校验：部门领导兜底；角色与精细权限点双重声明。
     * H2 整改：写入类工具未声明 minRole/permCode 时不再默认放行——
     * 挂了 precheck 的写入工具由 precheck 做目标存在性与归属判定（如任务仅负责人本人可操作）；
     * 既无权限声明也无 precheck 的写入工具，按项目负责人基线判定。
     */
    String checkPerm(OpsTool tool, LoginUser user) {
        boolean leader = user.getRoles() != null && user.getRoles().contains("ROLE_DEPT_LEADER");
        if (leader) {
            return null;
        }
        boolean hasPermDecl = tool.getMinRole() != null || tool.getPermCode() != null;
        if (!tool.isReadOnly() && !hasPermDecl) {
            if (tool.getPrecheck() == null) {
                // 无权限声明且无前置校验的写入：按项目负责人基线兜底（默认拒绝未授权角色）
                if (user.getRoles() == null || !user.getRoles().contains("ROLE_PROJECT_LEADER")) {
                    return "「" + tool.getTitle() + "」为写入操作，需要 项目负责人 或部门领导权限，当前账号无权执行该操作。";
                }
                return null;
            }
            // 有 precheck 的写入：由语义校验阶段的 precheck 做目标存在性与归属判定
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

    /**
     * MCP 层权限校验入口（A4 身份透传：按 X-Acting-User-Id 对应用户逐调用校验）。
     * 复用 checkPerm 语义：部门领导兜底；minRole/permCode 双重声明；写入基线兜底。
     * 通过返回 null；否则返回业务可读的拒绝文案（MCP 层转为 isError=true 结果）。
     */
    public String checkPermission(OpsTool tool, LoginUser user) {
        return checkPerm(tool, user);
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
