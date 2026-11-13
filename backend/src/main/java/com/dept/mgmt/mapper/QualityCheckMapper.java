package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.workshop.entity.QualityCheck;
import org.apache.ibatis.annotations.Mapper;

/**
 * QualityCheck Mapper（W4 生产域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface QualityCheckMapper extends BaseMapper<QualityCheck> {
}
