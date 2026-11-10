package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.delivery.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;

/**
 * Shipment Mapper（T-206 W6 交付域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {
}
