package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.chnchange.entity.EcnChange;
import com.dept.mgmt.chnchange.entity.EcnRecallTask;
import com.dept.mgmt.chnchange.service.EcnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * W5 变更域接口（T-302）
 * BR-07：技术变更必须走 ECN 流程、旧版资料必须回收（实施时自动生成回收任务并受控归档）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/ecn")
@RequiredArgsConstructor
@Tag(name = "变更管理", description = "ECN 变更单/旧版回收/批量齐套（W5，BR-07）")
public class EcnController {

    private final EcnService ecnService;

    @PostMapping
    @Operation(summary = "创建 ECN 变更单（草稿）")
    @OperLog(title = "变更管理", businessType = 1)
    public R<Long> createEcn(@RequestBody Map<String, Object> body) {
        return R.ok("ECN 已创建", ecnService.createEcn(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.get("title"),
                (String) body.get("reason"),
                (String) body.getOrDefault("changeType", "DESIGN"),
                body.get("originSchemeId") == null ? null : Long.valueOf(String.valueOf(body.get("originSchemeId"))),
                (String) body.getOrDefault("impactLevel", "B")));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交 ECN 审批")
    public R<Void> submitEcn(@PathVariable Long id) {
        ecnService.submitEcn(id);
        return R.ok("已提交审批", null);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "ECN 审批（approved=true 批准 / false 驳回）")
    public R<Void> approveEcn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ecnService.approveEcn(id,
                (String) body.getOrDefault("approver", "未署名"),
                Boolean.parseBoolean(String.valueOf(body.getOrDefault("approved", "true"))),
                (String) body.get("remark"));
        return R.ok("审批完成", null);
    }

    @PostMapping("/{id}/implement")
    @Operation(summary = "ECN 实施（BR-07：旧版回收任务自动生成 + 批量齐套复检 + 缺料请购草稿）")
    @OperLog(title = "变更管理", businessType = 2)
    public R<Map<String, Object>> implementEcn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok("ECN 已实施", ecnService.implementEcn(
                id,
                body.get("newSchemeId") == null ? null : Long.valueOf(String.valueOf(body.get("newSchemeId"))),
                (String) body.get("recallOwner"),
                body.get("recallDueDays") == null ? null : Integer.valueOf(String.valueOf(body.get("recallDueDays")))));
    }

    @GetMapping
    @Operation(summary = "ECN 分页")
    public R<PageResult<EcnChange>> listEcns(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String status) {
        return R.ok(ecnService.listEcns(pageNum, pageSize, projectId, status));
    }

    @GetMapping("/recall-tasks")
    @Operation(summary = "旧版资料回收任务（按 ECN/状态）")
    public R<List<EcnRecallTask>> recallTasks(@RequestParam(required = false) Long ecnId,
                                              @RequestParam(required = false) String status) {
        return R.ok(ecnService.listRecallTasks(ecnId, status));
    }

    @PostMapping("/recall-tasks/{id}/confirm")
    @Operation(summary = "回收完成确认")
    public R<Void> confirmRecall(@PathVariable Long id) {
        ecnService.confirmRecall(id);
        return R.ok("回收已确认", null);
    }
}
