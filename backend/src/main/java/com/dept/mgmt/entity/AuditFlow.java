package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审核流程表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_flow")
public class AuditFlow extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String flowName;
    private String flowType;
    private Integer projectType;
    private Integer status;
    private String description;
}
