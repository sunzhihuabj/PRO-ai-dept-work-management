package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.platform.notify.entity.NotificationTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知模板 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface NotificationTemplateMapper extends BaseMapper<NotificationTemplate> {
}
