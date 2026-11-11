package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.dingtalk.entity.DingTalkConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钉钉企业应用配置 Mapper（T-309）
 *
 * @author dept-mgmt
 */
@Mapper
public interface DingTalkConfigMapper extends BaseMapper<DingTalkConfig> {
}
