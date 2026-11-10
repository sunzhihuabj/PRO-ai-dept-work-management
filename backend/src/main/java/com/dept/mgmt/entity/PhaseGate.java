package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 阶段门禁表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("phase_gate")
public class PhaseGate extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long phaseId;
    private String gateName;
    private Integer gateOrder;
    private String passCondition;
    /** 审核结果（0待审核 1通过 2驳回） */
    private Integer auditResult;
    private String auditBy;
    private LocalDateTime auditTime;
    private String auditComment;
}
