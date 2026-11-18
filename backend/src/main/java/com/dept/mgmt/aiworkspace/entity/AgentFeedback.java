package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 反馈采集实体（V2.0 M8 自迭代）：run/消息/任务三源 ±1 评分 + 维度归因。
 *
 * @author dept-mgmt
 */
@Data
@TableName("agent_feedback")
public class AgentFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** RUN / MESSAGE / TASK */
    private String sourceType;

    /** 来源主键（agent_task.id / ai_message.id） */
    private Long sourceId;

    /** Hermes runId（sourceType=RUN 时） */
    private String runId;

    /** 评分人 */
    private Long userId;

    /** +1 赞 / -1 踩 */
    private Integer rating;

    /** ACCURACY / SPEED / SAFETY / TOOL */
    private String dimension;

    /** 文字反馈 */
    private String comment;

    private LocalDateTime createTime;
}
