package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.tender.collect.entity.TenderPlatformSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台登录会话 Mapper（AGT-09 标讯联网采集）
 *
 * @author dept-mgmt
 */
@Mapper
public interface TenderPlatformSessionMapper extends BaseMapper<TenderPlatformSession> {
}
