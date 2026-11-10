package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.TaskComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务评论 Mapper
 */
@Mapper
public interface TaskCommentMapper extends BaseMapper<TaskComment> {
}
