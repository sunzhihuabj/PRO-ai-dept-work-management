package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程实例节点运行记录实体（T-408）
 * 无逻辑删除：运行记录只追加与状态推进，不继承 BaseEntity
 *
 * @author dept-mgmt
 */
@Data
@TableName("wf_instance_node")
public class WfInstanceNode {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实例ID */
    private Long instanceId;

    /** 节点键（流程图内唯一） */
    private String nodeKey;

    /** 节点名称快照 */
    private String nodeName;

    /** 节点类型（start/agent/approval/notification） */
    private String nodeType;

    /** 节点配置快照 JSON（能力点/指令模板/审批人/通知对象） */
    private String config;

    /** 状态（pending/running/waiting_approval/done/failed/skipped） */
    private String status;

    /** 关联 Agent 任务ID（agent 节点） */
    private Long agentTaskId;

    /** 审批人ID（approval 节点） */
    private Long assigneeUserId;

    /** 审批意见 */
    private String approvalComment;

    /** 失败原因 */
    private String errorMsg;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
