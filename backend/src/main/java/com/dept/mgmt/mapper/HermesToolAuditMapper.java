package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.agent.collab.mcp.HermesToolAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 工具调用审计 Mapper（V2.0 铁律五：append-only——仅 insert/select，无更新删除方法）
 *
 * @author dept-mgmt
 */
@Mapper
public interface HermesToolAuditMapper extends BaseMapper<HermesToolAudit> {
}
