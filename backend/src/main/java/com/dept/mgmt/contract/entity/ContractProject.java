package com.dept.mgmt.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同-项目关联实体（T-406）
 * 一份合同可关联多个项目，一个项目可关联多份合同（销售+采购）；绑定/解除留痕
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_project")
public class ContractProject extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 项目ID */
    private Long projectId;

    /** 绑定操作人 */
    private String bindBy;
}
