package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限分配实体（T-404 精细化权限）
 * subjectType=DEPT 部门维度；subjectType=POSITION 职位维度（职位对用户的 FIXED/TEMP
 * 性质由 sys_user_position 决定，合并优先级 TEMP > FIXED > DEPT）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_perm_assignment")
public class SysPermAssignment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public static final String SUBJECT_DEPT = "DEPT";
    public static final String SUBJECT_POSITION = "POSITION";

    public static final String EFFECT_GRANT = "GRANT";
    public static final String EFFECT_DENY = "DENY";

    /** 主体类型（DEPT/POSITION） */
    private String subjectType;

    /** 主体ID（部门ID/职位ID） */
    private Long subjectId;

    /** 权限点编码 */
    private String permCode;

    /** 权限类型（MENU/BUTTON/API） */
    private String permType;

    /** 效果（GRANT授予 DENY拒绝） */
    private String effect;
}
