package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限模板实体（T-404 精细化权限）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_perm_template")
public class SysPermTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板编码（唯一） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 描述 */
    private String description;
}
