package com.dept.mgmt.design.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 方案模板库实体（T-115 W3 设计域，AGT-09 匹配源）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("design_template")
public class DesignTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板名称 */
    private String templateName;

    /** 适用场景（塔筒/机舱/基础环/电气等，匹配键） */
    private String scene;

    /** 模板正文（含 {{projectName}}/{{customerName}}/{{specRefs}} 占位符） */
    private String templateText;

    /** 引用规范（如 GB/T 19072-2018） */
    private String specRefs;

    /** 启用（1启用 0停用） */
    private Integer enabled;
}
