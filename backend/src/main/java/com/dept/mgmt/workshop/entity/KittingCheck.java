package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 齐套审查表实体（T-201 W4，BR-08 数据源）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kitting_check")
public class KittingCheck extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 关联工单ID */
    private Long workorderId;

    /** 齐套率%（0~100） */
    private BigDecimal kitRate;

    /** 结果（PASS/BLOCK） */
    private String result;

    /** 缺料明细 JSON */
    private String detailJson;

    /** 审查方（AGT-13/MANUAL） */
    private String checker;

    /** 审查时间 */
    private LocalDateTime createTime;
}
