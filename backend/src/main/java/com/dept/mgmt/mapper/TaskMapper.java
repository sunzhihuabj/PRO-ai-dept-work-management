package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 任务Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 累加任务实际工时
     */
    @Update("UPDATE task SET actual_hours = COALESCE(actual_hours, 0) + #{hours} WHERE id = #{taskId} AND deleted = 0")
    int addActualHours(@Param("taskId") Long taskId, @Param("hours") BigDecimal hours);
}
