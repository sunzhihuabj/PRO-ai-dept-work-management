package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.delivery.entity.Acceptance;
import com.dept.mgmt.delivery.entity.ConstructionLog;
import com.dept.mgmt.delivery.entity.LogisticsRecord;
import com.dept.mgmt.delivery.entity.Shipment;
import com.dept.mgmt.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * W6 交付域接口（T-206）
 * 硬卡点 BR-02/03/04/05 在服务层动作入口内嵌拦截（详见 DeliveryService）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "交付管理", description = "发货/物流/施工日志/分级验收（W6）")
public class DeliveryController {

    private final DeliveryService deliveryService;

    // ===== 发货 =====

    @PostMapping("/shipments")
    @Operation(summary = "创建发货单（草稿）")
    @OperLog(title = "交付管理", businessType = 1)
    public R<Long> createShipment(@RequestBody Map<String, Object> body) {
        return R.ok("发货单已创建", deliveryService.createShipment(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                body.get("workorderId") == null ? null : Long.valueOf(String.valueOf(body.get("workorderId"))),
                (String) body.get("batchNo"),
                body.get("qty") == null ? null : Integer.valueOf(String.valueOf(body.get("qty"))),
                (String) body.get("carrier"),
                (String) body.get("remark")));
    }

    @PostMapping("/shipments/{id}/submit")
    @Operation(summary = "发货单提交发运（BR-02：整机厂首次发货必须持有 PASS 的 FAI 报告）")
    @OperLog(title = "交付管理", businessType = 2)
    public R<Void> submitShipment(@PathVariable Long id) {
        deliveryService.submitShipment(id);
        return R.ok("发货单已提交发运", null);
    }

    @GetMapping("/shipments")
    @Operation(summary = "发货单分页")
    public R<PageResult<Shipment>> shipments(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String status) {
        return R.ok(deliveryService.listShipments(pageNum, pageSize, projectId, status));
    }

    // ===== 物流 =====

    @PostMapping("/shipments/{id}/logistics")
    @Operation(summary = "登记物流事件（DISPATCH/TRANSIT/ARRIVED/INSTALL）")
    public R<Long> addLogistics(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok("物流事件已登记", deliveryService.addLogistics(
                id, (String) body.get("eventType"), (String) body.get("location"), (String) body.get("description")));
    }

    @GetMapping("/logistics")
    @Operation(summary = "物流记录（按发货单）")
    public R<List<LogisticsRecord>> logistics(@RequestParam Long shipmentId) {
        return R.ok(deliveryService.listLogistics(shipmentId));
    }

    // ===== 施工日志 =====

    @PostMapping("/construction-logs")
    @Operation(summary = "录入施工日志（返回项目当前累计工程量）")
    public R<Map<String, Object>> addConstructionLog(@RequestBody Map<String, Object> body) {
        BigDecimal progress = deliveryService.addConstructionLog(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                body.get("logDate") == null ? null : LocalDate.parse(String.valueOf(body.get("logDate"))),
                (String) body.get("weather"),
                body.get("personnelCount") == null ? null : Integer.valueOf(String.valueOf(body.get("personnelCount"))),
                (String) body.get("workContent"),
                body.get("progressPct") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(body.get("progressPct"))),
                (String) body.get("issues"),
                (String) body.get("recorder"));
        return R.ok("施工日志已录入", Map.of("currentProgress", progress));
    }

    @GetMapping("/construction-logs")
    @Operation(summary = "施工日志列表（按项目）")
    public R<List<ConstructionLog>> constructionLogs(@RequestParam Long projectId) {
        return R.ok(deliveryService.listConstructionLogs(projectId));
    }

    @GetMapping("/progress")
    @Operation(summary = "项目当前累计工程量")
    public R<Map<String, Object>> progress(@RequestParam Long projectId) {
        return R.ok(Map.of("projectId", projectId, "progress", deliveryService.currentProgress(projectId)));
    }

    // ===== 分级验收 =====

    @PostMapping("/acceptances")
    @Operation(summary = "手动创建验收单（FIRST/MID/LATE/FINAL，重复创建拦截）")
    public R<Long> createAcceptance(@RequestBody Map<String, Object> body) {
        return R.ok("验收单已创建", deliveryService.createAcceptance(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.get("stage"),
                body.get("progressPct") == null ? null : new BigDecimal(String.valueOf(body.get("progressPct"))),
                "MANUAL",
                (String) body.get("comment")));
    }

    @PostMapping("/acceptances/check")
    @Operation(summary = "工程量验收触发检查（BR-03 30% 中期 / BR-04 85% 后期，AGT-19 同源）")
    public R<Map<String, Object>> checkAcceptance(@RequestParam Long projectId) {
        return R.ok(deliveryService.triggerAcceptanceChecks(projectId, "MANUAL"));
    }

    @PostMapping("/acceptances/{id}/sign")
    @Operation(summary = "验收单签字（OWNER/SUPERVISOR/CLIENT，BR-05 三方齐备生效）")
    public R<Void> signAcceptance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        deliveryService.signAcceptance(id, (String) body.get("party"),
                (String) body.get("acceptor"), (String) body.get("comment"));
        return R.ok("已签字", null);
    }

    @GetMapping("/acceptances")
    @Operation(summary = "验收单列表（按项目/阶段/状态）")
    public R<List<Acceptance>> acceptances(@RequestParam(required = false) Long projectId,
                                           @RequestParam(required = false) String stage,
                                           @RequestParam(required = false) String status) {
        return R.ok(deliveryService.listAcceptances(projectId, stage, status));
    }
}
