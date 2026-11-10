package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出入库记录实体（T-204 扫码过账，AGT-16 数据源）
 * IN 采购入库必须关联 PO；OUT 生产出库必须关联工单
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_record")
public class InventoryRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 出入库单号（IO 编码引擎） */
    private String recordNo;

    /** 项目ID */
    private Long projectId;

    /** 关联工单ID（出库必填） */
    private Long workorderId;

    /** 关联PO ID（入库必填） */
    private Long poId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 方向（IN采购入库/OUT生产出库） */
    private String direction;

    /** 数量 */
    private BigDecimal qty;

    /** 扫码条码内容 */
    private String barcode;

    /** 来源（SCAN扫码/MANUAL手工） */
    private String source;

    /** 操作人 */
    private String operator;

    /** 扫码时间 */
    private LocalDateTime scannedAt;

    /** T+推送状态（PENDING待推送/PUSHED已推送/FAILED失败） */
    private String tplusStatus;

    /** T+推送时间 */
    private LocalDateTime tplusPushTime;

    /** T+凭证号 */
    private String tplusVoucherNo;

    /** 备注 */
    private String remark;
}
