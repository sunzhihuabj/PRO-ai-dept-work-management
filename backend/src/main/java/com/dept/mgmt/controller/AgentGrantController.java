package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.platform.identity.dto.AgentGrantDTO;
import com.dept.mgmt.platform.identity.entity.SysAgentGrant;
import com.dept.mgmt.platform.identity.service.AgentGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 代理授权接口（数据面，T-107 MasterAgent 消费）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/agent-grants")
@RequiredArgsConstructor
@Tag(name = "Agent代理授权", description = "ASSISTANT 能力白名单管理")
public class AgentGrantController {

    private final AgentGrantService agentGrantService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "创建/续期授权（默认72小时）")
    @OperLog(title = "Agent代理授权", businessType = 1)
    public R<Long> create(@Valid @RequestBody AgentGrantDTO dto) {
        return R.ok("授权成功", agentGrantService.create(dto));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "授权列表（可按被代理人过滤）")
    public R<List<SysAgentGrant>> list(@RequestParam(required = false) Long principalUserId) {
        return R.ok(agentGrantService.list(principalUserId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "撤销授权")
    @OperLog(title = "Agent代理授权", businessType = 3)
    public R<Void> revoke(@PathVariable Long id) {
        agentGrantService.revoke(id);
        return R.ok("授权已撤销", null);
    }
}
