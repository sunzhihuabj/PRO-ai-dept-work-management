package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.kb.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
}
