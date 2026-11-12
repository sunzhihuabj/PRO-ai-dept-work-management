package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.aiworkspace.entity.WfWorkflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务流程定义 Mapper（T-408）
 *
 * @author dept-mgmt
 */
@Mapper
public interface WfWorkflowMapper extends BaseMapper<WfWorkflow> {
}
