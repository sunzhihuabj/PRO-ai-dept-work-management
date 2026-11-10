package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.mdm.customer.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/**
 * Customer Mapper（客户主体库，T-213 迁移）
 *
 * @author dept-mgmt
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
