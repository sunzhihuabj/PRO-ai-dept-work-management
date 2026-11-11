package com.dept.mgmt.tender.entity;

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
 * 投标报价记录实体（T-301，AGT-04 载体 / 历史成交价数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("tender_bid")
public class TenderBid implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报价ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标讯ID */
    private Long tenderId;

    /** 我方报价 */
    private BigDecimal price;

    /** 预估成本 */
    private BigDecimal costEstimate;

    /** 预估毛利率% */
    private BigDecimal grossMargin;

    /** 投标策略说明 */
    private String strategyText;

    /** 递交时间 */
    private LocalDateTime submittedAt;

    /** 开标日期 */
    private LocalDate openDate;

    /** 开标结果（我方名次/中标价） */
    private String openResult;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
