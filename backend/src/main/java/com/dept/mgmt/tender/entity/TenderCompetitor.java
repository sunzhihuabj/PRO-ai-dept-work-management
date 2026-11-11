package com.dept.mgmt.tender.entity;

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
 * 竞品开标记录实体（T-301，AGT-05 数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("tender_competitor")
public class TenderCompetitor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标讯ID */
    private Long tenderId;

    /** 竞品单位 */
    private String competitorName;

    /** 竞品报价 */
    private BigDecimal price;

    /** 备注 */
    private String note;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
