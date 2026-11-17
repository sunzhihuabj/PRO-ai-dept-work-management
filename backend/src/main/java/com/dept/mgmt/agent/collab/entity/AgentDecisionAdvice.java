package com.dept.mgmt.agent.collab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 决策建议实体（阶段一多 Agent 协同，agent_decision_advice 表）
 *
 * <p>生命周期：业务事件 → DecisionAdviceService 生成（确定性模板，阶段二接入 LLM）→
 * 站内信推送 target_user_id → 人工审批（requiresApproval=1 时 HMAC 锁定参数）→
 * 采纳执行 / 驳回归档。
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_decision_advice")
public class AgentDecisionAdvice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 场景标识（PROJECT_CREATED/TASK_CREATED/TASK_STATUS_CHANGED/PROJECT_PROGRESS） */
    private String scene;

    /** 来源实体类型（PROJECT/TASK/PHASE/MILESTONE） */
    private String sourceType;

    /** 来源实体 ID */
    private Long sourceId;

    /** 来源业务编号（项目编号/任务名等，便于展示） */
    private String sourceBizNo;

    /** 建议 Agent 标识（阶段一为规则引擎 system-rule） */
    private String agentId;

    /** 建议 Agent 角色（业务执行/编排/审批兜底/系统集成） */
    private String agentRole;

    /** 建议标题 */
    private String title;

    /** 建议内容 JSON（type=INFO/WARNING/REQUEST + body + structured） */
    private String contentJson;

    /** 置信度（0-1） */
    private Double confidence;

    /** 风险等级（LOW/MEDIUM/HIGH） */
    private String riskLevel;

    /** 是否需人工审批（0否 1是） */
    private Integer requiresApproval;

    /** 审批通过后执行的动作（AUTO_ASSIGN_TASK 等，空=纯建议） */
    private String approvalAction;

    /** 审批动作参数 JSON（HMAC 锁定防篡改） */
    private String approvalParamsJson;

    /** 审批参数 HMAC-SHA256 摘要 */
    private String approvalHmac;

    /** 状态（PENDING/APPROVED/REJECTED/EXPIRED） */
    private String status;

    /** 建议依据引用 JSON 数组 */
    private String evidenceJson;

    /** 建议推送目标用户 */
    private Long targetUserId;

    /** 建议响应截止时间（SLA） */
    private LocalDateTime responseDeadline;

    /** 审批人 */
    private Long approverId;

    /** 审批备注（驳回原因等） */
    private String approverComment;

    /** 审批时间 */
    private LocalDateTime approvedAt;
}
