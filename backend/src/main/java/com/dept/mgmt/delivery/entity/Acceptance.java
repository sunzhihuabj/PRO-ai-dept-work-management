package com.dept.mgmt.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分级验收单实体（T-206 W6）
 * BR-05：三方签字（业主+监理+我方）齐备才生效（PASS）
 *
 * @author dept-mgmt
 */
@Data
@TableName("acceptance")
public class Acceptance implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 验收单ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 阶段（FIRST首台/MID中期/LATE后期/FINAL终验） */
    private String stage;

    /** 状态（TRIGGERED已触发/PENDING_SIGN待签字/PASS生效/FAIL不通过） */
    private String status;

    /** 触发时工程量% */
    private BigDecimal progressPct;

    /** 我方验收人 */
    private String acceptorOwner;

    /** 监理验收人 */
    private String acceptorSupervisor;

    /** 业主验收人 */
    private String acceptorClient;

    /** 我方签字（0/1） */
    private Integer signedOwner;

    /** 监理签字（0/1） */
    private Integer signedSupervisor;

    /** 业主签字（0/1） */
    private Integer signedClient;

    /** 三方签字齐备（生效）时间 */
    private LocalDateTime acceptedAt;

    /** 验收意见 */
    private String comment;

    /** 触发方式（MANUAL/AGENT） */
    private String triggeredBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
