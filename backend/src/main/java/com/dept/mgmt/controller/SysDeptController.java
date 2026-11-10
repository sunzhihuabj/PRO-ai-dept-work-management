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
 * 部门管理接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/depts")
@RequiredArgsConstructor
@Tag(name = "部门管理", description = "部门树 CRUD")
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @GetMapping
    @Operation(summary = "部门列表（平铺）")
    public R<List<SysDept>> list() {
        return R.ok(sysDeptService.listAll());
    }

    @PostMapping
    @Operation(summary = "新建部门")
    @OperLog(title = "部门管理", businessType = 1)
    public R<Long> create(@Valid @RequestBody SysDeptDTO dto) {
        return R.ok("部门创建成功", sysDeptService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新部门")
    @OperLog(title = "部门管理", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysDeptDTO dto) {
        sysDeptService.update(id, dto);
        return R.ok("部门更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门（存在子部门/部门下有用户时禁止）")
    @OperLog(title = "部门管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        sysDeptService.delete(id);
        return R.ok("部门删除成功", null);
    }
}
