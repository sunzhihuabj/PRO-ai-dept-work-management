package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.AiDocDraft;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 文档草稿 mapper（M6 文档工作台）
 *
 * @author dept-mgmt
 */
@Mapper
public interface AiDocDraftMapper extends BaseMapper<AiDocDraft> {
}
