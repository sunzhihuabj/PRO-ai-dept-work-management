package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目奖金发放记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_bonus_record")
public class ProjectBonusRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long bonusId;
    private Long projectId;
    private Long userId;
    /** 发放类型（1月度 2年度） */
    private Integer payType;
    private BigDecimal payAmount;
    /** 发放周期（YYYY-MM） */
    private String payPeriod;
    private LocalDateTime payTime;
    private String remark;
}
