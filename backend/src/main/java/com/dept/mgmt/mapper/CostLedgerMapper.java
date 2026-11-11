package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.cost.entity.CostLedger;
import org.apache.ibatis.annotations.Mapper;

/**
 * CostLedger Mapper（T-303 W7 成本域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface CostLedgerMapper extends BaseMapper<CostLedger> {
}
