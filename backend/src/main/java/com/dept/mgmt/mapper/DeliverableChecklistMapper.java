package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.DeliverableChecklist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交付物检查清单 Mapper
 */
@Mapper
public interface DeliverableChecklistMapper extends BaseMapper<DeliverableChecklist> {
}
