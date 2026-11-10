package com.dept.mgmt.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 能力自主策略实体（T-110 渐进自主引擎）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_capability_policy")
public class AgentCapabilityPolicy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 能力点编码（唯一） */
    private String capabilityCode;

    /** 自主级别（0禁用/1建议级/2自动级/3批量自动级） */
    private Integer autonomyLevel;

    /** 采纳次数 */
    private Integer acceptCount;

    /** 修改次数 */
    private Integer modifyCount;

    /** 驳回次数 */
    private Integer rejectCount;

    /** 累计执行次数 */
    private Integer totalCount;

    /** 累计错误次数 */
    private Integer errorCount;

    /** 上次评估时间 */
    private LocalDateTime lastEvaluatedAt;

    /** 最近升降级说明 */
    private String levelChangeNote;
}
