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
 * 报价模型实体（T-303 W7，F-08 历史成本库）
 *
 * @author dept-mgmt
 */
@Data
@TableName("price_model")
public class PriceModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目类型（1整机厂/2技改/3研发） */
    private Integer projectType;

    /** 平均毛利率% */
    private BigDecimal avgMargin;

    /** 最低毛利率% */
    private BigDecimal minMargin;

    /** 样本数（定稿核算单） */
    private Integer sampleCount;

    /** 模型版本（每次更新+1） */
    private Integer version;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
