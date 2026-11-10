package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.workshop.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * WorkOrder Mapper（W4 样机域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}
