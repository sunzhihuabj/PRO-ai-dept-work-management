package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体（T-408）
 * 无逻辑删除/更新语义：消息只追加不修改，故不继承 BaseEntity
 *
 * @author dept-mgmt
 */
@Data
@TableName("ai_message")
public class AiMessage {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 角色（user/assistant） */
    private String role;

    /** 消息内容（Markdown） */
    private String content;

    /** 消息类型（text/task/workflow） */
    private String msgType;

    /** 关联 Agent 任务ID（msg_type=task） */
    private Long refTaskId;

    /** 关联流程草稿ID（msg_type=workflow） */
    private Long refWorkflowId;

    /** 用户评分（1赞/-1踩/NULL未评，仅 assistant 消息，T-409） */
    private Integer rating;

    /** 创建时间 */
    private LocalDateTime createTime;
}
