package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 决策建议 Mapper（阶段一多 Agent 协同）
 *
 * @author dept-mgmt
 */
@Mapper
public interface AgentDecisionAdviceMapper extends BaseMapper<AgentDecisionAdvice> {
}
