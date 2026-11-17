package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.aiworkspace.entity.WfInstance;
import com.dept.mgmt.aiworkspace.entity.WfWorkflow;
import com.dept.mgmt.aiworkspace.service.AiChatService;
import com.dept.mgmt.aiworkspace.service.WorkflowEngineService;
import com.dept.mgmt.aiworkspace.service.WorkflowService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.fineperm.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统一 AI 助手工作台接口（T-408）：多轮对话 + 业务流程编排 + 实例生命周期
 *
 * @author dept-mgmt
 */
@Tag(name = "AI 工作台")
@RestController
@RequestMapping("/v1/ai/ws")
@RequiredArgsConstructor
@Validated
public class AiWorkspaceController {

    private final AiChatService chatService;
    private final WorkflowService workflowService;
    private final WorkflowEngineService engineService;
    private final com.dept.mgmt.aiworkspace.service.HermesDelegateService hermesDelegateService;

    // ==================== Hermes 委托（B2/B3） ====================

    @GetMapping("/hermes/runs/{runId}/events")
    @Operation(summary = "Hermes 委托执行事件时间线（B3 过程回写，仅限本人会话的委托任务）")
    public R<List<com.dept.mgmt.aiworkspace.entity.HermesRunEvent>> hermesRunEvents(
            @PathVariable String runId) {
        com.dept.mgmt.security.LoginUser user = com.dept.mgmt.security.LoginUser.current();
        return R.ok(hermesDelegateService.eventsForUser(user.getUserId(), runId));
    }

    // ==================== 对话 ====================

    @PostMapping("/conversations")
    @Operation(summary = "创建会话")
    public R<AiConversation> createConversation(@RequestBody ConversationDTO dto) {
        return R.ok("会话已创建", chatService.createConversation(dto.getTitle()));
    }

    @GetMapping("/conversations")
    @Operation(summary = "我的会话列表")
    public R<List<AiConversation>> conversations() {
        return R.ok(chatService.myConversations());
    }

    @PutMapping("/conversations/{id}")
    @Operation(summary = "重命名会话")
    public R<Void> renameConversation(@PathVariable Long id, @RequestBody ConversationDTO dto) {
        chatService.renameConversation(id, dto.getTitle());
        return R.ok("已重命名", null);
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话")
    public R<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return R.ok("已删除", null);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "会话消息历史")
    public R<List<AiMessage>> messages(@PathVariable Long id) {
        return R.ok(chatService.messages(id));
    }

    @PostMapping("/conversations/{id}/chat")
    @Operation(summary = "发送消息（意图路由：Agent 任务 / 流程生成 / 通用对话）")
    public R<Map<String, Object>> chat(@PathVariable Long id, @RequestBody ChatDTO dto) {
        return R.ok(chatService.chat(id, dto.getContent()));
    }

    @PostMapping("/conversations/{id}/regenerate")
    @Operation(summary = "重新生成最后一条回复（T-409 对齐豆包重试）")
    public R<Map<String, Object>> regenerate(@PathVariable Long id) {
        return R.ok(chatService.regenerate(id));
    }

    @PutMapping("/conversations/{id}/pin")
    @Operation(summary = "置顶/取消置顶会话（T-409）")
    public R<Void> pinConversation(@PathVariable Long id, @RequestBody PinDTO dto) {
        chatService.pinConversation(id, Boolean.TRUE.equals(dto.getPinned()));
        return R.ok("已更新", null);
    }

    @PostMapping("/messages/{id}/rating")
    @Operation(summary = "消息评分（1赞/-1踩/0清除，T-409）")
    public R<Void> rateMessage(@PathVariable Long id, @RequestBody RatingDTO dto) {
        if (dto.getRating() == null) {
            throw new BusinessException(400, "rating 不能为空");
        }
        chatService.rateMessage(id, dto.getRating());
        return R.ok("已评分", null);
    }

    // ==================== 流程定义 ====================

    @PostMapping("/workflows")
    @Operation(summary = "创建流程草稿")
    @RequirePerm("api:wf:manage")
    public R<WfWorkflow> createWorkflow(@RequestBody WorkflowDTO dto) {
        return R.ok("草稿已创建", workflowService.create(dto.getName(), dto.getDescription(), normalizeGraph(dto.getGraph())));
    }

    @GetMapping("/workflows")
    @Operation(summary = "流程列表（mine=true 仅我创建）")
    public R<List<WfWorkflow>> workflows(@RequestParam(required = false) Boolean mine,
                                         @RequestParam(required = false) String status) {
        return R.ok(workflowService.list(mine, status));
    }

