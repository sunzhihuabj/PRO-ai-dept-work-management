package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件审核记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_audit")
public class FileAudit extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 关联的文件 ID */
    private Long fileId;

    /** 审核人 ID（对应 sys_user.id） */
    private Long auditorId;

    /** 审核状态：APPROVED / REJECTED */
    private String status;

    /** 审核意见/备注 */
    private String comment;

    /** 审核时间 */
    private LocalDateTime auditTime;
}
