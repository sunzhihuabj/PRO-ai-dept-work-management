package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.workshop.entity.InventoryRecord;
import com.dept.mgmt.workshop.entity.TplusPushQueue;
import com.dept.mgmt.workshop.entity.TplusReconciliation;
import com.dept.mgmt.workshop.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫码过账接口（T-204）
 * 链路：扫码 → 出入库记录 → T+ 推送队列 → 文件适配器 → 对账（AGT-16）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "扫码过账", description = "出入库记录/T+推送队列/对账（W4，AGT-16）")
public class InventoryController {

    private final InventoryService inventoryService;
    private final com.dept.mgmt.workshop.service.TplusFileAdapterService tplusFileAdapterService;

    @PostMapping("/tplus/import")
    @Operation(summary = "T+ 每日导出文件导入+对账（文件适配器，支持 xlsx/csv，T-211）")
    @OperLog(title = "扫码过账", businessType = 3)
    public R<Map<String, Object>> importTplus(@RequestBody Map<String, Object> body) {
        return R.ok("导入+对账完成", tplusFileAdapterService.importAndReconcile((String) body.get("filePath")));
    }

    @PostMapping("/scan")
    @Operation(summary = "扫码过账（IN 入库须关联 PO 不超收；OUT 出库须关联工单）")
    @OperLog(title = "扫码过账", businessType = 1)
    public R<Long> scan(@RequestBody Map<String, Object> body) {
        return R.ok("过账成功", inventoryService.scanPosting(
                body.get("poId") == null ? null : Long.valueOf(String.valueOf(body.get("poId"))),
                body.get("workorderId") == null ? null : Long.valueOf(String.valueOf(body.get("workorderId"))),
                (String) body.get("materialCode"),
                (String) body.get("materialName"),
                (String) body.get("direction"),
                new BigDecimal(String.valueOf(body.get("qty"))),
                (String) body.get("barcode"),
                (String) body.get("operator"),
                (String) body.get("remark")));
    }

    @GetMapping("/records")
    @Operation(summary = "出入库记录分页")
    public R<PageResult<InventoryRecord>> records(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) Long projectId,
                                                  @RequestParam(required = false) String direction,
                                                  @RequestParam(required = false) String tplusStatus) {
        return R.ok(inventoryService.listRecords(pageNum, pageSize, projectId, direction, tplusStatus));
    }

    @PostMapping("/queue/process")
    @Operation(summary = "处理 T+ 推送队列（PENDING → 文件适配器落盘 → PUSHED）")
    public R<Map<String, Object>> processQueue() {
        int processed = inventoryService.processPushQueue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processed", processed);
        result.put("successRate", inventoryService.tplusSuccessRate());
        return R.ok("队列处理完成", result);
    }

    @GetMapping("/queue")
    @Operation(summary = "推送队列分页")
    public R<PageResult<TplusPushQueue>> queue(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String status) {
        return R.ok(inventoryService.listQueue(pageNum, pageSize, status));
    }

    @PostMapping("/reconcile")
    @Operation(summary = "T+ 对账（本地已推送 ↔ T+ 回执逐单核对，差异清单落库）")
    public R<TplusReconciliation> reconcile() {
        return R.ok("对账完成", inventoryService.reconcile());
    }

    @GetMapping("/reconciliations")
    @Operation(summary = "对账批次列表（最近 50 次）")
    public R<List<TplusReconciliation>> reconciliations() {
        return R.ok(inventoryService.listReconciliations());
    }
}
