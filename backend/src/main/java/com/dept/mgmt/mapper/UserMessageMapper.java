package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消息Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
