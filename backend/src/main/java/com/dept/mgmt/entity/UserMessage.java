package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户消息表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_message")
public class UserMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 接收用户ID */
    private Long userId;

    /** 消息类型（任务提醒/审核通知/事件通知等） */
    private String messageType;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 关联业务ID */
    private Long relatedId;

    /** 是否已读（0未读 1已读） */
    private Integer isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;
}
