package com.dept.mgmt.design.entity;

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
 * 方案 BOM 明细实体（T-115 W3 设计域，AGT-11 校验对象）
 * 附属表无独立 deleted 语义，随方案级联重建
 *
 * @author dept-mgmt
 */
@Data
@TableName("design_bom_item")
public class DesignBomItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案ID */
    private Long schemeId;

    /** 物料编码（WL-XXXXXX） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specModel;

    /** 数量 */
    private BigDecimal qty;

    /** 计量单位 */
    private String unit;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
