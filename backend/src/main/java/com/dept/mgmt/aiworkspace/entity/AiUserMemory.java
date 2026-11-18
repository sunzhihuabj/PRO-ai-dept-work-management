package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 用户长期记忆实体（P0-3 / V2.0 M9 完整闭环）
 *
 * <p>会话滚动摘要过程中抽取的稳定事实（如「用户主要跟三一重能项目」「周报偏好表格形式」），
 * <b>建议化写入</b>（PROPOSED，用户确认后 APPROVED 才参与召回），
 * 跨会话沉淀并在后续对话 prompt 中注入，实现「记得住用户」的长期记忆；
 * TTL 90 天自动过期，用户可随时查看/删除本人记忆（合规要求）。
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

    /** V2.0 M9：PROPOSED 待用户确认 / APPROVED 已生效（recall 只取 APPROVED） */
    private String status;

    /** V2.0 M9：TTL 过期时间（默认写入后 90 天），过期不再召回 */
    private java.time.LocalDateTime expireAt;

    /** V2.0 M9：SUMMARY 会话摘要抽取 / USER 用户手工 */
    private String source;
}
