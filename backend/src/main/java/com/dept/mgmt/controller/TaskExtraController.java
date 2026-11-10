package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.TaskComment;
import com.dept.mgmt.entity.TaskSub;
import com.dept.mgmt.service.TaskExtraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务扩展接口：子任务 / 评论
 */
@Slf4j
@RestController
@RequestMapping("/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "任务扩展", description = "子任务拆解、任务评论协作")
public class TaskExtraController {

    private final TaskExtraService taskExtraService;

    // ---------- 子任务 ----------
    @GetMapping("/{id}/subs")
    @Operation(summary = "子任务列表")
    public R<List<TaskSub>> listSubs(@PathVariable Long id) {
        return R.ok(taskExtraService.listSubs(id));
    }

    @PostMapping("/{id}/subs")
    @Operation(summary = "添加子任务")
    @OperLog(title = "任务管理", businessType = 1)
    public R<TaskSub> addSub(@PathVariable Long id, @RequestBody TaskSub dto) {
        return R.ok("子任务已添加", taskExtraService.addSub(id, dto));
    }

    @PutMapping("/subs/{subId}")
    @Operation(summary = "更新子任务")
    @OperLog(title = "任务管理", businessType = 2)
    public R<Void> updateSub(@PathVariable Long subId, @RequestBody TaskSub dto) {
        taskExtraService.updateSub(subId, dto);
        return R.ok("更新成功", null);
    }

    @PutMapping("/subs/{subId}/toggle")
    @Operation(summary = "勾选子任务状态（0待办/1进行中/2完成）")
    public R<Void> toggleSub(@PathVariable Long subId, @RequestParam Integer status) {
        taskExtraService.toggleSub(subId, status);
        return R.ok("已更新", null);
    }

    @DeleteMapping("/subs/{subId}")
    @Operation(summary = "删除子任务")
    @OperLog(title = "任务管理", businessType = 3)
    public R<Void> deleteSub(@PathVariable Long subId) {
        taskExtraService.deleteSub(subId);
        return R.ok("删除成功", null);
    }

    // ---------- 评论 ----------
    @GetMapping("/{id}/comments")
    @Operation(summary = "评论列表")
    public R<List<TaskComment>> listComments(@PathVariable Long id) {
        return R.ok(taskExtraService.listComments(id));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "发表评论")
    public R<TaskComment> addComment(@PathVariable Long id, @RequestBody TaskComment dto) {
        return R.ok("评论成功", taskExtraService.addComment(id, dto));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "删除评论（仅本人）")
    public R<Void> deleteComment(@PathVariable Long commentId) {
        taskExtraService.deleteComment(commentId);
        return R.ok("删除成功", null);
    }
}
