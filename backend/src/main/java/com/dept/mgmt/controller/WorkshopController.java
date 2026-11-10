package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.workshop.entity.ArrivalRecord;
import com.dept.mgmt.workshop.entity.DisclosureRecord;
import com.dept.mgmt.workshop.entity.FaiReport;
import com.dept.mgmt.workshop.entity.KittingCheck;
import com.dept.mgmt.workshop.entity.PrototypeAcceptance;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import com.dept.mgmt.workshop.entity.QualityCheck;
import com.dept.mgmt.workshop.entity.Rectification;
import com.dept.mgmt.workshop.entity.WorkOrder;
import com.dept.mgmt.workshop.service.WorkshopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * W4 样机域接口（T-201）
 * 硬卡点 BR-01/08/09 在服务层动作入口内嵌拦截（详见 WorkshopService）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/workshop")
@RequiredArgsConstructor
@Tag(name = "样机管理", description = "工单/齐套/PO/到货/交底/FAI/质检/整改/验收（W4）")
public class WorkshopController {

    private final WorkshopService workshopService;

    // ===== 工单 =====

    @PostMapping("/work-orders")
    @Operation(summary = "创建工单（BR-01：技改项目样机验收未通过禁止批量工单）")
    @OperLog(title = "样机管理", businessType = 1)
    public R<Long> createWorkOrder(@RequestBody Map<String, Object> body) {
        return R.ok("工单已创建", workshopService.createWorkOrder(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.getOrDefault("orderType", "PROTOTYPE"),
                body.get("qty") == null ? null : Integer.valueOf(String.valueOf(body.get("qty"))),
                (String) body.get("remark")));
    }

    @PostMapping("/work-orders/{id}/start")
    @Operation(summary = "工单开工（BR-08 齐套≥95% + BR-09 交底已签字）")
    @OperLog(title = "样机管理", businessType = 2)
    public R<Void> startWorkOrder(@PathVariable Long id) {
        workshopService.startWorkOrder(id);
        return R.ok("工单已开工", null);
    }

    @PostMapping("/work-orders/{id}/close")
    @Operation(summary = "工单完工")
    public R<Void> closeWorkOrder(@PathVariable Long id) {
        workshopService.closeWorkOrder(id);
        return R.ok("工单已完工", null);
    }

