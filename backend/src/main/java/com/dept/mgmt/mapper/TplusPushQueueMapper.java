package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.workshop.entity.TplusPushQueue;
import org.apache.ibatis.annotations.Mapper;

/**
 * TplusPushQueue Mapper（T-204 T+ 推送队列）
 *
 * @author dept-mgmt
 */
@Mapper
public interface TplusPushQueueMapper extends BaseMapper<TplusPushQueue> {
}
