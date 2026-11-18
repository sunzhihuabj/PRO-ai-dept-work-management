package com.dept.mgmt.controller;

import com.dept.mgmt.agent.collab.mcp.HermesToolAudit;
import com.dept.mgmt.agent.collab.mcp.HermesToolAuditService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hermes/MCP 工具调用审计追溯（V2.0 铁律五：操作留痕，全链路可追溯）
 *
 * <p>数据范围：部门负责人可查全部；普通用户仅本人调用记录。
 * 配合 hermes_run_event（过程）/sys_ai_call_log（模型）/SysOperLog（落库）五表联查。
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/system/hermes-audit")
@Tag(name = "Hermes 审计", description = "V2.0 铁律五：MCP 工具调用审计追溯（append-only）")
public class HermesAuditController {

    private final HermesToolAuditService auditService;

    public HermesAuditController(HermesToolAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "审计记录查询（runId/userId/toolName 过滤；负责人看全部，其他用户仅本人）")
    public R<Map<String, Object>> query(
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String toolName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        LoginUser user = LoginUser.current();
        boolean leader = user.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEPT_LEADER".equals(a.getAuthority()));
        Long effectiveUserId = leader ? userId : user.getUserId();
        List<HermesToolAudit> rows = auditService.query(runId, effectiveUserId, toolName, pageNum, pageSize);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", rows);
        out.put("total", rows.size());
        out.put("chainVerified", auditService.verifyChain(Math.max(rows.size(), 100)));
        return R.ok(out);
    }
}
