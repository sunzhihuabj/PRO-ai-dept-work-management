package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kpi_indicator")
public class KpiIndicator extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String indicatorName;
    private String indicatorCode;
    private Integer projectType;
    private String indicatorType;
    private BigDecimal baseScore;
    private String unit;
    private String description;
    private Integer status;
}
