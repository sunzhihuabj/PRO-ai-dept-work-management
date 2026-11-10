package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kpi_weight")
public class KpiWeight extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Integer projectType;
    private Long indicatorId;
    private BigDecimal weight;
    private String period;
}
