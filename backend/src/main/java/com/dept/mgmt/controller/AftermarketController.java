package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.aftermarket.entity.InspectionRecord;
import com.dept.mgmt.aftermarket.entity.RepairTicket;
import com.dept.mgmt.aftermarket.entity.WarrantyInfo;
import com.dept.mgmt.aftermarket.service.AftermarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * W8 售后域接口（T-304）：报修/巡检/质保/故障月报
 * AGT-23/24 也可走 Agent 任务
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/aftermarket")
@RequiredArgsConstructor
@Tag(name = "售后管理", description = "报修分级/诊断建议/巡检/质保金/故障月报（W8，AGT-23/24）")
public class AftermarketController {

    private final AftermarketService aftermarketService;

    // ===== 报修 =====

    @PostMapping("/repairs")
    @Operation(summary = "报修受理（AGT-23 自动分级：紧急/一般/咨询 + 响应时效）")
    @OperLog(title = "售后管理", businessType = 1)
    public R<Long> createRepair(@RequestBody Map<String, Object> body) {
        return R.ok("工单已受理", aftermarketService.createRepair(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.get("faultDesc"),
                (String) body.get("reporter")));
    }

    @GetMapping("/repairs")
    @Operation(summary = "报修工单分页")
    public R<PageResult<RepairTicket>> repairs(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String level) {
        return R.ok(aftermarketService.listRepairs(pageNum, pageSize, projectId, status, level));
    }

    @PostMapping("/repairs/{id}/start")
    @Operation(summary = "开始处理（指定处理人）")
    public R<Void> start(@PathVariable Long id, @RequestBody Map<String, String> body) {
        aftermarketService.startProcessing(id, body.get("handler"));
        return R.ok("处理中", null);
    }

    @PostMapping("/repairs/{id}/close")
    @Operation(summary = "工单闭环（A-06：根因/方案必填）")
    public R<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body) {
        aftermarketService.closeRepair(id, body.get("rootCause"), body.get("solution"));
        return R.ok("工单已闭环", null);
    }

    @GetMapping("/repairs/{id}/diagnosis")
    @Operation(summary = "远程诊断建议（B-04：历史闭环工单 TOP3，知识库 RAG 就绪后切换）")
    public R<List<Map<String, Object>>> diagnosis(@PathVariable Long id) {
        return R.ok(aftermarketService.diagnosisSuggest(id));
    }

    @GetMapping("/classify")
    @Operation(summary = "分级规则试算（调试/演示用）")
    public R<Map<String, Object>> classify(@RequestParam String faultDesc) {
        String level = aftermarketService.classify(faultDesc);
        return R.ok(Map.of("level", level,
                "sla", switch (level) {
                    case "EMERGENCY" -> "紧急：2 小时内响应";
                    case "INQUIRY" -> "咨询：48 小时内答复";
                    default -> "一般：24 小时内响应";
                }));
    }

    // ===== 巡检 =====

    @PostMapping("/inspections")
    @Operation(summary = "录入巡检记录（半年/年度）")
    public R<Long> addInspection(@RequestBody Map<String, Object> body) {
        return R.ok("巡检已登记", aftermarketService.addInspection(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.getOrDefault("inspectionType", "HALF_YEAR"),
                body.get("inspectionDate") == null ? null : LocalDate.parse(String.valueOf(body.get("inspectionDate"))),
                (String) body.get("inspector"),
                (String) body.get("content"),
                (String) body.get("issues")));
    }

    @GetMapping("/inspections")
    @Operation(summary = "巡检记录（按项目）")
    public R<List<InspectionRecord>> inspections(@RequestParam Long projectId) {
        return R.ok(aftermarketService.listInspections(projectId));
    }

    // ===== 质保 =====

    @PostMapping("/warranties")
    @Operation(summary = "登记/更新质保信息（AGT-24 数据源）")
    public R<Long> saveWarranty(@RequestBody Map<String, Object> body) {
        return R.ok("质保信息已登记", aftermarketService.saveWarranty(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                LocalDate.parse(String.valueOf(body.get("warrantyEnd"))),
                body.get("depositAmount") == null ? null : new BigDecimal(String.valueOf(body.get("depositAmount")))));
    }

    @GetMapping("/warranties")
    @Operation(summary = "质保信息列表")
    public R<List<WarrantyInfo>> warranties() {
        return R.ok(aftermarketService.listWarranties());
    }

    // ===== 故障月报 =====

    @GetMapping("/monthly-report")
    @Operation(summary = "故障统计月报（A-09：报修量/分级分布/闭环率/平均闭环时长）")
    public R<Map<String, Object>> monthlyReport(@RequestParam(required = false) String month) {
        return R.ok(aftermarketService.monthlyReport(month));
    }
}
