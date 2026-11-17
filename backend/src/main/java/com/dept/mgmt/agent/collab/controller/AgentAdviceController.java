package com.dept.mgmt.agent.collab.controller;

import com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice;
import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 决策建议接口（阶段一多 Agent 协同）
 * 查询开放给登录用户；审批（采纳/驳回）仅部门负责人——与既有 Agent 写操作权限口径一致
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/agent-advices")
@RequiredArgsConstructor
@Tag(name = "Agent 决策建议", description = "多 Agent 协同：建议查询 + 人工审批（HITL）")
public class AgentAdviceController {

    private final DecisionAdviceService adviceService;

    @GetMapping
    @Operation(summary = "分页查询决策建议")
    public R<PageResult<AgentDecisionAdvice>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String scene) {
        return R.ok(adviceService.page(status, scene, pageNum, pageSize));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "采纳建议（HMAC 校验 + 动作执行，仅部门负责人）")
    public R<AgentDecisionAdvice> approve(@PathVariable Long id,
                                          @RequestBody(required = false) AdviceOpDTO body) {
        return R.ok("已采纳", adviceService.approve(id, body == null ? null : body.getComment()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "驳回建议（仅部门负责人）")
    public R<AgentDecisionAdvice> reject(@PathVariable Long id,
                                         @RequestBody(required = false) AdviceOpDTO body) {
        return R.ok("已驳回", adviceService.reject(id, body == null ? null : body.getComment()));
    }

    /** 审批操作入参（comment 可空） */
    @Data
    public static class AdviceOpDTO {
        private String comment;
    }
}
