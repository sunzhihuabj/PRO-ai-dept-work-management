package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.tender.entity.TenderNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * TenderNotice Mapper（T-301 W1 投标域）
 *
 * @author dept-mgmt
 */
@Mapper
public interface TenderNoticeMapper extends BaseMapper<TenderNotice> {
}
