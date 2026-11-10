package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.workshop.entity.InventoryRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * InventoryRecord Mapper（T-204 扫码过账）
 *
 * @author dept-mgmt
 */
@Mapper
public interface InventoryRecordMapper extends BaseMapper<InventoryRecord> {
}
