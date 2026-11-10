package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审核记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_record")
public class AuditRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long flowId;
    private Long projectId;
    private String businessType;
    private Long businessId;
    private Integer auditLevel;
    private Long auditorId;
    private Integer auditResult;
    private String auditComment;
    private LocalDateTime auditTime;
}
