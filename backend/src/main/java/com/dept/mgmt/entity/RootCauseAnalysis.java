package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 根因分析表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("root_cause_analysis")
public class RootCauseAnalysis extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private String problemDesc;
    /** 分析方法（5Why/鱼骨图/故障树/FMEA） */
    private String analysisMethod;
    private String findings;
    private String rootCause;
    private String correctiveAction;
    private String preventiveAction;
    /** 状态（0分析中 1已完成 2已关闭） */
    private Integer status;
    private String createBy;
}
