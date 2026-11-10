package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.ProjectDTO;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.ProjectPhase;
import com.dept.mgmt.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
@Tag(name = "项目管理", description = "项目CRUD、阶段管理")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 项目列表分页
     */
    @GetMapping
    @Operation(summary = "项目列表分页查询")
    public R<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer projectType) {
        return R.ok(projectService.list(pageNum, pageSize, name, status, projectType));
    }

    /**
     * 项目详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "项目详情")
    public R<Project> detail(@PathVariable Long id) {
        return R.ok(projectService.detail(id));
    }

    /**
     * 创建项目
     */
    @PostMapping
    @Operation(summary = "创建项目")
    public R<Long> create(@Valid @RequestBody ProjectDTO dto) {
        Long id = projectService.create(dto);
        return R.ok("项目创建成功", id);
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新项目")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectDTO dto) {
        projectService.update(id, dto);
        return R.ok("项目更新成功", null);
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return R.ok("项目删除成功", null);
    }

    /**
     * 项目阶段列表
     */
    @GetMapping("/{id}/phases")
    @Operation(summary = "项目阶段列表")
    public R<List<ProjectPhase>> listPhases(@PathVariable Long id) {
        return R.ok(projectService.listPhases(id));
    }

    /**
     * 更新阶段状态
     */
    @PutMapping("/phases/{phaseId}/status")
    @Operation(summary = "更新阶段状态")
    public R<Void> updatePhaseStatus(@PathVariable Long phaseId, @RequestBody PhaseStatusDTO dto) {
        projectService.updatePhaseStatus(phaseId, dto.getStatus());
        return R.ok("阶段状态更新成功", null);
    }

    @Data
    public static class PhaseStatusDTO {
        private Integer status;
    }

    // ==================== 项目成员 ====================

    @GetMapping("/{id}/members")
    @Operation(summary = "项目成员列表")
    public R<List<com.dept.mgmt.entity.ProjectMember>> listMembers(@PathVariable Long id) {
        return R.ok(projectService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "添加项目成员")
    @com.dept.mgmt.common.annotation.OperLog(title = "项目管理", businessType = 1)
    public R<com.dept.mgmt.entity.ProjectMember> addMember(@PathVariable Long id, @RequestBody MemberDTO dto) {
        return R.ok("添加成功", projectService.addMember(id, dto.getUserId(), dto.getRoleType()));
    }

    @DeleteMapping("/members/{memberId}")
    @Operation(summary = "移除项目成员")
    @com.dept.mgmt.common.annotation.OperLog(title = "项目管理", businessType = 3)
    public R<Void> removeMember(@PathVariable Long memberId) {
        projectService.removeMember(memberId);
        return R.ok("移除成功", null);
    }

    // ==================== 里程碑 ====================

    @GetMapping("/{id}/milestones")
    @Operation(summary = "项目里程碑列表")
    public R<List<com.dept.mgmt.entity.Milestone>> listMilestones(@PathVariable Long id) {
        return R.ok(projectService.listMilestones(id));
    }

    @PostMapping("/{id}/milestones")
    @Operation(summary = "新建里程碑")
    @com.dept.mgmt.common.annotation.OperLog(title = "项目管理", businessType = 1)
    public R<Long> createMilestone(@PathVariable Long id, @RequestBody com.dept.mgmt.entity.Milestone dto) {
        dto.setProjectId(id);
        return R.ok("里程碑创建成功", projectService.createMilestone(dto));
    }

    @PutMapping("/milestones/{milestoneId}")
    @Operation(summary = "更新里程碑")
    @com.dept.mgmt.common.annotation.OperLog(title = "项目管理", businessType = 2)
    public R<Void> updateMilestone(@PathVariable Long milestoneId, @RequestBody com.dept.mgmt.entity.Milestone dto) {
        projectService.updateMilestone(milestoneId, dto);
        return R.ok("里程碑更新成功", null);
    }

    @PostMapping("/milestones/{milestoneId}/mark")
    @Operation(summary = "标记里程碑（status=1完成 / 2延期）")
    @com.dept.mgmt.common.annotation.OperLog(title = "项目管理", businessType = 2)
    public R<Void> markMilestone(@PathVariable Long milestoneId, @RequestParam Integer status) {
        projectService.markMilestone(milestoneId, status);
        return R.ok("标记成功", null);
    }

    @DeleteMapping("/milestones/{milestoneId}")
    @Operation(summary = "删除里程碑")
    @com.dept.mgmt.common.annotation.OperLog(title = "项目管理", businessType = 3)
    public R<Void> deleteMilestone(@PathVariable Long milestoneId) {
        projectService.deleteMilestone(milestoneId);
        return R.ok("删除成功", null);
    }

    @Data
    public static class MemberDTO {
        private Long userId;
        private String roleType;
    }
}