    @GetMapping("/work-orders")
    @Operation(summary = "工单分页")
    public R<PageResult<WorkOrder>> workOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String status) {
        return R.ok(workshopService.listWorkOrders(pageNum, pageSize, projectId, status));
    }

    // ===== 齐套 =====

    @PostMapping("/kitting")
    @Operation(summary = "录入齐套审查（阈值 95%；AGT-13 自动化前人工入口）")
    public R<Long> runKitting(@RequestBody Map<String, Object> body) {
        return R.ok("审查已记录", workshopService.runKitting(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                new BigDecimal(String.valueOf(body.get("kitRate"))),
                (String) body.get("detailJson"),
                (String) body.getOrDefault("checker", "MANUAL")));
    }

    @GetMapping("/kitting")
    @Operation(summary = "齐套审查记录（按项目）")
    public R<List<KittingCheck>> kitting(@RequestParam Long projectId) {
        return R.ok(workshopService.listKitting(projectId));
    }

    // ===== PO / 到货 =====

    @PostMapping("/pos")
    @Operation(summary = "创建采购订单（PO 编号引擎）")
    @OperLog(title = "样机管理", businessType = 1)
    public R<Long> createPo(@RequestBody Map<String, Object> body) {
        return R.ok("PO 已创建", workshopService.createPo(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.get("materialCode"),
                (String) body.get("materialName"),
                new BigDecimal(String.valueOf(body.get("qty"))),
                (String) body.get("supplier")));
    }

    @GetMapping("/pos")
    @Operation(summary = "PO 分页")
    public R<PageResult<PurchaseOrder>> pos(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Long projectId) {
        return R.ok(workshopService.listPos(pageNum, pageSize, projectId));
    }

    @PostMapping("/pos/{id}/arrivals")
    @Operation(summary = "到货登记（发布 material.arrived 事件，齐量自动完结 PO）")
    @OperLog(title = "样机管理", businessType = 1)
    public R<Long> registerArrival(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok("到货已登记", workshopService.registerArrival(
                id, new BigDecimal(String.valueOf(body.get("qty"))), (String) body.get("remark")));
    }

    @GetMapping("/arrivals")
    @Operation(summary = "到货记录（按 PO）")
    public R<List<ArrivalRecord>> arrivals(@RequestParam(required = false) Long poId) {
        return R.ok(workshopService.listArrivals(poId));
    }

    // ===== 交底 =====

    @PostMapping("/disclosures")
    @Operation(summary = "创建技术交底记录（默认未签字）")
    public R<Long> createDisclosure(@RequestBody DisclosureRecord record) {
        return R.ok("交底记录已创建", workshopService.createDisclosure(
                record.getProjectId(), record.getWorkorderId(),
                record.getDiscloser(), record.getReceivers(), record.getContent()));
    }

    @PostMapping("/disclosures/{id}/sign")
    @Operation(summary = "交底签字确认（BR-09 放行条件）")
    public R<Void> signDisclosure(@PathVariable Long id) {
        workshopService.signDisclosure(id);
        return R.ok("已签字确认", null);
    }

    // ===== FAI / 质检 / 整改 / 验收 =====

    @PostMapping("/fai")
    @Operation(summary = "登记 FAI 首件检验（FAIL 自动派生整改单）")
    public R<Long> createFai(@RequestBody Map<String, Object> body) {
        return R.ok("FAI 已登记", workshopService.createFai(
                Long.valueOf(String.valueOf(body.get("workorderId"))),
                (String) body.get("result"),
                (String) body.get("inspector"),
                (String) body.get("content")));
    }

    @GetMapping("/fai")
    @Operation(summary = "FAI 列表（按项目）")
    public R<List<FaiReport>> fai(@RequestParam Long projectId) {
        return R.ok(workshopService.listFai(projectId));
    }

    @PostMapping("/quality-checks")
    @Operation(summary = "工序质检（FAIL 必填缺陷并自动派生整改单）")
    public R<Long> createQualityCheck(@RequestBody Map<String, Object> body) {
        return R.ok("质检已登记", workshopService.createQualityCheck(
                Long.valueOf(String.valueOf(body.get("workorderId"))),
                (String) body.get("stage"),
                (String) body.get("result"),
                (String) body.get("checker"),
                (String) body.get("remark")));
    }

    @GetMapping("/quality-checks")
    @Operation(summary = "质检记录（按工单）")
    public R<List<QualityCheck>> qualityChecks(@RequestParam Long workorderId) {
        return R.ok(workshopService.listQuality(workorderId));
    }

    @PostMapping("/rectifications")
    @Operation(summary = "手工创建整改单")
    public R<Long> createRectification(@RequestBody Map<String, Object> body) {
        return R.ok("整改单已创建", workshopService.createRectification(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.getOrDefault("source", "QC"),
                Long.valueOf(String.valueOf(body.get("sourceId"))),
                (String) body.get("issue"),
                (String) body.get("owner"),
                body.get("dueDate") == null ? null : LocalDate.parse(String.valueOf(body.get("dueDate")))));
    }

    @GetMapping("/rectifications")
    @Operation(summary = "整改单分页")
    public R<PageResult<Rectification>> rectifications(@RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                                       @RequestParam(required = false) Long projectId,
                                                       @RequestParam(required = false) String status) {
        return R.ok(workshopService.listRectifications(pageNum, pageSize, projectId, status));
    }

    @PostMapping("/rectifications/{id}/close")
    @Operation(summary = "关闭整改单")
    public R<Void> closeRectification(@PathVariable Long id, @RequestBody Map<String, String> body) {
        workshopService.closeRectification(id, body.get("closeNote"));
        return R.ok("整改单已关闭", null);
    }

    @PostMapping("/acceptances")
    @Operation(summary = "样机验收登记（BR-01 数据源；FAIL 派生整改单）")
    @OperLog(title = "样机管理", businessType = 2)
    public R<Long> completeAcceptance(@RequestBody Map<String, Object> body) {
        return R.ok("验收已登记", workshopService.completeAcceptance(
                Long.valueOf(String.valueOf(body.get("workorderId"))),
                (String) body.get("result"),
                (String) body.get("acceptor"),
                (String) body.get("comment")));
    }

    @GetMapping("/acceptances")
    @Operation(summary = "样机验收列表（按项目）")
    public R<List<PrototypeAcceptance>> acceptances(@RequestParam Long projectId) {
        return R.ok(workshopService.listAcceptances(projectId));
    }
}
