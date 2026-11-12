package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.WfInstanceNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例节点运行记录 Mapper（T-408）
 *
 * @author dept-mgmt
 */
@Mapper
public interface WfInstanceNodeMapper extends BaseMapper<WfInstanceNode> {
}
