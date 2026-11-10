package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.platform.identity.entity.SysAgentGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 代理授权 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysAgentGrantMapper extends BaseMapper<SysAgentGrant> {
}
