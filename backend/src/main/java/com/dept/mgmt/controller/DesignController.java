package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.design.entity.DesignBomCheckLog;
import com.dept.mgmt.design.entity.DesignBomItem;
import com.dept.mgmt.design.entity.DesignScheme;
import com.dept.mgmt.design.entity.DesignSurvey;
import com.dept.mgmt.design.entity.DesignTemplate;
import com.dept.mgmt.design.service.BomCheckRules;
import com.dept.mgmt.design.service.DesignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * W3 设计域接口（T-115/T-116/T-117）
 * 受控发布为人工操作（外发语义，AI 不代行）；BOM 校验可人工触发或经 AGT-11 任务触发
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/design")
@RequiredArgsConstructor
@Tag(name = "设计管理", description = "模板库/勘测记录/方案版本/BOM 校验/受控发布（W3）")
public class DesignController {

    private final DesignService designService;

    // ===== 模板库 =====

    @GetMapping("/templates")
    @Operation(summary = "模板列表（启用）")
    public R<List<DesignTemplate>> templates(@RequestParam(required = false) String scene) {
        return R.ok(designService.listTemplates(scene));
    }

    @PostMapping("/templates")
    @Operation(summary = "新增模板")
    @OperLog(title = "设计管理", businessType = 1)
    public R<Long> createTemplate(@RequestBody DesignTemplate template) {
        return R.ok("模板已创建", designService.createTemplate(template));
    }

    // ===== 勘测记录 =====

    @GetMapping("/surveys")
    @Operation(summary = "勘测记录列表（按项目）")
    public R<List<DesignSurvey>> surveys(@RequestParam Long projectId) {
        return R.ok(designService.listSurveys(projectId));
    }

    @PostMapping("/surveys")
    @Operation(summary = "新增勘测记录")
    @OperLog(title = "设计管理", businessType = 1)
    public R<Long> createSurvey(@RequestBody DesignSurvey survey) {
        return R.ok("勘测记录已创建", designService.createSurvey(survey));
    }

    // ===== 方案版本 =====

    @GetMapping("/schemes")
    @Operation(summary = "方案分页")
    public R<PageResult<DesignScheme>> schemes(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String status) {
        return R.ok(designService.listSchemes(pageNum, pageSize, projectId, status));
    }

    @GetMapping("/schemes/{id}")
    @Operation(summary = "方案详情（含 BOM 与校验记录）")
    public R<Map<String, Object>> schemeDetail(@PathVariable Long id) {
        return R.ok(designService.schemeDetail(id));
    }

    @PostMapping("/schemes")
    @Operation(summary = "新建方案版本（人工）")
    @OperLog(title = "设计管理", businessType = 1)
    public R<Long> createScheme(@RequestBody DesignScheme dto) {
        return R.ok("方案草稿已创建", designService.createScheme(
                dto.getProjectId(), dto.getContractId(), dto.getTitle(), dto.getContent(), "MANUAL"));
    }

    @PostMapping("/schemes/{id}/publish")
    @Operation(summary = "受控发布（DRAFT→RELEASED，发布 design.released 事件）")
    @OperLog(title = "设计管理", businessType = 2)
    public R<Void> publish(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        designService.publish(id, body == null ? null : body.get("releaseNote"));
        return R.ok("方案已受控发布", null);
    }

    // ===== BOM =====

    @PostMapping("/schemes/{id}/bom")
    @Operation(summary = "覆盖式录入 BOM 明细（仅草稿）")
    @OperLog(title = "设计管理", businessType = 1)
    public R<Void> replaceBom(@PathVariable Long id, @RequestBody List<DesignBomItem> items) {
        designService.replaceBom(id, items);
        return R.ok("BOM 已保存", null);
    }

    @PostMapping("/schemes/{id}/check")
    @Operation(summary = "执行 BOM 校验（13 条规则，人工入口；AGT-11 走任务）")
    public R<BomCheckRules.CheckResult> checkBom(@PathVariable Long id) {
        return R.ok(designService.checkBom(id, "MANUAL"));
    }

    @GetMapping("/schemes/{id}/check-logs")
    @Operation(summary = "校验记录列表")
    public R<List<DesignBomCheckLog>> checkLogs(@PathVariable Long id) {
        return R.ok(designService.listCheckLogs(id));
    }
}
