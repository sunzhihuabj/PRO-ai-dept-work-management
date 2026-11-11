package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.fineperm.entity.SysPermAssignment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限分配 Mapper（T-404）
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysPermAssignmentMapper extends BaseMapper<SysPermAssignment> {
}
