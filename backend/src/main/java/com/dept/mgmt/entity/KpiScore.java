package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kpi_score")
public class KpiScore extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long userId;
    private String period;
    private Long indicatorId;
    private BigDecimal baseScore;
    private BigDecimal adjustScore;
    private BigDecimal finalScore;
    private String adjustReason;
    private String level;
    private Integer status;
}
