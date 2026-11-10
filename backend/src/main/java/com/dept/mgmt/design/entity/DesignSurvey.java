package com.dept.mgmt.design.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 勘测记录实体（T-115 W3 设计域）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("design_survey")
public class DesignSurvey extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 勘测点位 */
    private String siteName;

    /** 勘测日期 */
    private LocalDate surveyDate;

    /** 勘测人 */
    private String surveyor;

    /** 风区等级 */
    private String windClass;

    /** 勘测结论 */
    private String content;
}
