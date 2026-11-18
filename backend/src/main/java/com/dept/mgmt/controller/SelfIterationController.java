package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.entity.AgentFeedback;
import com.dept.mgmt.aiworkspace.entity.HermesRegressionCase;
import com.dept.mgmt.aiworkspace.entity.SoulChangeLog;
import com.dept.mgmt.aiworkspace.entity.SoulPatchProposal;
import com.dept.mgmt.aiworkspace.service.RegressionSuiteService;
import com.dept.mgmt.aiworkspace.service.SelfIterationService;
import com.dept.mgmt.aiworkspace.service.SoulManageService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自迭代闭环接口（V2.0 M8/D7）：反馈采集/统计、补丁建议采纳、SOUL 版本链、回归集。
 *
 * @author dept-mgmt
 */
@Tag(name = "AI自迭代（V2.0 M8）")
@RestController
@RequestMapping("/v1/ai/self-iteration")
@RequiredArgsConstructor
public class SelfIterationController {

    private final SelfIterationService selfIterationService;
    private final SoulManageService soulManageService;
    private final RegressionSuiteService regressionSuiteService;
    private final com.dept.mgmt.aiworkspace.service.SkillTemplateService skillTemplateService;

    @Operation(summary = "提交反馈（±1 评分，RUN/MESSAGE/TASK 三源）")
    @PostMapping("/feedback")
    public R<AgentFeedback> feedback(@RequestBody Map<String, Object> body) {
        return R.ok(selfIterationService.submitFeedback(
                str(body.get("sourceType")),
                body.get("sourceId") == null ? null : Long.valueOf(String.valueOf(body.get("sourceId"))),
                str(body.get("runId")),
                body.get("rating") == null ? 0 : Integer.parseInt(String.valueOf(body.get("rating"))),
                str(body.get("dimension")),
                str(body.get("comment"))));
    }

    @Operation(summary = "反馈统计（总量/好评率）")
    @GetMapping("/feedback/stats")
    public R<Map<String, Object>> feedbackStats() {
        return R.ok(selfIterationService.feedbackStats());
    }

    @Operation(summary = "补丁建议列表（status=PROPOSED/ADOPTED/REJECTED 可选）")
    @GetMapping("/proposals")
    public R<List<SoulPatchProposal>> proposals(@RequestParam(required = false) String status) {
        return R.ok(selfIterationService.listProposals(status));
    }

    @Operation(summary = "采纳补丁建议（回归门槛 ≥90%，写入 SOUL.md 版本链）")
    @PostMapping("/proposals/{id}/adopt")
    public R<Map<String, Object>> adopt(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = selfIterationService.adopt(id,
                body == null ? null : str(body.get("note")));
        return R.ok(result);
    }

    @Operation(summary = "驳回补丁建议")
    @PostMapping("/proposals/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        selfIterationService.reject(id, body == null ? null : str(body.get("note")));
        return R.ok();
    }

    @Operation(summary = "手动触发周迭代归因（补跑用）")
    @PostMapping("/iterate")
    public R<Map<String, Object>> iterate() {
        return R.ok(selfIterationService.weeklyIterate(java.time.LocalDate.now()));
    }

    @Operation(summary = "SOUL.md 版本链（新→旧）")
    @GetMapping("/soul/versions")
    public R<List<SoulChangeLog>> soulVersions() {
        return R.ok(soulManageService.versions());
    }

    @Operation(summary = "SOUL.md 回滚到指定版本")
    @PostMapping("/soul/rollback/{version}")
    public R<Map<String, Object>> rollback(@PathVariable String version) {
        LoginUser user = LoginUser.current();
        return R.ok(soulManageService.rollback(version, user == null ? "system" : user.getUsername()));
    }

    @Operation(summary = "执行黄金指令回归（ROUTE 类）")
    @PostMapping("/regression/run")
    public R<Map<String, Object>> regressionRun() {
        return R.ok(regressionSuiteService.runAll());
    }

    @Operation(summary = "越权用例结果回写（验收脚本 HTTP 403 断言后调用）")
    @PostMapping("/regression/perm-result")
    public R<Void> permResult(@RequestParam String caseNo, @RequestParam boolean pass) {
        regressionSuiteService.writePermResult(caseNo, pass);
        return R.ok();
    }

    @Operation(summary = "回归用例清单（含最近结果）")
    @GetMapping("/regression/cases")
    public R<List<HermesRegressionCase>> regressionCases() {
        return R.ok(regressionSuiteService.listCases());
    }

    @Operation(summary = "任务书模板沉淀清单（成功率/使用统计）")
    @GetMapping("/skills")
    public R<List<com.dept.mgmt.aiworkspace.entity.SkillTemplate>> skills() {
        return R.ok(skillTemplateService.list());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
