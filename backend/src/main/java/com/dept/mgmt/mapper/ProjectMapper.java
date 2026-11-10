package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
