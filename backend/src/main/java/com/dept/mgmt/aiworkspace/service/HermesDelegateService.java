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
import org.springframework.beans.factory.annotation.Value;
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
    private final com.dept.mgmt.agent.collab.service.DecisionAdviceService adviceService;

    /** 轮询节奏与等待上限（与 app.hermes 对齐，B5 审批检测轮询复用） */
    @Value("${app.hermes.poll-interval-ms:2000}")
    private long pollIntervalMs;
    @Value("${app.hermes.await-timeout-ms:600000}")
    private long awaitTimeoutMs;

    /** 单线程后台执行器（daemon：JVM 退出不阻塞；并发上限由 Hermes 侧控制） */
    private final ExecutorService pollExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hermes-delegate-poll");
        t.setDaemon(true);
        return t;
    });

    /** 事件流消费执行器（独立于轮询线程：B5 起 run 可长时间停在 waiting_for_approval，SSE 流保持打开不能阻塞轮询） */
    private final ExecutorService eventsExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hermes-delegate-events");
        t.setDaemon(true);
        return t;
    });

    public HermesDelegateService(HermesService hermesService,
                                 AiMessageMapper messageMapper,
                                 AiConversationMapper conversationMapper,
                                 HermesRunEventMapper runEventMapper,
                                 com.dept.mgmt.agent.collab.service.DecisionAdviceService adviceService) {
        this.hermesService = hermesService;
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.runEventMapper = runEventMapper;
        this.adviceService = adviceService;
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
        // 后台：事件流消费（独立线程）+ 轮询至终态并回写结果（不阻塞 chat HTTP 线程）
        Long convId = conv.getId();
        Long uid = userId;
        eventsExecutor.submit(() -> consumeAndStoreEvents(runId));
        pollExecutor.submit(() -> pollAndWriteBack(convId, runId, uid));
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

    /**
     * 委托任务列表（C2 AI Agent 员工面板）：以 msg_type=hermes 卡片消息为委托主记录，
     * 关联事件终态 / 结果消息 / HERMES_APPROVAL 审批建议。数据范围：部门负责人看全部，其他用户仅看本人会话的委托。
     */
    public java.util.List<java.util.Map<String, Object>> pageDelegations(
            Long userId, boolean leader, int pageNum, int pageSize) {
        // 1) 数据范围：非负责人仅本人会话
        List<Long> convIds = null;
        if (!leader) {
            convIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getUserId, userId))
                    .stream().map(AiConversation::getId).toList();
            if (convIds.isEmpty()) {
                return List.of();
            }
        }
        // 2) 委托卡片消息分页（每条 = 一次委托）
        LambdaQueryWrapper<AiMessage> qw = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getMsgType, "hermes")
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + Math.min(Math.max(pageSize, 1), 50) + " OFFSET " + Math.max(pageNum - 1, 0) * pageSize);
        if (convIds != null) {
            qw.in(AiMessage::getConversationId, convIds);
        }
        List<AiMessage> cards = messageMapper.selectList(qw);
        if (cards.isEmpty()) {
            return List.of();
        }
        // 3) 组装：runId 解析 + 事件终态 + 结果消息 + 审批建议
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        Set<Long> convIdSet = cards.stream().map(AiMessage::getConversationId).collect(java.util.stream.Collectors.toSet());
        Map<Long, AiConversation> convMap = conversationMapper.selectBatchIds(convIdSet).stream()
                .collect(java.util.stream.Collectors.toMap(AiConversation::getId, c -> c));
        for (AiMessage card : cards) {
            String runId = extractRunId(card.getContent());
            if (!StringUtils.hasText(runId)) {
                continue;
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("runId", runId);
            row.put("messageId", card.getId());
            row.put("delegatedAt", card.getCreateTime());
            AiConversation conv = convMap.get(card.getConversationId());
            row.put("conversationId", card.getConversationId());
            row.put("conversationTitle", conv == null ? null : conv.getTitle());
            row.put("delegatorId", conv == null ? null : conv.getUserId());
            row.put("delegator", conv == null ? null : conv.getUserId());
            // 事件终态（hermes_run_event）
            List<HermesRunEvent> evts = runEventMapper.selectList(new LambdaQueryWrapper<HermesRunEvent>()
                    .eq(HermesRunEvent::getRunId, runId)
                    .orderByAsc(HermesRunEvent::getSeq).last("LIMIT 200"));
            String status = "RUNNING";
            String resultPreview = null;
            for (HermesRunEvent ev : evts) {
                if (ev.getEventType().startsWith("run.")) {
                    status = ev.getEventType().substring(4).toUpperCase();
                    resultPreview = ev.getPreview();
                }
            }
            row.put("status", status);
            row.put("eventCount", evts.size());
            // 结果消息（msg_type=text 同 runId 标记，B2 回写）
            AiMessage resultMsg = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                    .likeRight(AiMessage::getContent, RUN_MARKER_PREFIX + runId + RUN_MARKER_SUFFIX)
                    .eq(AiMessage::getMsgType, "text")
                    .orderByDesc(AiMessage::getId).last("LIMIT 1"));
            if (resultMsg != null) {
                String body = resultMsg.getContent().replace(RUN_MARKER_PREFIX + runId + RUN_MARKER_SUFFIX, "").trim();
                row.put("resultPreview", body.length() > 200 ? body.substring(0, 200) + "…" : body);
                // B2 早期委托无事件记录：从回写消息推断终态（事件缺失兜底）
                if ("RUNNING".equals(status)) {
                    if (body.contains("执行完成")) {
                        row.put("status", "COMPLETED");
                    } else if (body.contains("执行失败")) {
                        row.put("status", "FAILED");
                    }
                }
            } else {
                row.put("resultPreview", resultPreview);
            }
            // 关联审批建议（HERMES_APPROVAL）
            com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice advice =
                    adviceService.findPendingHermesApproval(runId);
            row.put("adviceId", advice == null ? null : advice.getId());
            row.put("adviceStatus", advice == null ? null : advice.getStatus());
            out.add(row);
        }
        return out;
    }

    /** 从消息 content 提取 runId 内嵌标记 */
    private static String extractRunId(String content) {
        if (content == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(RUN_MARKER_PREFIX) + "([a-zA-Z0-9_-]+)"
                        + java.util.regex.Pattern.quote(RUN_MARKER_SUFFIX))
                .matcher(content);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 后台轮询至终态并回写结果消息（B5 增强）：轮询中检测 waiting_for_approval——
     * 从 run 状态 approval 字段提取 requestId/摘要，经 DecisionAdviceService 生成
     * HERMES_APPROVAL 建议（幂等，同 requestId 只生成一次）；委托人采纳/驳回后
     * 回调 Hermes 恢复/终止 run，轮询继续至终态。超时兜底回写提示并 stopRun。
     */
    @SuppressWarnings("unchecked")
    private void pollAndWriteBack(Long convId, String runId, Long userId) {
        try {
            Map<String, Object> result = pollDetectingApproval(runId, userId);
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

    /**
     * 轮询等待终态（B5：审批中断检测）——status=waiting_for_approval 且 approval.request_id
     * 存在时生成 HERMES_APPROVAL 建议（幂等），委托人处理后 run 恢复执行；返回最后已知状态。
     */
    private Map<String, Object> pollDetectingApproval(String runId, Long userId) {
        long deadline = System.currentTimeMillis() + awaitTimeoutMs;
        Map<String, Object> last = null;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> status = hermesService.getRun(runId);
            if (status != null) {
                last = status;
                String st = String.valueOf(status.get("status"));
                if (HermesService.isTerminal(status)) {
                    return status;
                }
                if ("waiting_for_approval".equals(st)) {
                    detectApprovalRequest(runId, status, userId);
                }
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        log.warn("Hermes 委托轮询超时: runId={}, timeoutMs={}", runId, awaitTimeoutMs);
        return last;
    }

    /** 从 run 状态提取审批请求并生成建议（异常静默：建议失败不影响轮询） */
    @SuppressWarnings("unchecked")
    private void detectApprovalRequest(String runId, Map<String, Object> status, Long userId) {
        try {
            Object approval = status.get("approval");
            if (!(approval instanceof Map<?, ?> ap)) {
                return;
            }
            Object requestId = ((Map<String, Object>) ap).get("request_id");
            if (requestId == null || String.valueOf(requestId).isBlank()) {
                return;
            }
            String summary = String.valueOf(((Map<String, Object>) ap).getOrDefault(
                    "description", ((Map<String, Object>) ap).getOrDefault("command", "")));
            adviceService.createHermesApproval(runId, String.valueOf(requestId),
                    abbreviate(summary, 200), userId);
        } catch (Exception e) {
            log.warn("Hermes 审批建议生成失败（继续轮询）: runId={}, err={}", runId, e.getMessage());
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
