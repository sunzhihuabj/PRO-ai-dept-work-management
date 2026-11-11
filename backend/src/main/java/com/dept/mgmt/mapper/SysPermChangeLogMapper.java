package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.fineperm.entity.SysPermChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限变更日志 Mapper（T-404）
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysPermChangeLogMapper extends BaseMapper<SysPermChangeLog> {
}
