package com.dept.mgmt.delivery.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发货单实体（T-206 W6，BR-02 载体：整机厂首次发货必须持有 PASS 的 FAI 报告）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shipment")
public class Shipment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 发货单号（SP 编码引擎） */
    private String shipmentNo;

    /** 项目ID */
    private Long projectId;

    /** 关联工单ID */
    private Long workorderId;

    /** 批次号 */
    private String batchNo;

    /** 发货台数 */
    private Integer qty;

    /** 承运商 */
    private String carrier;

    /** 状态（DRAFT草稿/SUBMITTED已发运） */
    private String status;

    /** 发运时间 */
    private LocalDateTime shippedAt;

    /** 备注 */
    private String remark;
}
