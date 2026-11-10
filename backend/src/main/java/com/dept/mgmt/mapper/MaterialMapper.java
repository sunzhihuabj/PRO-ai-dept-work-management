package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.mdm.material.entity.Material;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物料主数据 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface MaterialMapper extends BaseMapper<Material> {
}
