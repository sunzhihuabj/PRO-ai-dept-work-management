package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.DeliverableChecklist;
import com.dept.mgmt.entity.DeliverableTemplate;
import com.dept.mgmt.service.DeliverableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 交付物管理接口：模板 / 清单生成 / 绑定文件 / 检查确认
 */
@Slf4j
@RestController
@RequestMapping("/v1/deliverables")
@RequiredArgsConstructor
@Tag(name = "交付物管理", description = "模板查询、清单生成、文件绑定、检查确认")
public class DeliverableController {

    private final DeliverableService deliverableService;

    @GetMapping("/templates")
    @Operation(summary = "交付物模板列表")
    public R<List<DeliverableTemplate>> templates(@RequestParam(required = false) Integer projectType) {
        return R.ok(deliverableService.listTemplates(projectType));
    }

    @PostMapping("/projects/{id}/generate")
    @Operation(summary = "按项目类型从模板生成检查清单（幂等）")
    @OperLog(title = "交付物管理", businessType = 1)
    public R<Map<String, Integer>> generate(@PathVariable Long id) {
        int created = deliverableService.generate(id);
        return R.ok("生成完成", Map.of("created", created));
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "项目交付物清单")
    public R<List<DeliverableChecklist>> listByProject(@PathVariable Long id) {
        return R.ok(deliverableService.listByProject(id));
    }

    @GetMapping("/projects/{id}/stats")
    @Operation(summary = "交付物完成度统计")
    public R<Map<String, Object>> stats(@PathVariable Long id) {
        return R.ok(deliverableService.stats(id));
    }

    @PostMapping
    @Operation(summary = "手动添加清单项")
    @OperLog(title = "交付物管理", businessType = 1)
    public R<Long> create(@RequestBody DeliverableChecklist dto) {
        return R.ok("添加成功", deliverableService.create(dto));
    }

    @PutMapping("/{id}/bind-file")
    @Operation(summary = "绑定已上传文件")
    @OperLog(title = "交付物管理", businessType = 2)
    public R<DeliverableChecklist> bindFile(@PathVariable Long id, @RequestParam Long fileId) {
        return R.ok("绑定成功", deliverableService.bindFile(id, fileId));
    }

    @PutMapping("/{id}/check")
    @Operation(summary = "检查确认（须先上传文件）")
    @OperLog(title = "交付物管理", businessType = 2)
    public R<DeliverableChecklist> check(@PathVariable Long id) {
        return R.ok("检查通过", deliverableService.check(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除清单项")
    @OperLog(title = "交付物管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        deliverableService.delete(id);
        return R.ok("删除成功", null);
    }
}
