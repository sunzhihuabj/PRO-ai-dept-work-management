package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.service.DocComplianceService;
import com.dept.mgmt.aiworkspace.service.DocDraftService;
import com.dept.mgmt.aiworkspace.entity.AiDocDraft;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文档工作台接口（V2.0 M6）：草稿列表/详情/编辑/送审/审核通过 + 合规检查。
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/ai/docs")
@RequiredArgsConstructor
@Tag(name = "AI 文档工作台", description = "V2.0 M6：AI 起草落库 → 查看/编辑/合规审核/送审")
public class DocDraftController {

    private final DocDraftService docDraftService;
    private final DocComplianceService docComplianceService;

    @GetMapping
    @Operation(summary = "草稿列表（status 过滤）")
    public R<List<AiDocDraft>> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "50") int limit) {
        return R.ok(docDraftService.list(status, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "草稿详情（含正文）")
    public R<AiDocDraft> get(@PathVariable Long id) {
        return R.ok(docDraftService.get(id));
    }

    @PostMapping
    @Operation(summary = "创建草稿（人工/MANUAL 来源）")
    public R<AiDocDraft> create(@RequestBody Map<String, String> body) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(docDraftService.create(body.get("title"), body.get("contentMd"),
                "MANUAL", null, user.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑草稿（版本 +1）")
    public R<AiDocDraft> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(docDraftService.update(id, body.get("title"), body.get("contentMd")));
    }

    @PostMapping("/{id}/submit-review")
    @Operation(summary = "送审（DRAFT → REVIEW）")
    public R<AiDocDraft> submitReview(@PathVariable Long id) {
        return R.ok(docDraftService.submitReview(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审核通过（REVIEW → APPROVED）")
    public R<AiDocDraft> approve(@PathVariable Long id) {
        return R.ok(docDraftService.approve(id));
    }

    @PostMapping("/{id}/compliance-check")
    @Operation(summary = "合规检查（kb 规范依据 + LLM 风险清单）")
    public R<Map<String, Object>> complianceCheck(@PathVariable Long id) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(docComplianceService.check(id, user.getUserId()));
    }
}
