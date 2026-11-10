package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.contract.entity.Contract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同台账 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface ContractMapper extends BaseMapper<Contract> {
}
