package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.agent.entity.AgentTaskEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务事件 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface AgentTaskEventMapper extends BaseMapper<AgentTaskEvent> {
}
