package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.platform.code.dto.CodeNextDTO;
import com.dept.mgmt.platform.code.dto.CodeRuleDTO;
import com.dept.mgmt.platform.code.entity.CodeRule;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.platform.code.service.CodeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 编码规则管理接口
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/code-rules")
@RequiredArgsConstructor
@Tag(name = "编码规则", description = "规则CRUD、取号")
public class CodeRuleController {

    private final CodeRuleService codeRuleService;
    private final CodeEngineService codeEngineService;

    @GetMapping
    @Operation(summary = "规则分页查询")
    public R<PageResult<CodeRule>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String ruleCode,
                                        @RequestParam(required = false) String ruleName) {
        return R.ok(codeRuleService.list(pageNum, pageSize, ruleCode, ruleName));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "创建规则")
    @OperLog(title = "编码规则", businessType = 1)
    public R<Long> create(@Valid @RequestBody CodeRuleDTO dto) {
        return R.ok("创建成功", codeRuleService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "更新规则")
    @OperLog(title = "编码规则", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CodeRuleDTO dto) {
        codeRuleService.update(id, dto);
        return R.ok("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "删除规则")
    @OperLog(title = "编码规则", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        codeRuleService.delete(id);
        return R.ok("删除成功", null);
    }

    @PostMapping("/next")
    @Operation(summary = "取下一个编码（业务建档用）")
    public R<String> next(@Valid @RequestBody CodeNextDTO dto) {
        // 拒绝注入日期/流水号类保留变量，它们只能由引擎计算
        if (dto.getParams() != null && dto.getParams().keySet().stream()
                .anyMatch(k -> k.equals("seq") || k.startsWith("yyyy"))) {
            throw new BusinessException(400, "seq/yyyy 系列为引擎保留变量，不允许外部传入");
        }
        return R.ok(codeEngineService.nextCode(dto.getRuleCode(), dto.getParams()));
    }
}
