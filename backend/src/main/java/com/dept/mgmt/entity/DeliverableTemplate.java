package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 交付物模板表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("deliverable_template")
public class DeliverableTemplate extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String templateName;
    /** 项目类型（1整机厂 2技改 3研发） */
    private Integer projectType;
    private Integer phaseOrder;
    private String phaseName;
    private String fileType;
    /** 是否必需（0可选 1必需） */
    private Integer required;
    private String description;
}
