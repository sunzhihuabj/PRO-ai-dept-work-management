package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工序质检记录实体（T-201 W4；FAIL 派生整改单）
 *
 * @author dept-mgmt
 */
@Data
@TableName("quality_check")
public class QualityCheck implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单ID */
    private Long workorderId;

    /** 工序 */
    private String stage;

    /** 结果（PASS/FAIL） */
    private String result;

    /** 质检员 */
    private String checker;

    /** 备注 */
    private String remark;

    /** 质检时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
