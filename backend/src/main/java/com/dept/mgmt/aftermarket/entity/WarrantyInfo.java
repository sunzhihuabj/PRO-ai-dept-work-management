package com.dept.mgmt.aftermarket.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质保信息实体（T-304 W8 售后域，AGT-24 质保金提醒数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("warranty_info")
public class WarrantyInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 信息ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 质保期满日期 */
    private LocalDate warrantyEnd;

    /** 质保金金额 */
    private BigDecimal depositAmount;

    /** 到期前30天是否已提醒（0/1） */
    private Integer reminded;

    /** 状态（ACTIVE质保中/CLAIMED已申请质保金/EXPIRED已过期） */
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
