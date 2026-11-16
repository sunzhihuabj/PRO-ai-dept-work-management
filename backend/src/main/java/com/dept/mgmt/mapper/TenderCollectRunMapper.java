package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.tender.collect.entity.TenderCollectRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标讯采集运行监控 Mapper（AGT-09 标讯联网采集）
 *
 * @author dept-mgmt
 */
@Mapper
public interface TenderCollectRunMapper extends BaseMapper<TenderCollectRun> {
}
