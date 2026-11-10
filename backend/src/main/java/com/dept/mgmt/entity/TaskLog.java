package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务日志表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_log")
public class TaskLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 操作类型（创建/分配/状态变更/编辑等） */
    private String operType;

    /** 操作描述 */
    private String operDesc;

    /** 操作人 */
    private String operBy;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 变更前值（JSON） */
    private String oldValue;

    /** 变更后值（JSON） */
    private String newValue;
}
