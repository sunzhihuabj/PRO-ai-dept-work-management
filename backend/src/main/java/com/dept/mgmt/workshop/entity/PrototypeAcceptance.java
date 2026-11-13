package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 生产完工验收实体（T-201 W4，BR-01 数据源；原样机验收，T-412 改造为生产管理口径）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("production_acceptance")
public class PrototypeAcceptance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 生产工单ID */
    private Long workorderId;

    /** 结论（PENDING/PASS/FAIL） */
    private String result;

    /** 验收人 */
    private String acceptor;

    /** 验收时间 */
    private LocalDateTime acceptedAt;

    /** 验收意见 */
    private String comment;
}
