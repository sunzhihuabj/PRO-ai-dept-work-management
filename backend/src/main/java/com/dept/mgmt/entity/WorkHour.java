package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时表
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_hour")
public class WorkHour extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 用户ID */
    private Long userId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 工时数 */
    private BigDecimal hours;

    /** 工作描述 */
    private String description;

    /** 状态（0无效 1有效） */
    private Integer status;
}
