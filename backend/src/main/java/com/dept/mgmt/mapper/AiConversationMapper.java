package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话 Mapper（T-408）
 *
 * @author dept-mgmt
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
