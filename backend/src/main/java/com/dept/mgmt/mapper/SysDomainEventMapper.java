package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.event.entity.SysDomainEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 跨域事件 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysDomainEventMapper extends BaseMapper<SysDomainEvent> {
}
