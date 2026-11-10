package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价值流记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("value_stream_record")
public class ValueStreamRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long phaseId;
    private LocalDate recordDate;
    private String processName;
    private String processType;
    private BigDecimal cycleTime;
    private BigDecimal valueAddedTime;
    private BigDecimal nonValueAddedTime;
    private BigDecimal leadTime;
    /** 流程效率（%），保存时自动计算 */
    private BigDecimal efficiencyRate;
    private String description;
}
