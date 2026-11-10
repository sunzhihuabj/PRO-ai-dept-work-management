package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购订单实体（T-201 W4）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** PO 编号 */
    private String poNo;

    /** 项目ID */
    private Long projectId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 采购数量 */
    private BigDecimal qty;

    /** 已到货数量 */
    private BigDecimal arrivedQty;

    /** 供应商 */
    private String supplier;

    /** 期望到货日期（AGT-15 交期跟踪） */
    private java.time.LocalDate expectedDate;

    /** 状态（DRAFT/SUBMITTED/CLOSED） */
    private String status;
}
