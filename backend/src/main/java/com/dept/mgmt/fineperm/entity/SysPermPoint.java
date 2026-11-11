package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限点目录实体（T-404 精细化权限）
 * MENU 菜单级（code=前端路由path）/ BUTTON 按钮级（btn:xxx）/ API 接口级（api:xxx）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_perm_point")
public class SysPermPoint extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_MENU = "MENU";
    public static final String TYPE_BUTTON = "BUTTON";
    public static final String TYPE_API = "API";

    /** 权限点编码（唯一） */
    private String permCode;

    /** 权限点名称 */
    private String permName;

    /** 权限类型（MENU/BUTTON/API） */
    private String permType;

    /** 父权限点编码（BUTTON/API 挂 MENU） */
    private String parentCode;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（0停用 1正常） */
    private Integer status;
}
