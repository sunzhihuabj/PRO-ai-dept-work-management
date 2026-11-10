package com.dept.mgmt.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 任务实体（T-107 状态机）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务单号（唯一） */
    private String taskNo;

    /** 路由到的能力点 */
    private String capabilityCode;

    /** 用户原始指令 */
    private String instruction;

    /** 扩展参数 JSON */
    private String params;

    /** 状态（TaskStatus.code） */
    private String status;

    /** 发起用户ID */
    private Long callerUserId;

    /** 执行实例标识（ASSISTANT 代理时） */
    private String agentInstanceKey;

    /** 意图识别置信度（0~1） */
    private Double intentConfidence;

    /** 执行结果（AI 生成内容必带标注） */
    private String resultText;

    /** 结果是否 AI 生成 */
    private Integer aiGenerated;

    /** 结果反馈（ACCEPTED采纳/MODIFIED修改/REJECTED驳回，T-110 渐进自主数据源） */
    private String resultFeedback;

    /** 失败原因（收敛后） */
    private String errorMsg;

    /** 实际执行者 */
    private String executedBy;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;
}
