package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.contract.entity.ContractProject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同-项目关联 Mapper（T-406）
 *
 * @author dept-mgmt
 */
@Mapper
public interface ContractProjectMapper extends BaseMapper<ContractProject> {
}
