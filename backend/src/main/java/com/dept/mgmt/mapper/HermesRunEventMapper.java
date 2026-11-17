package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.HermesRunEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Hermes run 执行过程事件 Mapper（B3 过程回写）
 *
 * @author dept-mgmt
 */
@Mapper
public interface HermesRunEventMapper extends BaseMapper<HermesRunEvent> {
}
