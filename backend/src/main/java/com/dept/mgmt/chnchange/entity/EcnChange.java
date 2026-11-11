package com.dept.mgmt.chnchange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * ECN 变更单实体（T-302 W5 变更域，BR-07 载体）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ecn_change")
public class EcnChange extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** ECN 编号（ECN 编码引擎） */
    private String ecnNo;

    /** 项目ID */
    private Long projectId;

    /** 变更标题 */
    private String title;

    /** 变更原因 */
    private String reason;

    /** 类型（DESIGN设计/PROCESS工艺/MATERIAL物料） */
    private String changeType;

    /** 原方案ID（旧版） */
    private Long originSchemeId;

    /** 新方案ID（实施后绑定） */
    private Long newSchemeId;

    /** 影响级别（A重大/B一般/C轻微） */
    private String impactLevel;

    /** 状态（DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/IMPLEMENTED/CLOSED） */
    private String status;

    /** 申请人 */
    private String applicant;

    /** 审批人 */
    private String approver;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 实施时间 */
    private LocalDateTime implementedAt;

    /** 备注 */
    private String remark;
}
