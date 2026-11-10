package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.design.entity.DesignBomCheckLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * BOM 校验记录 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface DesignBomCheckLogMapper extends BaseMapper<DesignBomCheckLog> {
}
