package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.PhaseGate;
import com.dept.mgmt.entity.TaskPool;
import com.dept.mgmt.mapper.TaskPoolMapper;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.GateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 阶段门禁 + 任务池接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/gates")
@RequiredArgsConstructor
@Tag(name = "阶段门禁", description = "阶段门禁 CRUD、审核；任务池查询")
public class GateController {

    private final GateService gateService;
    private final TaskPoolMapper taskPoolMapper;
    private final com.dept.mgmt.mapper.TaskMapper taskMapper;
    private final com.dept.mgmt.service.ProjectService projectService;

    // ---------- 门禁 ----------
    @GetMapping
    @Operation(summary = "项目门禁列表")
    public R<List<PhaseGate>> list(@RequestParam Long projectId) {
        return R.ok(gateService.listByProject(projectId));
    }

    @PostMapping
    @Operation(summary = "新建门禁")
    @OperLog(title = "阶段门禁", businessType = 1)
    public R<Long> create(@RequestBody PhaseGate dto) {
        return R.ok("门禁创建成功", gateService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新门禁")
    @OperLog(title = "阶段门禁", businessType = 2)
    public R<Void> update(@PathVariable Long id, @RequestBody PhaseGate dto) {
        gateService.update(id, dto);
        return R.ok("更新成功", null);
    }

    @PostMapping("/{id}/audit")
    @Operation(summary = "审核门禁（通过/驳回）")
    @OperLog(title = "阶段门禁", businessType = 2)
    public R<PhaseGate> audit(@PathVariable Long id, @RequestBody GateAuditDTO dto) {
        return R.ok("审核完成", gateService.audit(id, Boolean.TRUE.equals(dto.getApproved()), dto.getComment()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除门禁")
    @OperLog(title = "阶段门禁", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        gateService.delete(id);
        return R.ok("删除成功", null);
    }

    // ---------- 任务池（标准任务模板库查询 + 领取生成任务） ----------
    @GetMapping("/pool")
    @Operation(summary = "任务池列表（标准任务模板）")
    public R<List<TaskPool>> pool(@RequestParam(required = false) Integer projectType,
                                  @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<TaskPool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectType != null, TaskPool::getProjectType, projectType);
        wrapper.eq(status != null, TaskPool::getStatus, status);
        wrapper.orderByAsc(TaskPool::getPhaseOrder);
        return R.ok(taskPoolMapper.selectList(wrapper));
    }

    /**
     * 从任务池领取：按模板创建一条真实任务到指定项目
     */
    @PostMapping("/pool/{poolId}/claim")
    @Operation(summary = "从任务池领取任务（生成到指定项目）")
    @OperLog(title = "任务管理", businessType = 1)
    public R<Long> claim(@PathVariable Long poolId,
                         @RequestParam Long projectId,
                         @RequestParam Long assigneeId) {
        TaskPool pool = taskPoolMapper.selectById(poolId);
        if (pool == null) {
            return R.fail(404, "任务池条目不存在");
        }
        if (pool.getStatus() != null && pool.getStatus() == 0) {
            return R.fail(409, "该任务已停用，不可领取");
        }
        // 匹配项目阶段（按池中 phaseOrder）
        var phases = projectService.listPhases(projectId);
        Long phaseId = phases.stream()
                .filter(p -> p.getPhaseOrder().equals(pool.getPhaseOrder()))
                .map(com.dept.mgmt.entity.ProjectPhase::getId)
                .findFirst().orElse(null);

        com.dept.mgmt.entity.Task task = new com.dept.mgmt.entity.Task();
        task.setProjectId(projectId);
        task.setPhaseId(phaseId);
        task.setTaskName(pool.getTaskName());
        task.setTaskType(pool.getTaskType());
        task.setValueType(pool.getValueType());
        task.setAssigneeId(assigneeId);
        task.setPriority(2);
        task.setStatus(0);
        task.setProgress(0);
        task.setPlanHours(pool.getPlanHours());
        task.setDescription(pool.getDescription());
        task.setPullMode(1); // 拉动模式：从池领取
        LoginUser loginUser = LoginUser.current();
        task.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");
        taskMapper.insert(task);
        log.info("任务池领取: poolId={}, projectId={}, taskId={}", poolId, projectId, task.getId());
        return R.ok("领取成功，任务已生成", task.getId());
    }

    @Data
    public static class GateAuditDTO {
        private Boolean approved;
        private String comment;
    }
}
