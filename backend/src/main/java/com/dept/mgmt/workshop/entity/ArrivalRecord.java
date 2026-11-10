package com.dept.mgmt.workshop.entity;

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
 * 到货跟踪记录实体（T-201 W4，AGT-15 数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("arrival_record")
public class ArrivalRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** PO ID */
    private Long poId;

    /** 项目ID */
    private Long projectId;

    /** 物料编码 */
    private String materialCode;

    /** 到货数量 */
    private BigDecimal qty;

    /** 到货时间 */
    private LocalDateTime arrivedAt;

    /** 登记人 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
