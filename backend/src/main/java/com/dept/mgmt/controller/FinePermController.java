package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.fineperm.annotation.RequirePerm;
import com.dept.mgmt.fineperm.dto.FinePermPreviewVO;
import com.dept.mgmt.fineperm.dto.PermAssignDTO;
import com.dept.mgmt.fineperm.dto.PermPointTreeVO;
import com.dept.mgmt.fineperm.dto.TemplateDTO;
import com.dept.mgmt.fineperm.dto.UserPositionDTO;
import com.dept.mgmt.fineperm.entity.SysPermAssignment;
import com.dept.mgmt.fineperm.entity.SysPermChangeLog;
import com.dept.mgmt.fineperm.entity.SysPosition;
import com.dept.mgmt.fineperm.service.FinePermAssignmentService;
import com.dept.mgmt.fineperm.service.FinePermCacheService;
import com.dept.mgmt.fineperm.service.FinePermChangeLogService;
import com.dept.mgmt.fineperm.service.FinePermissionService;
import com.dept.mgmt.fineperm.service.FinePermPositionService;
import com.dept.mgmt.fineperm.service.FinePermTemplateService;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 精细化权限配置接口（T-404）
 * 多维度权限分配 / 权限预览 / 职位管理 / 模板复用 / 变更日志审计 / 权限版本号
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/fine-perms")
@RequiredArgsConstructor
@Tag(name = "精细化权限", description = "部门/职位三维度权限配置、预览、模板、日志（T-404）")
public class FinePermController {

    private final FinePermAssignmentService assignmentService;
    private final FinePermPositionService positionService;
    private final FinePermTemplateService templateService;
    private final FinePermChangeLogService changeLogService;
    private final FinePermissionService finePermissionService;
    private final FinePermCacheService cacheService;

    // ==================== 权限点与分配 ====================

    @GetMapping("/points")
    @Operation(summary = "权限点树（MENU->BUTTON/API）")
    public R<List<PermPointTreeVO>> points() {
        return R.ok(assignmentService.pointTree());
    }

    @GetMapping("/assignments")
    @Operation(summary = "主体当前分配列表（subjectType=DEPT/POSITION）")
    public R<List<SysPermAssignment>> assignments(@RequestParam String subjectType,
                                                  @RequestParam Long subjectId) {
        return R.ok(assignmentService.listAssignments(subjectType, subjectId));
    }

    @PostMapping("/assignments")
    @RequirePerm("api:system/perm-config:assign")
    @Operation(summary = "批量分配（effect=GRANT/DENY，支持批量）")
    public R<Integer> assign(@Valid @RequestBody PermAssignDTO dto) {
        return R.ok(assignmentService.assign(dto));
    }

    @PutMapping("/assignments/remove")
    @RequirePerm("api:system/perm-config:assign")
    @Operation(summary = "批量移除分配")
    public R<Integer> batchRemove(@Valid @RequestBody PermAssignDTO dto) {
        return R.ok(assignmentService.batchRemove(dto.getSubjectType(), dto.getSubjectId(),
                dto.getPermCodes(), dto.getRemark()));
    }

