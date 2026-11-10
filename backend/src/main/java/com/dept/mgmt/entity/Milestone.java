package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 里程碑表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("milestone")
public class Milestone extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long phaseId;
    private String milestoneName;
    private LocalDate planDate;
    private LocalDate actualDate;
    /** 状态（0未开始 1已完成 2延期） */
    private Integer status;
    private String description;
}
