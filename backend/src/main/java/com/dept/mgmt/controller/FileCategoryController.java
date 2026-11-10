package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.FileCategoryDTO;
import com.dept.mgmt.entity.FileCategory;
import com.dept.mgmt.service.FileCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分类管理
 */
@Slf4j
@RestController
@RequestMapping("/v1/file-categories")
@RequiredArgsConstructor
@Tag(name = "文件分类", description = "分类树管理")
public class FileCategoryController {

    private final FileCategoryService fileCategoryService;

    @GetMapping
    @Operation(summary = "分类列表")
    public R<List<FileCategory>> list() {
        return R.ok(fileCategoryService.listAll());
    }

    @PostMapping
    @Operation(summary = "新建分类")
    public R<Long> create(@Valid @RequestBody FileCategoryDTO dto) {
        return R.ok("分类创建成功", fileCategoryService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分类")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody FileCategoryDTO dto) {
        fileCategoryService.update(id, dto);
        return R.ok("分类更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类（存在子分类时不允许删除）")
    public R<Void> delete(@PathVariable Long id) {
        fileCategoryService.delete(id);
        return R.ok("分类删除成功", null);
    }
}
