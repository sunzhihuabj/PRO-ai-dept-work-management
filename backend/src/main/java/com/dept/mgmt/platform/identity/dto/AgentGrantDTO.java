package com.dept.mgmt.platform.identity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 代理授权 DTO
 *
 * @author dept-mgmt
 */
@Data
public class AgentGrantDTO {

    /** Agent 实例标识（规范 agent:{userId}:{能力编号}） */
    @NotBlank(message = "Agent实例标识不能为空")
    @Size(max = 100, message = "Agent实例标识不能超过100字符")
    private String agentInstanceKey;

    /** 被代理人用户ID */
    @NotNull(message = "被代理人不能为空")
    private Long principalUserId;

    /** 能力点编码 */
    @NotBlank(message = "能力点编码不能为空")
    private String capabilityCode;

    /** 有效时长（小时），默认 72，规划 L1 确认 TTL 语义 */
    @Min(value = 1, message = "有效时长至少1小时")
    private Integer expireHours = 72;
}
