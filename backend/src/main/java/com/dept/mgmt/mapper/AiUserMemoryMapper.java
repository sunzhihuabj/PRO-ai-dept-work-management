package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.AiUserMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 用户长期记忆 Mapper（P0-3）
 *
 * @author dept-mgmt
 */
@Mapper
public interface AiUserMemoryMapper extends BaseMapper<AiUserMemory> {
}
