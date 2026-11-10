package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.contract.entity.ContractApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同审批记录 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface ContractApprovalMapper extends BaseMapper<ContractApproval> {
}
