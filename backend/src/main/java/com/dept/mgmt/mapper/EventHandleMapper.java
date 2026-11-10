package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.EventHandle;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件处理记录 Mapper
 */
@Mapper
public interface EventHandleMapper extends BaseMapper<EventHandle> {
}
