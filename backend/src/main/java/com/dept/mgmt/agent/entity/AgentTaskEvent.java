package com.dept.mgmt.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 任务事件实体（状态流转留痕，供工作台时间线展示）
 *
 * @author dept-mgmt
 */
@Data
@TableName("agent_task_event")
public class AgentTaskEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务ID */
    private Long taskId;

    /** 原状态 */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 操作者（user:{id}/agent:{key}/system） */
    private String operator;

    /** 事件说明 */
    private String detail;

    /** 发生时间 */
    private LocalDateTime createTime;
}
