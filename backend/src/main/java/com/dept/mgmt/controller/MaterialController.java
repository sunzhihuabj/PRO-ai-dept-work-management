package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.excel.ExcelExportWriter;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.mdm.material.dto.MaterialImportResult;
import com.dept.mgmt.mdm.material.dto.MaterialImportRow;
import com.dept.mgmt.mdm.material.entity.Material;
import com.dept.mgmt.mdm.material.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 物料主数据接口（T-101 导入导出示例场景）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/materials")
@RequiredArgsConstructor
@Tag(name = "物料主数据", description = "分页、导入、导出、模板下载")
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    @Operation(summary = "物料分页查询")
    public R<PageResult<Material>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String materialName,
                                        @RequestParam(required = false) String category) {
        return R.ok(materialService.list(pageNum, pageSize, materialName, category));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "批量导入（有错误行则整批拒绝）")
    @OperLog(title = "物料主数据", businessType = 2)
    public R<MaterialImportResult> importExcel(@RequestParam("file") MultipartFile file) {
        return R.ok(materialService.importMaterials(file));
    }

    @GetMapping("/export")
    @Operation(summary = "导出物料清单")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String materialName,
                       @RequestParam(required = false) String category) {
        List<MaterialImportRow> rows = materialService.exportRows(materialName, category);
        String fileName = "物料清单_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        ExcelExportWriter.write(response, fileName, "物料清单", MaterialImportRow.class, rows);
    }

    @GetMapping("/template")
    @Operation(summary = "下载导入模板")
    public void template(HttpServletResponse response) {
        ExcelExportWriter.write(response, "物料导入模板", "物料清单",
                MaterialImportRow.class, materialService.templateRows());
    }
}
