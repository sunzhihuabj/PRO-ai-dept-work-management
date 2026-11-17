package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.aiworkspace.entity.HermesRunEvent;
import com.dept.mgmt.mapper.AiConversationMapper;
import com.dept.mgmt.mapper.AiMessageMapper;
import com.dept.mgmt.mapper.HermesRunEventMapper;
import com.dept.mgmt.common.client.HermesService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import com.dept.mgmt.common.exception.BusinessException;

/**
 * Hermes 委托服务（B2：AI 助手第四级路由——复杂多步任务委托 AI Agent 员工）
 *
 * <p>插入位置：Agent 能力点（AGT-XX 专属卡片）未命中之后、通用对话之前——
 * Ops 单轮与 Agent 能力点已覆盖简单查询与固定能力，剩余「复杂多步/跨模块办理」
 * 指令委托 Hermes run 异步执行（经 MCP 驱动 27 个业务工具）。
 *
 * <p>执行语义：chat() 立即返回「执行中」卡片（不阻塞 HTTP 线程，run 分钟级耗时）；
 * 后台线程轮询至终态后把结果以 assistant 消息追加回原会话（前端刷新可见）。
 * Hermes 不可用 → 返回 null 降级通用对话，业务主流程无感。
 *
 * <p>委托判定为启发式规则（触发词/多步串联/排除纯查询）——不消耗 LLM 配额，
 * 规避 35 次/分限流；LLM 意图判别为阶段二优化点。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class HermesDelegateService {

    /** Hermes runId 内嵌标记（结果回写与 B3 过程关联用） */
    public static final String RUN_MARKER_PREFIX = "<!-- hermes-run:";
    public static final String RUN_MARKER_SUFFIX = " -->";

    /** 委托触发词（强意图） */
    private static final Pattern DELEGATE_TRIGGER = Pattern.compile(
            "(委托|交给|交给.{0,8}(agent|智能体|助手)|帮我(处理|办理|跟进|协调|排查|整理|起草)|"
                    + "跨模块|跨部门|生成.{0,12}(报告|周报|日报|月报|总结)|汇总|对比分析)");

    /** 多步串联信号（长度达标的组合指令） */
    private static final Pattern MULTI_STEP = Pattern.compile("(然后|之后|同时|再|并且)");

    /** 纯查询排除（简单查询走 Ops 单轮/通用对话，成本最优） */
    private static final Pattern PURE_QUERY = Pattern.compile(
            "^(查|看|看下|看看|问|告诉|什么|多少|是否|有没有|如何|怎么)");

    /** 过程事件入库白名单（B3）：丢弃 message.delta/reasoning.available 等逐 token 噪声流 */
    private static final Set<String> STORED_EVENT_TYPES = Set.of(
            "tool.started", "tool.completed", "approval.request",
            "message.interim", "subagent.start", "subagent.complete",
            "run.completed", "run.failed", "run.cancelled", "run.interrupted", "run.steered");

    /** Hermes 任务书系统约束（贴合系统数据规范，工具经 MCP 暴露） */
    private static final String TASK_BOOK_HEADER =
            "你是部门工作管理系统的 AI Agent 员工，以指定用户的身份执行任务。"
                    + "你可以通过 MCP 工具（dept-mgmt，工具名 mcp__dept_mgmt__*，共 27 个业务工具）"
                    + "查询与办理项目/任务/合同/投标/生产/交付/变更/成本/售后/档案/事件/知识库等业务。"
                    + "约束：①只执行用户明确要求的动作，不越权；②写入类操作如需确认会触发审批中断，属正常流程；"
                    + "③最终回复用简洁中文 Markdown 汇报结果与依据。\n\n";

    private final HermesService hermesService;
    private final AiMessageMapper messageMapper;
    private final AiConversationMapper conversationMapper;
    private final HermesRunEventMapper runEventMapper;

    /** 单线程后台执行器（daemon：JVM 退出不阻塞；并发上限由 Hermes 侧控制） */
    private final ExecutorService pollExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hermes-delegate-poll");
        t.setDaemon(true);
        return t;
    });

    public HermesDelegateService(HermesService hermesService,
                                 AiMessageMapper messageMapper,
                                 AiConversationMapper conversationMapper,
                                 HermesRunEventMapper runEventMapper) {
        this.hermesService = hermesService;
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.runEventMapper = runEventMapper;
    }

    /**
     * 第四级路由入口：判定通过且 Hermes 可用 → 提交 run 并回复执行中卡片；否则返回 null（降级）
     */
    public AiMessage tryDelegate(AiConversation conv, String content, Long userId, String username) {
        if (!shouldDelegate(content) || !hermesService.available()) {
            return null;
        }
        String runId = hermesService.submitRun(buildTaskBook(content, userId, username));
        if (!StringUtils.hasText(runId)) {
            hermesService.invalidateAvailability();
            return null;
        }
        AiMessage card = saveMessage(conv.getId(), "assistant",
                RUN_MARKER_PREFIX + runId + RUN_MARKER_SUFFIX
                        + "已委托 **AI Agent 员工**（Hermes）执行你的指令，任务书已受理（runId="
                        + abbreviate(runId, 18) + "…）。执行过程中如需敏感写操作会自动发起审批；"
                        + "结果完成后将自动回复在本会话中。",
                "hermes");
        // 后台轮询至终态并回写结果（不阻塞 chat HTTP 线程）
        Long convId = conv.getId();
        pollExecutor.submit(() -> {
            consumeAndStoreEvents(runId);
            pollAndWriteBack(convId, runId, content);
        });
        log.info("对话已委托 Hermes: conv={}, runId={}", convId, runId);
        return card;
    }

    /** 委托判定（启发式：触发词或组合指令，排除纯查询） */
    public boolean shouldDelegate(String content) {
        if (!StringUtils.hasText(content) || content.length() < 8) {
            return false;
        }
        if (PURE_QUERY.matcher(content).find() && content.length() < 30) {
            return false;
        }
        return DELEGATE_TRIGGER.matcher(content).find()
                || (content.length() >= 25 && MULTI_STEP.matcher(content).find());
    }

    /** 任务书组装（系统约束 + 委托人身份 + 用户指令） */
    public String buildTaskBook(String content, Long userId, String username) {
        return TASK_BOOK_HEADER
                + "【委托人】" + (StringUtils.hasText(username) ? username : "user-" + userId)
                + "（userId=" + userId + "，业务工具以该身份执行）\n\n"
                + "【任务】\n" + content;
    }

    /**
     * 消费 run 过程事件流并落库（B3 过程回写）：SSE 流关闭（终态后）或读超时即返回；
     * 噪声事件（message.delta/reasoning.available 等逐 token 流）不入库，(run_id, seq) 幂等去重；
     * 事件流失败静默降级——不影响 pollAndWriteBack 终态轮询回写
     */
    private void consumeAndStoreEvents(String runId) {
        hermesService.consumeEvents(runId, ev -> {
            String type = String.valueOf(ev.get("event"));
            if (!STORED_EVENT_TYPES.contains(type)) {
                return;
            }
            HermesRunEvent e = new HermesRunEvent();
            e.setRunId(runId);
            Object seq = ev.get("seq");
            e.setSeq(seq instanceof Number n ? n.longValue() : -1L);
            e.setEventType(type);
            Object tool = ev.get("tool");
            e.setTool(tool == null ? null : abbreviate(String.valueOf(tool), 128));
            e.setPreview(previewOf(ev));
            Object duration = ev.get("duration");
            e.setDurationMs(duration instanceof Number n ? Math.round(n.doubleValue() * 1000) : null);
            e.setIsError(Boolean.TRUE.equals(ev.get("error")) ? 1 : 0);
            e.setCreateTime(LocalDateTime.now());
            try {
                runEventMapper.insert(e);
            } catch (Exception dup) {
                // (run_id, seq) 冲突 = 重连重放已落库事件，幂等跳过
                log.debug("Hermes run 事件重复跳过: runId={}, seq={}", runId, e.getSeq());
            }
        });
    }

    /** 事件预览字段收敛（preview/text/error 兜底，截断 500 字符） */
    private static String previewOf(Map<String, Object> ev) {
        for (String key : new String[]{"preview", "text", "error"}) {
            Object v = ev.get(key);
            if (v instanceof String s && StringUtils.hasText(s)) {
                return abbreviate(s, 500);
            }
        }
        return null;
    }

    /**
     * 查询 run 执行事件时间线（B3）：仅限 runId 归属当前用户会话（ai_message 内嵌标记 → 会话归属校验）
     */
    public List<HermesRunEvent> eventsForUser(Long userId, String runId) {
        String marker = RUN_MARKER_PREFIX + runId + RUN_MARKER_SUFFIX;
        AiMessage msg = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                .likeRight(AiMessage::getContent, marker)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT 1"));
        if (msg == null) {
            throw new BusinessException(404, "委托任务不存在");
        }
        AiConversation conv = conversationMapper.selectById(msg.getConversationId());
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(404, "委托任务不存在");
        }
        return runEventMapper.selectList(new LambdaQueryWrapper<HermesRunEvent>()
                .eq(HermesRunEvent::getRunId, runId)
                .orderByAsc(HermesRunEvent::getSeq)
                .last("LIMIT 200"));
    }

    /** 后台轮询至终态并回写结果消息 */
    @SuppressWarnings("unchecked")
    private void pollAndWriteBack(Long convId, String runId, String instruction) {
        try {
            Map<String, Object> result = hermesService.awaitRun(runId);
            if (!HermesService.isTerminal(result)) {
                saveMessage(convId, "assistant",
                        "AI Agent 员工执行超时（runId=" + abbreviate(runId, 18)
                                + "…），已转入后台继续；可稍后重新发送指令查询进展。",
                        "text");
                hermesService.stopRun(runId);
                return;
            }
            if (HermesService.isSuccessful(result)) {
                String output = HermesService.extractOutput(result);
                saveMessage(convId, "assistant",
                        RUN_MARKER_PREFIX + runId + RUN_MARKER_SUFFIX
                                + "**AI Agent 员工执行完成**\n\n"
                                + (StringUtils.hasText(output) ? output : "（无文本输出）"),
                        "text");
            } else {
                Object err = result == null ? null : result.get("error");
                saveMessage(convId, "assistant",
                        RUN_MARKER_PREFIX + runId + RUN_MARKER_SUFFIX
                                + "**AI Agent 员工执行失败**（runId=" + abbreviate(runId, 18) + "…）："
                                + (err == null ? "执行异常终止" : err)
                                + "。可调整指令后重新委托，或转人工处理。",
                        "text");
            }
            // 刷新会话活跃时间（后台回写消息后前端可见）
            AiConversation conv = conversationMapper.selectById(convId);
            if (conv != null) {
                conv.setLastMsgTime(LocalDateTime.now());
                conversationMapper.updateById(conv);
            }
            log.info("Hermes 委托结果已回写: conv={}, runId={}, status={}",
                    convId, runId, result == null ? "null" : result.get("status"));
        } catch (Exception e) {
            log.warn("Hermes 委托结果回写异常: conv={}, runId={}, err={}", convId, runId, e.getMessage(), e);
        }
    }

    /** 消息落库（与 AiChatService.saveMessage 同语义；hermes 卡片走独立 msgType） */
    private AiMessage saveMessage(Long convId, String role, String content, String msgType) {
        AiMessage m = new AiMessage();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        m.setMsgType(msgType);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
        return m;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
