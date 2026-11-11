package com.dept.mgmt.cost.entity;

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
 * 成本台账实体（T-303 W7，AGT-21 归集数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("cost_ledger")
public class CostLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 台账ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 成本类型（MATERIAL物料/LABOR人工/EXPENSE费用） */
    private String costType;

    /** 来源（AGENT自动归集/MANUAL手工） */
    private String sourceType;

    /** 来源记录ID（物料归集时=inventory_record.id） */
    private Long sourceId;

    /** 金额 */
    private BigDecimal amount;

    /** 发生日期 */
    private LocalDate occurredDate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
