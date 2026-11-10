package com.dept.mgmt.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同审批记录实体（会签/审批/用印节点留痕）
 *
 * @author dept-mgmt
 */
@Data
@TableName("contract_approval")
public class ContractApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 合同ID */
    private Long contractId;

    /** 审批节点（COUNTERSIGN会签/APPROVE审批/SEAL用印） */
    private String node;

    /** 审批人用户ID */
    private Long approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 结果（APPROVED通过/REJECTED驳回） */
    private String result;

    /** 审批意见 */
    private String comment;

    /** 审批时间 */
    private LocalDateTime createTime;
}
