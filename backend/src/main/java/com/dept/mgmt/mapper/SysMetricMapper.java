package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.chatbi.entity.SysMetric;
import org.apache.ibatis.annotations.Mapper;

/**
 * ChatBI 指标白名单 Mapper（T-307 AGT-25）
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysMetricMapper extends BaseMapper<SysMetric> {
}
