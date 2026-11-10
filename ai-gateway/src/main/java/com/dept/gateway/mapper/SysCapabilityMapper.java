package com.dept.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.gateway.entity.SysCapability;
import org.apache.ibatis.annotations.Mapper;

/**
 * 能力点 Mapper（网关只读）
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysCapabilityMapper extends BaseMapper<SysCapability> {
}
