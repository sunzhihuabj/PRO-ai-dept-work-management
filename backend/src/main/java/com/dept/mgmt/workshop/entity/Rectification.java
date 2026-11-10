package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 整改单实体（T-201 W4，质检/验收 FAIL 派生）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rectification")
public class Rectification extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 来源（QC质检/ACCEPT验收） */
    private String source;

    /** 来源记录ID */
    private Long sourceId;

    /** 问题描述 */
    private String issue;

    /** 责任人 */
    private String owner;

    /** 整改期限 */
    private LocalDate dueDate;

    /** 状态（OPEN/CLOSED） */
    private String status;

    /** 关闭说明 */
    private String closeNote;
}