    @PostMapping("/assignments/clear")
    @RequirePerm("api:system/perm-config:assign")
    @Operation(summary = "清空主体全部分配")
    public R<Integer> clear(@RequestBody Map<String, Object> body) {
        String subjectType = String.valueOf(body.get("subjectType"));
        Long subjectId = Long.valueOf(String.valueOf(body.get("subjectId")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(assignmentService.clear(subjectType, subjectId, remark));
    }

    // ==================== 权限预览 ====================

    @GetMapping("/preview")
    @RequirePerm("api:system/perm-config:preview")
    @Operation(summary = "预览指定用户的实际生效权限集合（分维度+冲突+合并）")
    public R<FinePermPreviewVO> preview(@RequestParam Long userId) {
        return R.ok(finePermissionService.preview(userId));
    }

    @GetMapping("/preview/me")
    @Operation(summary = "预览当前用户生效权限（自助，无需管理权限）")
    public R<FinePermPreviewVO> previewMe() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(finePermissionService.preview(loginUser.getUserId()));
    }

    @GetMapping("/version")
    @Operation(summary = "全局权限版本号（前端轮询比对，变更即刷新权限）")
    public R<Long> version() {
        return R.ok(cacheService.currentVersion());
    }

    // ==================== 职位管理 ====================

    @GetMapping("/positions")
    @Operation(summary = "职位列表（含在持人数）")
    public R<List<FinePermPositionService.PositionVO>> positions() {
        return R.ok(positionService.listPositions());
    }

    @PostMapping("/positions")
    @RequirePerm("api:system/perm-config:position")
    @Operation(summary = "创建职位")
    public R<Long> createPosition(@RequestBody SysPosition position) {
        return R.ok(positionService.createPosition(position));
    }

    @PutMapping("/positions/{id}")
    @RequirePerm("api:system/perm-config:position")
    @Operation(summary = "更新职位")
    public R<Void> updatePosition(@PathVariable Long id, @RequestBody SysPosition position) {
        positionService.updatePosition(id, position);
        return R.ok(null);
    }

    @DeleteMapping("/positions/{id}")
    @RequirePerm("api:system/perm-config:position")
    @Operation(summary = "删除职位（需先解除用户关联）")
    public R<Void> deletePosition(@PathVariable Long id) {
        positionService.deletePosition(id);
        return R.ok(null);
    }

    @GetMapping("/users/{userId}/positions")
    @Operation(summary = "用户持有的职位列表（固定/临时+有效期）")
    public R<List<FinePermPositionService.UserPositionVO>> userPositions(@PathVariable Long userId) {
        return R.ok(positionService.listUserPositions(userId));
    }

    @PostMapping("/users/{userId}/positions")
    @RequirePerm("api:system/perm-config:assign")
    @Operation(summary = "为用户分配职位（FIXED无有效期/TEMP必须起止时间）")
    public R<Long> assignUserPosition(@PathVariable Long userId, @Valid @RequestBody UserPositionDTO dto) {
        dto.setUserId(userId);
        return R.ok(positionService.assignUserPosition(dto));
    }

    @DeleteMapping("/user-positions/{id}")
    @RequirePerm("api:system/perm-config:assign")
    @Operation(summary = "移除用户职位关联")
    public R<Void> removeUserPosition(@PathVariable Long id,
                                      @RequestParam(required = false) String remark) {
        positionService.removeUserPosition(id, remark);
        return R.ok(null);
    }

    // ==================== 权限模板 ====================

    @GetMapping("/templates")
    @Operation(summary = "权限模板列表（含条目）")
    public R<List<Map<String, Object>>> templates() {
        return R.ok(templateService.listTemplates());
    }

    @PostMapping("/templates")
    @RequirePerm("api:system/perm-config:template")
    @Operation(summary = "创建模板")
    public R<Long> createTemplate(@Valid @RequestBody TemplateDTO dto) {
        return R.ok(templateService.createTemplate(dto));
    }

    @PutMapping("/templates/{id}")
    @RequirePerm("api:system/perm-config:template")
    @Operation(summary = "更新模板（条目全量替换）")
    public R<Void> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateDTO dto) {
        templateService.updateTemplate(id, dto);
        return R.ok(null);
    }

    @DeleteMapping("/templates/{id}")
    @RequirePerm("api:system/perm-config:template")
    @Operation(summary = "删除模板")
    public R<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return R.ok(null);
    }

    @PostMapping("/templates/{id}/apply")
    @RequirePerm("api:system/perm-config:assign")
    @Operation(summary = "应用模板到主体（一键复用）")
    public R<Integer> applyTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String subjectType = String.valueOf(body.get("subjectType"));
        Long subjectId = Long.valueOf(String.valueOf(body.get("subjectId")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(assignmentService.applyTemplate(subjectType, subjectId, id, remark));
    }

    // ==================== 变更日志 ====================

    @GetMapping("/change-logs")
    @RequirePerm("api:system/perm-config:log")
    @Operation(summary = "权限变更日志分页审计查询")
    public R<PageResult<SysPermChangeLog>> changeLogs(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(changeLogService.page(subjectType, subjectId, operatorName, dimension, operation,
                startTime, endTime, pageNo, pageSize));
    }
}
