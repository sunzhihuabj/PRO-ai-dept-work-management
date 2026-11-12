package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程实例实体（T-408 生命周期）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_instance")
public class WfInstance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 实例编号（唯一，WFI 规则） */
    private String instanceNo;

    /** 流程ID */
    private Long workflowId;

    /** 流程名称快照 */
    private String workflowName;

    /** 启动时流程图快照 JSON */
    private String graphSnapshot;

    /** 状态（running/completed/failed/cancelled） */
    private String status;

    /** 发起人 */
    private String startedBy;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
