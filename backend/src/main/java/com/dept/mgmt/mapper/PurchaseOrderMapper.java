package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * PurchaseOrder Mapper（W4 生产域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
