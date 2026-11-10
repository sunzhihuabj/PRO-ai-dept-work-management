package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.workshop.entity.TplusReconciliation;
import org.apache.ibatis.annotations.Mapper;

/**
 * TplusReconciliation Mapper（T-204 T+ 对账批次）
 *
 * @author dept-mgmt
 */
@Mapper
public interface TplusReconciliationMapper extends BaseMapper<TplusReconciliation> {
}
