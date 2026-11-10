package com.dept.mgmt.platform.identity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 代理授权实体（ASSISTANT 以被代理人名义执行的能力白名单，规划 72h TTL）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_agent_grant")
public class SysAgentGrant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** Agent 实例标识（规范 agent:{userId}:{能力编号}） */
    private String agentInstanceKey;

    /** 被代理人用户ID */
    private Long principalUserId;

    /** 能力点编码 */
    private String capabilityCode;

    /** 状态（1有效 0撤销） */
    private Integer status;

    /** 过期时间（默认72小时，到期链路即失效） */
    private LocalDateTime expireTime;
}
