package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.aiworkspace.entity.WfWorkflow;
import com.dept.mgmt.aiworkspace.ops.SystemOpsService;
import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.agent.service.MasterAgentService;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AiConversationMapper;
import com.dept.mgmt.mapper.AiMessageMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一 AI 助手对话服务（T-408A）
 *
 * <p>路由策略：用户消息先经 MasterAgent 意图识别——
 * 命中能力点 → 创建 Agent 任务并回复任务卡片消息（前端轮询任务状态实时反馈）；
 * 含流程创建意图 → 生成流程草稿并回复流程卡片；
 * 未命中 → 走 AI 网关通用对话（携带会话最近历史作为多轮上下文）。
 * 红线：模型调用统一经 AiGatewayClient，不直连模型 SDK。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 通用对话携带的历史消息条数（多轮上下文窗口） */
    private static final int CONTEXT_MESSAGES = 10;

    /** 通用对话走摘要能力通道（当前已注册可用的对话型能力点） */
    private static final String CAP_GENERAL_CHAT = "CAP_DOC_SUMMARY";

    /** 流程创建意图（允许动词与"流程"之间出现名称引注等修饰，用于 NL 生成流程草稿） */
    private static final Pattern WF_CREATE_PATTERN =
            Pattern.compile("(创建|新建|搭建|生成|建)[^\\n]{0,30}流程|流程(编排|设计)");

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final MasterAgentService masterAgentService;
    private final AiGatewayClient aiGatewayClient;
    private final CodeEngineService codeEngineService;
    private final WorkflowService workflowService;
    private final SystemOpsService systemOpsService;

    // ==================== 会话管理 ====================

    /** 创建会话 */
    public AiConversation createConversation(String title) {
        LoginUser user = current();
        AiConversation conv = new AiConversation();
        conv.setConvNo(codeEngineService.nextCode("CONV", null));
        conv.setTitle(StringUtils.hasText(title) ? title : "新对话");
        conv.setUserId(user.getUserId());
        conv.setPinned(0);
        conv.setLastMsgTime(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    /** 我的会话列表（置顶优先，按最后消息时间倒序） */
    public List<AiConversation> myConversations() {
        LoginUser user = current();
        return conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, user.getUserId())
                .orderByDesc(AiConversation::getPinned)
                .orderByDesc(AiConversation::getLastMsgTime)
                .last("LIMIT 100"));
    }

    /** 重命名会话（仅归属人） */
    public void renameConversation(Long id, String title) {
        AiConversation conv = mustOwn(id);
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(400, "标题不能为空");
        }
        conv.setTitle(title.trim());
        conversationMapper.updateById(conv);
    }

    /** 删除会话（逻辑删除 + 消息物理清理） */
    public void deleteConversation(Long id) {
        mustOwn(id);
        conversationMapper.deleteById(id);
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, id));
    }

    /** 消息历史（正序） */
    public List<AiMessage> messages(Long conversationId) {
        mustOwn(conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId)
                .last("LIMIT 200"));
    }

    // ==================== 对话核心 ====================

    /**
     * 发送消息并获取助手回复（同步返回落库后的助手消息；任务类消息为异步卡片，前端轮询任务状态）
     */
    public Map<String, Object> chat(Long conversationId, String content) {
        LoginUser user = current();
        if (!StringUtils.hasText(content) || content.length() > 4000) {
            throw new BusinessException(400, "消息为空或超过 4000 字符");
        }
        AiConversation conv = mustOwn(conversationId);

        // 首条消息自动生成会话标题（截断 20 字）
        long msgCount = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
        if (msgCount == 0) {
            String t = content.trim();
            conv.setTitle(t.length() > 20 ? t.substring(0, 20) : t);
            conversationMapper.updateById(conv);
        }

        // 1. 落用户消息
        AiMessage userMsg = saveMessage(conversationId, "user", content, "text", null, null);

        // 2. 路由：流程创建意图 → 任务意图 → 通用对话
        AiMessage assistantMsg = routeAndReply(conv, content, user.getUserId());

        // 3. 刷新会话活跃时间
        conv.setLastMsgTime(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return Map.of("userMessage", userMsg, "assistantMessage", assistantMsg);
    }

    /**
     * 意图路由并生成助手回复（chat 与 regenerate 共用）：
     * 流程创建意图 → 系统操作意图（T-411 对话驱动全系统操作，含工具执行/参数追问/闲聊回复，
     * 判定为 AI 分析类能力时返回 null 转交 Agent）→ 任务意图 → 通用对话
     */
    private AiMessage routeAndReply(AiConversation conv, String content, Long userId) {
        if (WF_CREATE_PATTERN.matcher(content).find()) {
            return handleWorkflowIntent(conv, content);
        }
        AiMessage assistantMsg = systemOpsService.tryOps(conv, content, current());
        if (assistantMsg == null) {
            assistantMsg = tryAgentTask(conv, content, userId);
        }
        if (assistantMsg == null) {
            assistantMsg = generalChat(conv, content);
        }
        return assistantMsg;
    }

    /**
     * 重新生成（T-409 对齐豆包重试）：删除最后一条用户消息之后的助手回复，重新路由生成。
     * 任务类消息删除仅移除卡片，Agent 任务本身保留留痕。
     */
    public Map<String, Object> regenerate(Long conversationId) {
        LoginUser user = current();
        AiConversation conv = mustOwn(conversationId);
        AiMessage lastUser = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, "user")
                .orderByDesc(AiMessage::getId)
                .last("LIMIT 1"));
        if (lastUser == null) {
            throw new BusinessException(400, "会话中没有可重新生成的用户消息");
        }
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, "assistant")
                .gt(AiMessage::getId, lastUser.getId()));
        AiMessage assistantMsg = routeAndReply(conv, lastUser.getContent(), user.getUserId());
        conv.setLastMsgTime(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return Map.of("userMessage", lastUser, "assistantMessage", assistantMsg);
    }

    /** 置顶/取消置顶会话（T-409 对齐豆包） */
    public void pinConversation(Long id, boolean pinned) {
        AiConversation conv = mustOwn(id);
        conv.setPinned(pinned ? 1 : 0);
        conversationMapper.updateById(conv);
    }

    /** 消息评分（1 赞 / -1 踩 / 0 清除，仅 assistant 消息；评分沉淀用于后续回复质量分析） */
    public void rateMessage(Long messageId, int rating) {
        LoginUser user = current();
        AiMessage msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new BusinessException(404, "消息不存在");
        }
        AiConversation conv = conversationMapper.selectById(msg.getConversationId());
        if (conv == null || !conv.getUserId().equals(user.getUserId())) {
            throw new BusinessException(404, "消息不存在");
        }
        if (!"assistant".equals(msg.getRole())) {
            throw new BusinessException(400, "仅可对 AI 回复评分");
        }
        if (rating != 1 && rating != -1 && rating != 0) {
            throw new BusinessException(400, "评分仅允许 1/-1/0");
        }
        if (rating == 0) {
            // 清除评分：MP updateById 忽略 null 字段，需用 UpdateWrapper 显式 set null
            messageMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiMessage>()
                    .eq(AiMessage::getId, messageId)
                    .set(AiMessage::getRating, null));
            return;
        }
        msg.setRating(rating);
        messageMapper.updateById(msg);
    }

    /**
     * Agent 能力路由：意图命中则建任务并回复任务卡片；意图不明返回 null（交由通用对话）
     */
    private AiMessage tryAgentTask(AiConversation conv, String content, Long userId) {
        try {
            AgentTask task = masterAgentService.submitAs(content, null, null, userId);
            AiMessage msg = saveMessage(conv.getId(), "assistant",
                    "已受理为 Agent 任务 **" + task.getTaskNo() + "**（能力 " + task.getCapabilityCode()
                            + "，意图置信度 " + String.format("%.0f%%", task.getIntentConfidence() * 100) + "），执行结果将实时更新在下方卡片中。",
                    "task", task.getId(), null);
            log.info("对话路由至 Agent 任务: conv={}, taskNo={}", conv.getConvNo(), task.getTaskNo());
            return msg;
        } catch (BusinessException e) {
            // 意图不明（400）→ 交由通用对话；其他业务异常同样降级为通用对话并记录
            log.debug("Agent 意图未命中，转通用对话: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 流程创建意图：模板引擎生成标准流程草稿（名称取自指令），回复流程卡片
     */
    private AiMessage handleWorkflowIntent(AiConversation conv, String content) {
        String name = extractWorkflowName(content);
        WfWorkflow draft = workflowService.createFromTemplate(name, current().getUsername());
        return saveMessage(conv.getId(), "assistant",
                "已为你生成业务流程草稿 **「" + name + "」**（含 开始 → 能力执行 → 人工审批 → 结果通知 四个标准节点）。\n\n"
                        + "请在左侧「流程」面板中打开该草稿，可拖拽调整节点、配置能力点/审批人后发布运行。",
                "workflow", null, draft.getId());
    }

    /** 从指令提取流程名（去掉意图动词，取剩余核心词，默认"未命名流程"） */
    private String extractWorkflowName(String content) {
        Matcher m = Pattern.compile("[「\\\"']([^」\\\"']{2,30})[」\\\"']").matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        String cleaned = content.replaceAll("(帮我|请|我想)?(创建|新建|搭建|生成|建)(一?个)?[^\\n]{0,30}流程", "")
                .replaceAll("[，。,.!！?？\\s]", "").trim();
        return StringUtils.hasText(cleaned) && cleaned.length() >= 2
                ? (cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned) : "未命名流程";
    }

    /**
     * 通用 LLM 对话：携带会话最近历史作为多轮上下文
     */
    private AiMessage generalChat(AiConversation conv, String content) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是部门工作管理平台的 AI 助手，请用简洁专业的中文回答用户问题，")
                .append("与部门工作管理（项目/任务/合同/流程等）相关的建议要贴合业务实际。\n\n【对话历史】\n");
        List<AiMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conv.getId())
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + (CONTEXT_MESSAGES + 1)));
        for (int i = history.size() - 1; i >= 1; i--) {
            AiMessage m = history.get(i);
            prompt.append(m.getRole().equals("user") ? "用户：" : "助手：")
                    .append(abbreviate(m.getContent(), 500)).append('\n');
        }
        prompt.append("\n【本轮用户消息】\n用户：").append(content);
        String reply;
        try {
            reply = aiGatewayClient.chat(CAP_GENERAL_CHAT, prompt.toString(),
                    LoginUser.current() == null ? null : LoginUser.current().getUserId(), null);
        } catch (Exception e) {
            log.error("AI 网关对话失败: conv={}", conv.getId(), e);
            reply = "抱歉，AI 服务暂时不可用，请稍后重试。若需执行业务操作，可直接描述任务（如：检索知识库 XX）。";
        }
        return saveMessage(conv.getId(), "assistant", reply, "text", null, null);
    }

    // ==================== 内部工具 ====================

    private AiMessage saveMessage(Long convId, String role, String content, String msgType, Long refTaskId, Long refWorkflowId) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(convId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setRefTaskId(refTaskId);
        msg.setRefWorkflowId(refWorkflowId);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg;
    }

    private AiConversation mustOwn(Long id) {
        LoginUser user = current();
        AiConversation conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(user.getUserId())) {
            throw new BusinessException(404, "会话不存在");
        }
        return conv;
    }

    private LoginUser current() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
