package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aftermarket.entity.RepairTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * RepairTicket Mapper（T-304 W8 售后域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface RepairTicketMapper extends BaseMapper<RepairTicket> {
}
