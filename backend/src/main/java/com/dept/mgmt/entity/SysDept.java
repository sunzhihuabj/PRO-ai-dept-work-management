package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织架构表（原部门表，T-310 命名统一；表名/字段名保持不变以确保数据兼容）
 * 层级关系：parentId 直属上级 + ancestors 祖级链路
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    private static final long serialVersionUID = 1L;
    /** 上级组织ID（0=根节点） */
    private Long parentId;
    /** 祖级链路（如 0,1,5，用于层级追溯） */
    private String ancestors;
    /** 组织名称 */
    private String deptName;
    /** 显示排序 */
    private Integer orderNum;
    /** 负责人 */
    private String leader;
    /** 联系电话 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 状态（1正常/0停用） */
    private Integer status;
}
