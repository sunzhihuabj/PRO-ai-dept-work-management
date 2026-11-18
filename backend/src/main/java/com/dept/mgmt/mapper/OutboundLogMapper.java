package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.OutboundLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出网留痕 mapper（铁律五 append-only：仅允许 insert/select）
 *
 * @author dept-mgmt
 */
@Mapper
public interface OutboundLogMapper extends BaseMapper<OutboundLog> {
}
