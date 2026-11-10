package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.TaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务日志Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {
}
