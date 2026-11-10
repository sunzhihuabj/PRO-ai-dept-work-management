package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.A3Report;
import com.dept.mgmt.entity.ImprovementProposal;
import com.dept.mgmt.entity.LessonsLearned;
import com.dept.mgmt.entity.RootCauseAnalysis;
import com.dept.mgmt.entity.ValueStreamRecord;
import com.dept.mgmt.entity.WasteRecord;
import com.dept.mgmt.service.LeanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 精益改进接口：提案 / 根因分析 / 经验教训
 */
@Slf4j
@RestController
@RequestMapping("/v1/lean")
@RequiredArgsConstructor
@Tag(name = "精益改进", description = "改进提案、5Why/鱼骨图根因分析、经验教训库")
public class LeanController {

    private final LeanService leanService;

    // ==================== 改进提案 ====================

    @GetMapping("/proposals")
    @Operation(summary = "提案分页查询")
    public R<PageResult<ImprovementProposal>> listProposals(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String proposalType,
            @RequestParam(required = false) String keyword) {
        return R.ok(leanService.listProposals(pageNum, pageSize, projectId, status, proposalType, keyword));
    }

    @GetMapping("/proposals/{id}")
    @Operation(summary = "提案详情")
    public R<ImprovementProposal> proposalDetail(@PathVariable Long id) {
        return R.ok(leanService.proposalDetail(id));
    }

    @PostMapping("/proposals")
    @Operation(summary = "创建提案")
    @OperLog(title = "精益改进", businessType = 1)
    public R<Long> createProposal(@RequestBody ImprovementProposal dto) {
        return R.ok("提案创建成功", leanService.createProposal(dto));
    }

    @PutMapping("/proposals/{id}")
    @Operation(summary = "更新提案（待审核/已驳回）")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> updateProposal(@PathVariable Long id, @RequestBody ImprovementProposal dto) {
        leanService.updateProposal(id, dto);
        return R.ok("提案更新成功", null);
    }

    @DeleteMapping("/proposals/{id}")
    @Operation(summary = "删除提案（待审核/已驳回）")
    @OperLog(title = "精益改进", businessType = 3)
    public R<Void> deleteProposal(@PathVariable Long id) {
        leanService.deleteProposal(id);
        return R.ok("提案删除成功", null);
    }

    @PostMapping("/proposals/{id}/review")
    @Operation(summary = "评审提案（通过/驳回）")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> reviewProposal(@PathVariable Long id, @RequestBody ReviewDTO dto) {
        leanService.reviewProposal(id, Boolean.TRUE.equals(dto.getApproved()), dto.getComment());
        return R.ok("评审完成", null);
    }

    @PostMapping("/proposals/{id}/advance")
    @Operation(summary = "推进状态（审核通过→实施中→已完成）")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> advanceProposal(@PathVariable Long id) {
        leanService.advanceProposal(id);
        return R.ok("推进成功", null);
    }

    // ==================== 根因分析 ====================

    @GetMapping("/rcas")
    @Operation(summary = "根因分析分页查询")
    public R<PageResult<RootCauseAnalysis>> listRcas(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String analysisMethod,
            @RequestParam(required = false) Integer status) {
        return R.ok(leanService.listRcas(pageNum, pageSize, projectId, analysisMethod, status));
    }

    @GetMapping("/rcas/{id}")
    @Operation(summary = "根因分析详情")
    public R<RootCauseAnalysis> rcaDetail(@PathVariable Long id) {
        return R.ok(leanService.rcaDetail(id));
    }

    @PostMapping("/rcas")
    @Operation(summary = "创建根因分析（5Why/鱼骨图等）")
    @OperLog(title = "精益改进", businessType = 1)
    public R<Long> createRca(@RequestBody RootCauseAnalysis dto) {
        return R.ok("分析创建成功", leanService.createRca(dto));
    }

    @PutMapping("/rcas/{id}")
    @Operation(summary = "更新根因分析")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> updateRca(@PathVariable Long id, @RequestBody RootCauseAnalysis dto) {
        leanService.updateRca(id, dto);
        return R.ok("分析更新成功", null);
    }

    @PostMapping("/rcas/{id}/advance")
    @Operation(summary = "推进状态（分析中→已完成→已关闭）")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> advanceRca(@PathVariable Long id, @RequestParam Integer targetStatus) {
        leanService.advanceRca(id, targetStatus);
        return R.ok("推进成功", null);
    }

    @DeleteMapping("/rcas/{id}")
    @Operation(summary = "删除根因分析")
    @OperLog(title = "精益改进", businessType = 3)
    public R<Void> deleteRca(@PathVariable Long id) {
        leanService.deleteRca(id);
        return R.ok("删除成功", null);
    }

    // ==================== 经验教训 ====================

    @GetMapping("/lessons")
    @Operation(summary = "经验库分页查询")
    public R<PageResult<LessonsLearned>> listLessons(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return R.ok(leanService.listLessons(pageNum, pageSize, projectId, category, keyword));
    }

