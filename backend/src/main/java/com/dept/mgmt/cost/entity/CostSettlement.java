package com.dept.mgmt.cost.entity;

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
 * 完工成本核算实体（T-303 W7，AGT-22 毛利三口径载体）
 * 三口径定义（透明披露）：完全成本=台账全量归集；标准成本=物料按标准价重算；
 * 现金支出=已完结 PO 部分物料按标准价
 *
 * @author dept-mgmt
 */
@Data
@TableName("cost_settlement")
public class CostSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 核算ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 核算单号（CST 编码引擎） */
    private String settleNo;

    /** 项目ID */
    private Long projectId;

    /** 物料成本 */
    private BigDecimal materialCost;

    /** 人工成本 */
    private BigDecimal laborCost;

    /** 费用成本 */
    private BigDecimal expenseCost;

    /** 完全成本合计 */
    private BigDecimal totalCost;

    /** 标准成本口径 */
    private BigDecimal standardCost;

    /** 现金支出口径（已完结 PO 物料） */
    private BigDecimal cashCost;

    /** 合同额 */
    private BigDecimal contractAmount;

    /** 毛利率%（完全成本口径） */
    private BigDecimal grossMargin;

    /** 三口径对比+差异分解 JSON */
    private String marginDiffJson;

    /** 状态（DRAFT定稿前/CONFIRMED定稿） */
    private String status;

    /** 定稿时间 */
    private LocalDateTime settledAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
