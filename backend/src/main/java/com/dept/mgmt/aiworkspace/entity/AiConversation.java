package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体（T-408 统一 AI 助手工作台）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 会话编号（唯一） */
    private String convNo;

    /** 会话标题（默认取首条消息截断） */
    private String title;

    /** 归属用户ID */
    private Long userId;

    /** 是否置顶（0否 1是） */
    private Integer pinned;

    /** 最后消息时间（列表排序） */
    private LocalDateTime lastMsgTime;
}
