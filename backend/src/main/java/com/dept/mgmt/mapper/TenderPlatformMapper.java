package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标商务平台配置 Mapper（AGT-09 标讯联网采集）
 *
 * @author dept-mgmt
 */
@Mapper
public interface TenderPlatformMapper extends BaseMapper<TenderPlatform> {
}
