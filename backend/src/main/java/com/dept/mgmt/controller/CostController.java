package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.cost.entity.CostLedger;
import com.dept.mgmt.cost.entity.PriceModel;
import com.dept.mgmt.cost.service.CostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * W7 成本域接口（T-303）：成本归集/完工核算/毛利三口径/报价模型
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/cost")
@RequiredArgsConstructor
@Tag(name = "成本管理", description = "成本台账/毛利三口径/报价模型（W7，AGT-21/22）")
public class CostController {

    private final CostService costService;
    private final com.dept.mgmt.mapper.MaterialMapper materialMapper;

    @PostMapping("/standard-price")
    @Operation(summary = "设置物料标准成本价（AGT-21 归集计算基础）")
    public R<Void> setStandardPrice(@RequestBody Map<String, Object> body) {
        String materialCode = (String) body.get("materialCode");
        BigDecimal price = new BigDecimal(String.valueOf(body.get("price")));
        com.dept.mgmt.mdm.material.entity.Material material = materialMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dept.mgmt.mdm.material.entity.Material>()
                        .eq(com.dept.mgmt.mdm.material.entity.Material::getMaterialCode, materialCode).last("LIMIT 1"));
        if (material == null) {
            return R.fail(404, "物料不存在: " + materialCode);
        }
        material.setStandardPrice(price);
        materialMapper.updateById(material);
        return R.ok("标准价已设置", null);
    }

    @PostMapping("/collect")
    @Operation(summary = "物料成本自动归集（AGT-21 同步入口，幂等）")
    @OperLog(title = "成本管理", businessType = 3)
    public R<Map<String, Object>> collect(@RequestParam Long projectId) {
        return R.ok(costService.collectMaterialCost(projectId));
    }

    @PostMapping("/ledger")
    @Operation(summary = "手工录入人工/费用成本")
    public R<Long> addCost(@RequestBody Map<String, Object> body) {
        return R.ok("成本已录入", costService.addManualCost(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.get("costType"),
                new BigDecimal(String.valueOf(body.get("amount"))),
                (String) body.get("remark")));
    }

    @GetMapping("/ledger")
    @Operation(summary = "成本台账（按项目）")
    public R<List<CostLedger>> ledger(@RequestParam Long projectId) {
        return R.ok(costService.listLedger(projectId));
    }

    @PostMapping("/settle")
    @Operation(summary = "完工成本核算 + 毛利三口径（AGT-22 同步入口）")
    @OperLog(title = "成本管理", businessType = 1)
    public R<Map<String, Object>> settle(@RequestParam Long projectId) {
        return R.ok(costService.settleProject(projectId));
    }

    @PostMapping("/settlements/{id}/confirm")
    @Operation(summary = "核算单定稿（参与报价模型）")
    public R<Void> confirm(@PathVariable Long id) {
        costService.confirmSettlement(id);
        return R.ok("核算单已定稿", null);
    }

    @PostMapping("/price-model/refresh")
    @Operation(summary = "报价模型更新（F-08，按项目类型聚合定稿毛利）")
    public R<List<PriceModel>> refreshPriceModel() {
        return R.ok(costService.refreshPriceModel());
    }

    @GetMapping("/price-model")
    @Operation(summary = "报价模型列表")
    public R<List<PriceModel>> priceModels() {
        return R.ok(costService.listPriceModels());
    }
}
