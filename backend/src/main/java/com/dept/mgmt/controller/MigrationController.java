package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.mdm.dto.MigrationImportResult;
import com.dept.mgmt.mdm.service.MigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据迁移接口（T-213）：存量客户/项目档案 Excel 批量导入 + 清洗校验报告
 * 物料迁移复用既有 /v1/materials/import（T-103 同一清洗口径）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/migration")
@RequiredArgsConstructor
@Tag(name = "数据迁移", description = "存量客户/项目批量导入+清洗校验报告（T-213）")
public class MigrationController {

    private final MigrationService migrationService;

    @PostMapping("/customers/import")
    @Operation(summary = "存量客户主体导入（全量校验整批拒绝，报告含行级错误）")
    public R<MigrationImportResult> importCustomers(@RequestParam("file") MultipartFile file) {
        return R.ok("导入完成", migrationService.importCustomers(file));
    }

    @PostMapping("/projects/import")
    @Operation(summary = "存量项目档案导入（客户联动建档，全量校验整批拒绝）")
    public R<MigrationImportResult> importProjects(@RequestParam("file") MultipartFile file) {
        return R.ok("导入完成", migrationService.importProjects(file));
    }
}
