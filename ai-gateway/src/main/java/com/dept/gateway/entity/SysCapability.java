package com.dept.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.gateway.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 能力点实体（与主应用共享 sys_capability 表，只读使用）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_capability")
public class SysCapability extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 能力点编码（唯一） */
    private String capabilityCode;

    /** 能力点名称 */
    private String capabilityName;

    /** 分类（base/agent/workflow） */
    private String category;

    /** 允许身份，逗号分隔 */
    private String allowedIdentities;

    /** 永久禁止Agent自动化 */
    private Integer isNever;

    /** 状态（1启用 0停用） */
    private Integer status;

    /** 备注 */
    private String remark;
}
