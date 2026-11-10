package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.KpiIndicatorDTO;
import com.dept.mgmt.dto.KpiScoreAdjustDTO;
import com.dept.mgmt.entity.KpiIndicator;
import com.dept.mgmt.entity.KpiScore;
import com.dept.mgmt.entity.KpiWeight;
import com.dept.mgmt.service.KpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 绩效管理接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/kpi")
@RequiredArgsConstructor
@Tag(name = "绩效管理", description = "指标维护、评分计算、调整、发布、报表")
public class KpiController {

    private final KpiService kpiService;

    // ---------- 指标 ----------
    @GetMapping("/indicators")
    @Operation(summary = "KPI指标列表")
    public R<List<KpiIndicator>> listIndicators(
            @RequestParam(required = false) Integer projectType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return R.ok(kpiService.listIndicators(projectType, status, keyword));
    }

    @PostMapping("/indicators")
    @Operation(summary = "新建KPI指标")
    public R<Long> createIndicator(@Valid @RequestBody KpiIndicatorDTO dto) {
        return R.ok("指标创建成功", kpiService.createIndicator(dto));
    }

    @PutMapping("/indicators/{id}")
    @Operation(summary = "更新KPI指标")
    public R<Void> updateIndicator(@PathVariable Long id, @Valid @RequestBody KpiIndicatorDTO dto) {
        kpiService.updateIndicator(id, dto);
        return R.ok("指标更新成功", null);
    }

    @DeleteMapping("/indicators/{id}")
    @Operation(summary = "删除KPI指标")
    public R<Void> deleteIndicator(@PathVariable Long id) {
        kpiService.deleteIndicator(id);
        return R.ok("指标删除成功", null);
    }

    // ---------- 权重 ----------
    @GetMapping("/weights")
    @Operation(summary = "权重配置查询")
    public R<List<KpiWeight>> listWeights(@RequestParam(required = false) Integer projectType,
                                          @RequestParam(required = false) String period) {
        return R.ok(kpiService.listWeights(projectType, period));
    }

    @PostMapping("/weights")
    @Operation(summary = "保存权重配置（权重和必须=100）")
    public R<Void> saveWeights(@Valid @RequestBody WeightSaveRequest req) {
        kpiService.saveWeights(req.getProjectType(), req.getPeriod(), req.getWeights());
        return R.ok("权重保存成功", null);
    }

    // ---------- 评分 ----------
    @GetMapping("/scores")
    @Operation(summary = "绩效评分查询")
    public R<List<KpiScore>> listScores(@RequestParam(required = false) String period,
                                        @RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) Long indicatorId,
                                        @RequestParam(required = false) Integer status) {
        return R.ok(kpiService.listScores(period, userId, indicatorId, status));
    }

    @PostMapping("/scores/calculate")
    @Operation(summary = "计算某期绩效（生成基础评分草稿）")
    public R<Map<String, Integer>> calculate(@RequestParam String period,
                                             @RequestParam(required = false) Integer projectType) {
        int created = kpiService.calculate(period, projectType);
        return R.ok("计算完成", Map.of("created", created));
    }

    @PutMapping("/scores/{id}/adjust")
    @Operation(summary = "调整评分（±10分，需填写原因）")
    public R<KpiScore> adjustScore(@PathVariable Long id, @Valid @RequestBody KpiScoreAdjustDTO dto) {
        return R.ok("调整成功", kpiService.adjustScore(id, dto));
    }

    @PutMapping("/scores/confirm")
    @Operation(summary = "发布某期绩效（锁定数据，状态=已确认）")
    public R<Map<String, Integer>> confirm(@RequestParam String period) {
        int count = kpiService.confirmPeriod(period);
        return R.ok("发布完成", Map.of("count", count));
    }

    // ---------- 报表 ----------
    @GetMapping("/report")
    @Operation(summary = "绩效报表（按用户汇总加权总分）")
    public R<Map<String, Object>> report(@RequestParam String period,
                                         @RequestParam(required = false) Integer projectType) {
        return R.ok(kpiService.report(period, projectType));
    }

    @GetMapping("/report/export")
    @Operation(summary = "绩效报表导出（CSV）")
    public ResponseEntity<String> exportReport(@RequestParam String period,
                                               @RequestParam(required = false) Integer projectType) {
        String csv = kpiService.exportReportCsv(period, projectType);
        String fileName = "kpi_report_" + period.replaceAll("[^0-9A-Za-z-]", "_") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }

    @Data
    public static class WeightSaveRequest {
        private Integer projectType;
        private String period;
        /** indicatorId -> weight(%) */
        private Map<Long, BigDecimal> weights;
    }
}
