package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.BonusPayDTO;
import com.dept.mgmt.dto.BonusSaveDTO;
import com.dept.mgmt.entity.ProjectBonus;
import com.dept.mgmt.entity.ProjectBonusRecord;
import com.dept.mgmt.service.BonusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 项目奖金管理接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/bonus")
@RequiredArgsConstructor
@Tag(name = "奖金管理", description = "奖金配置、KPI联动、发放记录")
public class BonusController {

    private final BonusService bonusService;

    // ---------- 配置 ----------
    @GetMapping
    @Operation(summary = "奖金配置分页查询")
    public R<PageResult<ProjectBonus>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer bonusType,
            @RequestParam(required = false) Integer status) {
        return R.ok(bonusService.list(pageNum, pageSize, projectId, userId, bonusType, status));
    }

    @PostMapping
    @Operation(summary = "新建奖金配置")
    @OperLog(title = "奖金管理", businessType = 1)
    public R<Long> create(@Valid @RequestBody BonusSaveDTO dto) {
        return R.ok("奖金配置创建成功", bonusService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新奖金配置")
    @OperLog(title = "奖金管理", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BonusSaveDTO dto) {
        bonusService.update(id, dto);
        return R.ok("奖金配置更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除奖金配置（有发放记录时禁止）")
    @OperLog(title = "奖金管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        bonusService.delete(id);
        return R.ok("奖金配置删除成功", null);
    }

    @PutMapping("/{id}/apply-kpi")
    @Operation(summary = "按KPI等级联动调整系数（A=1.2 B=1.0 C=0.8 D=0.6）")
    @OperLog(title = "奖金管理", businessType = 2)
    public R<ProjectBonus> applyKpi(@PathVariable Long id, @RequestParam String period) {
        return R.ok("已按KPI调整", bonusService.applyKpi(id, period));
    }

    // ---------- 发放 ----------
    @PostMapping("/{id}/pay")
    @Operation(summary = "发放奖金（月度/年度）")
    @OperLog(title = "奖金管理", businessType = 2)
    public R<ProjectBonusRecord> pay(@PathVariable Long id, @Valid @RequestBody BonusPayDTO dto) {
        return R.ok("发放成功", bonusService.pay(id, dto));
    }

    @GetMapping("/records")
    @Operation(summary = "发放记录分页查询")
    public R<PageResult<ProjectBonusRecord>> records(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer payType,
            @RequestParam(required = false) String payPeriod) {
        return R.ok(bonusService.records(pageNum, pageSize, projectId, userId, payType, payPeriod));
    }
}
