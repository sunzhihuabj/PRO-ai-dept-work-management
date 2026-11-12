package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话消息 Mapper（T-408）
 *
 * @author dept-mgmt
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