    @GetMapping("/workflows/{id}")
    @Operation(summary = "流程详情（含 graph）")
    public R<WfWorkflow> workflowDetail(@PathVariable Long id) {
        return R.ok(workflowService.detail(id));
    }

    @PutMapping("/workflows/{id}")
    @Operation(summary = "更新流程（草稿/下线态）")
    @RequirePerm("api:wf:manage")
    public R<Void> updateWorkflow(@PathVariable Long id, @RequestBody WorkflowDTO dto) {
        workflowService.update(id, dto.getName(), dto.getDescription(), normalizeGraph(dto.getGraph()));
        return R.ok("已保存", null);
    }

    @DeleteMapping("/workflows/{id}")
    @Operation(summary = "删除流程（草稿/下线态）")
    @RequirePerm("api:wf:manage")
    public R<Void> deleteWorkflow(@PathVariable Long id) {
        workflowService.delete(id);
        return R.ok("已删除", null);
    }

    @PostMapping("/workflows/{id}/publish")
    @Operation(summary = "发布流程")
    @RequirePerm("api:wf:manage")
    public R<Void> publishWorkflow(@PathVariable Long id) {
        workflowService.publish(id);
        return R.ok("已发布", null);
    }

    @PostMapping("/workflows/{id}/offline")
    @Operation(summary = "下线流程")
    @RequirePerm("api:wf:manage")
    public R<Void> offlineWorkflow(@PathVariable Long id) {
        workflowService.offline(id);
        return R.ok("已下线", null);
    }

    // ==================== 流程实例（生命周期） ====================

    @PostMapping("/workflows/{id}/start")
    @Operation(summary = "启动流程实例")
    @RequirePerm("api:wf:start")
    public R<WfInstance> startInstance(@PathVariable Long id) {
        return R.ok("实例已启动", engineService.start(id));
    }

    @GetMapping("/instances")
    @Operation(summary = "实例列表")
    public R<List<WfInstance>> instances(@RequestParam(required = false) Long workflowId,
                                         @RequestParam(required = false) String status) {
        return R.ok(engineService.instances(workflowId, status));
    }

    @GetMapping("/instances/{id}")
    @Operation(summary = "实例详情（含节点运行状态）")
    public R<Map<String, Object>> instanceDetail(@PathVariable Long id) {
        return R.ok(engineService.instanceDetail(id));
    }

    @PostMapping("/instances/{id}/nodes/{nodeKey}/approve")
    @Operation(summary = "节点审批（pass=true 通过 / false 驳回）")
    @RequirePerm("api:wf:approve")
    public R<Void> approveNode(@PathVariable Long id, @PathVariable String nodeKey,
                               @RequestBody ApproveDTO dto) {
        if (dto.getPass() == null) {
            throw new BusinessException(400, "pass 参数不能为空");
        }
        engineService.approve(id, nodeKey, dto.getPass(), dto.getComment());
        return R.ok(dto.getPass() ? "已通过" : "已驳回", null);
    }

    @PostMapping("/instances/{id}/nodes/{nodeKey}/retry")
    @Operation(summary = "重试失败节点")
    @RequirePerm("api:wf:start")
    public R<Void> retryNode(@PathVariable Long id, @PathVariable String nodeKey) {
        engineService.retry(id, nodeKey);
        return R.ok("已重新发起", null);
    }

    @PostMapping("/instances/{id}/cancel")
    @Operation(summary = "取消实例")
    public R<Void> cancelInstance(@PathVariable Long id) {
        engineService.cancel(id);
        return R.ok("已取消", null);
    }

    // ==================== DTO ====================

    @Data
    public static class ConversationDTO {
        private String title;
    }

    @Data
    public static class ChatDTO {
        @NotBlank(message = "消息内容不能为空")
        private String content;
    }

    @Data
    public static class WorkflowDTO {
        private String name;
        private String description;
        /** 流程图：兼容 JSON 对象内嵌与字符串两种传输形态 */
        private com.fasterxml.jackson.databind.JsonNode graph;
    }

    /** graph 归一化：对象节点序列化为紧凑 JSON；文本节点取原值 */
    private String normalizeGraph(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.textValue() : node.toString();
    }

    @Data
    public static class ApproveDTO {
        private Boolean pass;
        private String comment;
    }

    @Data
    public static class PinDTO {
        private Boolean pinned;
    }

    @Data
    public static class RatingDTO {
        private Integer rating;
    }
}