    @GetMapping("/lessons/{id}")
    @Operation(summary = "经验详情")
    public R<LessonsLearned> lessonDetail(@PathVariable Long id) {
        return R.ok(leanService.lessonDetail(id));
    }

    @PostMapping("/lessons")
    @Operation(summary = "沉淀经验")
    @OperLog(title = "精益改进", businessType = 1)
    public R<Long> createLesson(@RequestBody LessonsLearned dto) {
        return R.ok("经验沉淀成功", leanService.createLesson(dto));
    }

    @PutMapping("/lessons/{id}")
    @Operation(summary = "更新经验")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> updateLesson(@PathVariable Long id, @RequestBody LessonsLearned dto) {
        leanService.updateLesson(id, dto);
        return R.ok("经验更新成功", null);
    }

    @DeleteMapping("/lessons/{id}")
    @Operation(summary = "删除经验")
    @OperLog(title = "精益改进", businessType = 3)
    public R<Void> deleteLesson(@PathVariable Long id) {
        leanService.deleteLesson(id);
        return R.ok("删除成功", null);
    }

    @Data
    public static class ReviewDTO {
        private Boolean approved;
        private String comment;
    }

    // ==================== A3 报告 ====================

    @GetMapping("/a3-reports")
    @Operation(summary = "A3报告分页查询")
    public R<PageResult<A3Report>> listA3s(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer status) {
        return R.ok(leanService.listA3s(pageNum, pageSize, projectId, status));
    }

    @GetMapping("/a3-reports/{id}")
    @Operation(summary = "A3报告详情")
    public R<A3Report> a3Detail(@PathVariable Long id) {
        return R.ok(leanService.a3Detail(id));
    }

    @PostMapping("/a3-reports")
    @Operation(summary = "新建A3报告")
    @OperLog(title = "精益改进", businessType = 1)
    public R<Long> createA3(@RequestBody A3Report dto) {
        return R.ok("A3报告创建成功", leanService.createA3(dto));
    }

    @PutMapping("/a3-reports/{id}")
    @Operation(summary = "更新A3报告")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> updateA3(@PathVariable Long id, @RequestBody A3Report dto) {
        leanService.updateA3(id, dto);
        return R.ok("A3报告更新成功", null);
    }

    @PostMapping("/a3-reports/{id}/advance")
    @Operation(summary = "A3状态推进（草稿→进行中→已完成→已关闭）")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> advanceA3(@PathVariable Long id, @RequestParam Integer targetStatus) {
        leanService.advanceA3(id, targetStatus);
        return R.ok("推进成功", null);
    }

    @DeleteMapping("/a3-reports/{id}")
    @Operation(summary = "删除A3报告")
    @OperLog(title = "精益改进", businessType = 3)
    public R<Void> deleteA3(@PathVariable Long id) {
        leanService.deleteA3(id);
        return R.ok("删除成功", null);
    }

    // ==================== 价值流记录 ====================

    @GetMapping("/value-streams")
    @Operation(summary = "价值流记录分页查询")
    public R<PageResult<ValueStreamRecord>> listValueStreams(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId) {
        return R.ok(leanService.listValueStreams(pageNum, pageSize, projectId));
    }

    @PostMapping("/value-streams")
    @Operation(summary = "保存价值流记录（流程效率自动计算）")
    @OperLog(title = "精益改进", businessType = 1)
    public R<Long> saveValueStream(@RequestBody ValueStreamRecord dto) {
        return R.ok("保存成功", leanService.saveValueStream(dto));
    }

    @DeleteMapping("/value-streams/{id}")
    @Operation(summary = "删除价值流记录")
    @OperLog(title = "精益改进", businessType = 3)
    public R<Void> deleteValueStream(@PathVariable Long id) {
        leanService.deleteValueStream(id);
        return R.ok("删除成功", null);
    }

    // ==================== 浪费记录 ====================

    @GetMapping("/wastes")
    @Operation(summary = "浪费记录分页查询")
    public R<PageResult<WasteRecord>> listWastes(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String wasteType,
            @RequestParam(required = false) Integer status) {
        return R.ok(leanService.listWastes(pageNum, pageSize, projectId, wasteType, status));
    }

    @PostMapping("/wastes")
    @Operation(summary = "登记/更新浪费记录")
    @OperLog(title = "精益改进", businessType = 1)
    public R<Long> saveWaste(@RequestBody WasteRecord dto) {
        return R.ok("保存成功", leanService.saveWaste(dto));
    }

    @PostMapping("/wastes/{id}/advance")
    @Operation(summary = "浪费状态推进（未处理→处理中→已消除→已关闭）")
    @OperLog(title = "精益改进", businessType = 2)
    public R<Void> advanceWaste(@PathVariable Long id, @RequestParam Integer targetStatus) {
        leanService.advanceWaste(id, targetStatus);
        return R.ok("推进成功", null);
    }

    @DeleteMapping("/wastes/{id}")
    @Operation(summary = "删除浪费记录")
    @OperLog(title = "精益改进", businessType = 3)
    public R<Void> deleteWaste(@PathVariable Long id) {
        leanService.deleteWaste(id);
        return R.ok("删除成功", null);
    }
}
