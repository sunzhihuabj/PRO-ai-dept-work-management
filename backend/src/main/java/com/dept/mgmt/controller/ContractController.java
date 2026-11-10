package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.contract.service.ContractService;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * W2 合同域接口（T-111）
 * 审批/用印/关闭为部门领导专属（审批类能力红线：AI 不可代行）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "合同管理", description = "台账、会签、审批、用印、生效（W2）")
public class ContractController {

    private final ContractService contractService;

    @PostMapping
    @Operation(summary = "创建合同草稿")
    @OperLog(title = "合同管理", businessType = 1)
    public R<Long> create(@RequestBody Contract dto) {
        return R.ok("草稿已创建", contractService.create(dto));
    }

    @GetMapping
    @Operation(summary = "合同分页")
    public R<PageResult<Contract>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String status) {
        return R.ok(contractService.list(pageNum, pageSize, name, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "合同详情（含审批记录）")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(contractService.detail(id));
    }

    @PostMapping("/{id}/submit-countersign")
    @Operation(summary = "提交会签（决策门 G1：收款比例合计=100）")
    public R<Void> submitCountersign(@PathVariable Long id) {
        contractService.submitCountersign(id);
        return R.ok("已提交会签", null);
    }

    @PostMapping("/{id}/countersign")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "会签（通过→审批节点；驳回→草稿）")
    @OperLog(title = "合同管理", businessType = 2)
    public R<Void> countersign(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireLeader();
        contractService.countersign(id, "APPROVED".equals(body.get("result")), body.get("comment"));
        return R.ok("会签已处理", null);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "审批（通过→用印节点；驳回→草稿）")
    @OperLog(title = "合同管理", businessType = 2)
    public R<Void> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireLeader();
        contractService.approve(id, "APPROVED".equals(body.get("result")), body.get("comment"));
        return R.ok("审批已处理", null);
    }

    @PostMapping("/{id}/seal")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "用印登记（决策门 G2：用印完成合同生效，发布跨域事件）")
    @OperLog(title = "合同管理", businessType = 2)
    public R<Void> seal(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireLeader();
        contractService.seal(id, body.get("comment"));
        return R.ok("用印登记完成，合同已生效", null);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "关闭/终止合同")
    public R<Void> close(@PathVariable Long id, @RequestParam(defaultValue = "false") Boolean terminated) {
        requireLeader();
        contractService.close(id, Boolean.TRUE.equals(terminated));
        return R.ok("已处理", null);
    }

    private void requireLeader() {
        LoginUser user = LoginUser.current();
        if (user == null || user.getRoles() == null || !user.getRoles().contains("ROLE_DEPT_LEADER")) {
            throw new BusinessException(403, "仅部门领导可执行审批操作");
        }
    }
}
