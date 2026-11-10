package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务评论表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_comment")
public class TaskComment extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long taskId;
    private Long userId;
    private String content;
    /** 父评论ID（0为顶级评论） */
    private Long parentId;
}
