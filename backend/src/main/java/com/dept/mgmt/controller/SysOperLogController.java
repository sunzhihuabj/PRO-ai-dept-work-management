package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.SysOperLog;
import com.dept.mgmt.service.SysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/oper-logs")
@RequiredArgsConstructor
@Tag(name = "操作日志", description = "系统操作日志查询")
public class SysOperLogController {

    private final SysOperLogService sysOperLogService;

    @GetMapping
    @Operation(summary = "日志分页查询")
    public R<PageResult<SysOperLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) Integer status) {
        return R.ok(sysOperLogService.list(pageNum, pageSize, title, operName, status));
    }
}
