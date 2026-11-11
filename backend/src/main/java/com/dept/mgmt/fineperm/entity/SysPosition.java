package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 职位定义实体（T-404 精细化权限）
 * 固定职位与临时职位共用职位定义，FIXED/TEMP 区分方式见 SysUserPosition.positionKind
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_position")
public class SysPosition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 职位编码（唯一） */
    private String positionCode;

    /** 职位名称 */
    private String positionName;

    /** 描述 */
    private String description;

    /** 状态（0停用 1正常） */
    private Integer status;
}
