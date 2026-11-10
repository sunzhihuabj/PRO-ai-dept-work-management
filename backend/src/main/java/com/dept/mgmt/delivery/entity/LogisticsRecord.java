package com.dept.mgmt.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物流跟踪记录实体（T-206 W6）
 *
 * @author dept-mgmt
 */
@Data
@TableName("logistics_record")
public class LogisticsRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发货单ID */
    private Long shipmentId;

    /** 事件（DISPATCH发运/TRANSIT在途/ARRIVED到货/INSTALL进场安装） */
    private String eventType;

    /** 事件时间 */
    private LocalDateTime eventTime;

    /** 位置 */
    private String location;

    /** 说明 */
    private String description;

    /** 记录人 */
    private String operator;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
