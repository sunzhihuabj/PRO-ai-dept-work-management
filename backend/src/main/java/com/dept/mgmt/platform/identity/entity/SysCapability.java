package com.dept.mgmt.platform.identity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 能力点注册实体（deny by default：未登记的能力点一律拒绝）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_capability")
public class SysCapability extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 能力点编码（唯一，如 CAP_CONTRACT_EXTRACT / AGT-06） */
    private String capabilityCode;

    /** 能力点名称 */
    private String capabilityName;

    /** 分类（base基础/agent智能体/workflow工作流） */
    private String category;

    /** 允许身份，逗号分隔：EXPERT,WORKER,ASSISTANT */
    private String allowedIdentities;

    /** 永久禁止Agent自动化（1=审批/资金/主数据建档/删除四类红线，仅拦截Agent不拦人类） */
    private Integer isNever;

    /** 状态（1启用 0停用） */
    private Integer status;

    /** 备注 */
    private String remark;
}
