package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限模板条目实体（T-404 精细化权限）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_perm_template_item")
public class SysPermTemplateItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long templateId;

    /** 权限点编码 */
    private String permCode;

    /** 权限类型（MENU/BUTTON/API） */
    private String permType;

    /** 效果（GRANT/DENY） */
    private String effect;

    /** 排序号 */
    private Integer sortOrder;
}
