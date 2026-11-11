package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限变更日志实体（T-404 精细化权限）
 * 只增不改的审计表：完整记录操作人/操作时间/主体/维度/变更前后权限内容
 * 注意：不继承 BaseEntity（审计表无 update_time/deleted 列，物理只增）
 *
 * @author dept-mgmt
 */
@Data
@TableName("sys_perm_change_log")
public class SysPermChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_DEPT = "DEPT";
    public static final String SUBJECT_POSITION = "POSITION";

    /** 日志ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名（冗余） */
    private String operatorName;

    /** 主体类型（USER用户视角 DEPT部门 POSITION职位） */
    private String subjectType;

    /** 主体ID（用户ID/部门ID/职位ID） */
    private Long subjectId;

    /** 主体名称（冗余便于审计阅读） */
    private String subjectName;

    /** 变更维度（DEPT部门 FIXED固定职位 TEMP临时职位） */
    private String dimension;

    /** 操作类型（GRANT/DENY/CLEAR/ASSIGN_POSITION/REMOVE_POSITION/APPLY_TEMPLATE/BATCH_GRANT/BATCH_CLEAR） */
    private String operation;

    /** 本次变更涉及的权限点编码（JSON数组） */
    private String permCodes;

    /** 变更前权限内容快照（JSON） */
    private String beforeJson;

    /** 变更后权限内容快照（JSON） */
    private String afterJson;

    /** 备注 */
    private String remark;

    /** 操作时间（DB DEFAULT CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;
}
