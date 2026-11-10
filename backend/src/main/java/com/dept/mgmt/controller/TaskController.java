package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.TaskDTO;
import com.dept.mgmt.dto.WorkHourDTO;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.entity.TaskLog;
import com.dept.mgmt.entity.WorkHour;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.TaskService;
import com.dept.mgmt.common.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "任务管理", description = "任务CRUD、状态流转、工时记录")
public class TaskController {

    private final TaskService taskService;

    /**
     * 任务列表分页
     */
    @GetMapping
    @Operation(summary = "任务列表分页查询")
    public R<PageResult<Task>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword) {
        return R.ok(taskService.list(pageNum, pageSize, projectId, status, priority, assigneeId, keyword));
    }

    /**
     * 任务详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "任务详情")
    public R<Task> detail(@PathVariable Long id) {
        return R.ok(taskService.detail(id));
    }

    /**
     * 创建任务
     */
    @PostMapping
    @Operation(summary = "创建任务")
    @OperLog(title = "任务管理", businessType = 1)
    public R<Long> create(@Valid @RequestBody TaskDTO dto) {
        Long id = taskService.create(dto);
        return R.ok("任务创建成功", id);
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新任务")
    @OperLog(title = "任务管理", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TaskDTO dto) {
        taskService.update(id, dto);
        return R.ok("任务更新成功", null);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务")
    @OperLog(title = "任务管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return R.ok("任务删除成功", null);
    }

    /**
     * 任务状态流转
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "任务状态流转")
    @OperLog(title = "任务管理", businessType = 2)
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody StatusChangeDTO dto) {
        taskService.changeStatus(id, dto.getTargetStatus(), dto.getComment());
        return R.ok("状态流转成功", null);
    }

    /**
     * 更新任务进度
     */
    @PutMapping("/{id}/progress")
    @Operation(summary = "更新任务进度")
    public R<Void> updateProgress(@PathVariable Long id, @RequestBody ProgressDTO dto) {
        taskService.updateProgress(id, dto.getProgress());
        return R.ok("进度更新成功", null);
    }

    /**
     * 录入工时
     */
    @PostMapping("/{id}/hours")
    @Operation(summary = "录入工时")
    @OperLog(title = "任务管理", businessType = 2)
    public R<WorkHour> addWorkHour(@PathVariable Long id, @Valid @RequestBody WorkHourDTO dto) {
        return R.ok("工时录入成功", taskService.addWorkHour(id, dto));
    }

    /**
     * 工时列表
     */
    @GetMapping("/{id}/hours")
    @Operation(summary = "任务工时列表")
    public R<List<WorkHour>> listWorkHours(@PathVariable Long id) {
        return R.ok(taskService.listWorkHours(id));
    }

    /**
     * 任务日志列表
     */
    @GetMapping("/{id}/logs")
    @Operation(summary = "任务日志列表")
    public R<List<TaskLog>> listTaskLogs(@PathVariable Long id) {
        return R.ok(taskService.listTaskLogs(id));
    }

    /**
     * 我的待办任务
     */
    @GetMapping("/my-todo")
    @Operation(summary = "我的待办任务")
    public R<List<Task>> myTodo() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(taskService.myTodoTasks(loginUser.getUserId()));
    }

    @Data
    public static class StatusChangeDTO {
        private Integer targetStatus;
        private String comment;
    }

    @Data
    public static class ProgressDTO {
        private Integer progress;
    }
}
