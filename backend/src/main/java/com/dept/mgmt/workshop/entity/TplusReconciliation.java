package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T+ 对账批次实体（T-204，AGT-16）
 * 本地已推送单 ↔ T+ 导出回执逐单核对，差异清单落库
 *
 * @author dept-mgmt
 */
@Data
@TableName("tplus_reconciliation")
public class TplusReconciliation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对账批次号 */
    private String batchNo;

    /** 对账日期 */
    private LocalDate reconcileDate;

    /** 本地已推送单数 */
    private Integer localCount;

    /** T+ 回执单数 */
    private Integer tplusCount;

    /** 核对一致单数 */
    private Integer matchedCount;

    /** 差异单数 */
    private Integer diffCount;

    /** 差异明细 JSON */
    private String diffJson;

    /** 状态（DONE） */
    private String status;

    /** 对账时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
