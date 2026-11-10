package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Agent 任务 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    /**
     * 乐观锁领取任务：仅当仍为 queued 时置为 running（防并发重复领取）
     *
     * @return 受影响行数：1=领取成功；0=已被其他执行器领取
     */
    @Update("UPDATE agent_task SET status = 'running', started_at = NOW(), executed_by = #{executedBy} " +
            "WHERE id = #{id} AND status = 'queued' AND deleted = 0")
    int claim(@Param("id") Long id, @Param("executedBy") String executedBy);
}
