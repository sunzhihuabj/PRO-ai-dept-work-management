package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.tender.entity.TenderBid;
import com.dept.mgmt.tender.entity.TenderCompetitor;
import com.dept.mgmt.tender.entity.TenderNotice;
import com.dept.mgmt.tender.entity.TenderQualificationCheck;
import com.dept.mgmt.tender.service.TenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * W1 投标域接口（T-301）
 * 标讯生命周期 + 资格比对 + 报价建议 + 竞品分析；AGT-01/03 走 Agent 任务，
 * AGT-02/04/05 也可走任务（Ask 级需确认）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/tender")
@RequiredArgsConstructor
@Tag(name = "投标管理", description = "标讯/资格比对/报价/竞品（W1，AGT-01~05）")
public class TenderController {

    private final TenderService tenderService;

    @PostMapping("/notices")
    @Operation(summary = "手工建档标讯（AGT-01 自动解析走 Agent 任务）")
    @OperLog(title = "投标管理", businessType = 1)
    public R<Long> createTender(@RequestBody Map<String, Object> body) {
        return R.ok("标讯已建档", tenderService.createTender(
                (String) body.get("title"),
                (String) body.get("platform"),
                parseMoney(body.get("budget")),
                parseMoney(body.get("deposit")),
                (String) body.get("qualificationReq"),
                parseDate(body.get("deadline")),
                parseDate(body.get("publishDate")),
                "MANUAL",
                (String) body.get("rawText")));
    }

    @GetMapping("/notices")
    @Operation(summary = "标讯分页（状态/关键词）")
    public R<PageResult<TenderNotice>> notices(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(tenderService.listTenders(pageNum, pageSize, status, keyword));
    }

    @GetMapping("/notices/{id}")
    @Operation(summary = "标讯详情（含 AGT-03 分析结果）")
    public R<TenderNotice> detail(@PathVariable Long id) {
        return R.ok(tenderService.detail(id));
    }

    @PostMapping("/notices/{id}/status")
    @Operation(summary = "标讯状态推进（WON 触发 tender.won 事件）")
    @OperLog(title = "投标管理", businessType = 2)
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        tenderService.updateStatus(id, body.get("status"), body.get("remark"));
        return R.ok("状态已更新", null);
    }

    @PostMapping("/notices/{id}/qualification-check")
    @Operation(summary = "资格比对（AGT-02 确定性引擎同步入口）")
    public R<Map<String, Object>> checkQualification(@PathVariable Long id) {
        return R.ok(tenderService.checkQualification(id, "MANUAL"));
    }

    @GetMapping("/notices/{id}/qualification-check")
    @Operation(summary = "资格比对结果（按标讯）")
    public R<List<TenderQualificationCheck>> listChecks(@PathVariable Long id) {
        return R.ok(tenderService.listChecks(id));
    }

    @PostMapping("/notices/{id}/price-suggestion")
    @Operation(summary = "报价建议（AGT-04 确定性同步入口）")
    public R<Map<String, Object>> suggestPrice(@PathVariable Long id) {
        return R.ok(tenderService.suggestPrice(id));
    }

    @PostMapping("/notices/{id}/competitor-analysis")
    @Operation(summary = "竞品分析（AGT-05 确定性同步入口）")
    public R<Map<String, Object>> analyzeCompetitors(@PathVariable Long id) {
        return R.ok(tenderService.analyzeCompetitors(id));
    }

    @PostMapping("/bids")
    @Operation(summary = "登记投标报价（毛利率自动计算）")
    public R<Long> saveBid(@RequestBody Map<String, Object> body) {
        return R.ok("报价已登记", tenderService.saveBid(
                Long.valueOf(String.valueOf(body.get("tenderId"))),
                parseMoney(body.get("price")),
                parseMoney(body.get("costEstimate")),
                (String) body.get("strategyText"),
                parseDate(body.get("openDate")),
                (String) body.get("openResult")));
    }

    @GetMapping("/bids")
    @Operation(summary = "报价记录（按标讯）")
    public R<List<TenderBid>> listBids(@RequestParam(required = false) Long tenderId) {
        return R.ok(tenderService.listBids(tenderId));
    }

    @PostMapping("/competitors")
    @Operation(summary = "录入竞品开标记录")
    public R<Long> addCompetitor(@RequestBody Map<String, Object> body) {
        return R.ok("竞品记录已登记", tenderService.addCompetitor(
                Long.valueOf(String.valueOf(body.get("tenderId"))),
                (String) body.get("competitorName"),
                parseMoney(body.get("price")),
                (String) body.get("note")));
    }

    @GetMapping("/competitors")
    @Operation(summary = "竞品记录（按标讯）")
    public R<List<TenderCompetitor>> listCompetitors(@RequestParam Long tenderId) {
        return R.ok(tenderService.listCompetitors(tenderId));
    }

    private BigDecimal parseMoney(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private LocalDate parseDate(Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null : LocalDate.parse(String.valueOf(value));
    }
}
