package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 用户长期记忆实体（P0-3）
 *
 * <p>会话滚动摘要过程中抽取的稳定事实（如「用户主要跟三一重能项目」「周报偏好表格形式」），
 * 跨会话沉淀并在后续对话 prompt 中注入，实现「记得住用户」的长期记忆。
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_user_memory")
public class AiUserMemory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 归属用户ID */
    private Long userId;

    /** 来源会话ID（溯源用，可空） */
    private Long conversationId;

    /** 记忆类型（fact稳定事实/preference偏好） */
    private String kind;

    /** 记忆内容（一句话事实） */
    private String content;

    /** 置信度（0-1，摘要抽取默认 0.8） */
    private Double confidence;
}
