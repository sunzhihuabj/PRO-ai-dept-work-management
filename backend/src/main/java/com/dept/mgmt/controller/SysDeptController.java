package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.SysDeptDTO;
import com.dept.mgmt.entity.SysDept;
import com.dept.mgmt.service.SysDeptService;
import com.dept.mgmt.common.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织架构接口（原"部门管理"，T-310 命名统一；数据模型与路由 /v1/depts 保持兼容）
 */
@Slf4j
@RestController
@RequestMapping("/v1/depts")
@RequiredArgsConstructor
@Tag(name = "组织架构", description = "组织架构树 CRUD（层级关系维护，原部门管理）")
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @GetMapping
    @Operation(summary = "组织列表（平铺，前端组树）")
    public R<List<SysDept>> list() {
        return R.ok(sysDeptService.listAll());
    }

    @PostMapping
    @Operation(summary = "新建组织节点")
    @OperLog(title = "组织架构", businessType = 1)
    public R<Long> create(@Valid @RequestBody SysDeptDTO dto) {
        return R.ok("组织节点创建成功", sysDeptService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新组织节点")
    @OperLog(title = "组织架构", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysDeptDTO dto) {
        sysDeptService.update(id, dto);
        return R.ok("组织节点更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除组织节点（存在下级节点/节点下有用户时禁止）")
    @OperLog(title = "组织架构", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        sysDeptService.delete(id);
        return R.ok("组织节点删除成功", null);
    }
}
