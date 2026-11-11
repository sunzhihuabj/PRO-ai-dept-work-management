package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.fineperm.entity.SysUserPosition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户职位关联 Mapper（T-404）
 *
 * @author dept-mgmt
 */
@Mapper
public interface SysUserPositionMapper extends BaseMapper<SysUserPosition> {
}
