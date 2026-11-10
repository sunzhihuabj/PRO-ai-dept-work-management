package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.EventInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件信息 Mapper
 */
@Mapper
public interface EventInfoMapper extends BaseMapper<EventInfo> {
}
