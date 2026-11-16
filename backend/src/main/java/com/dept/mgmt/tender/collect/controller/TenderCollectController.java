package com.dept.mgmt.tender.collect.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.tender.collect.entity.TenderCollectRun;
import com.dept.mgmt.tender.collect.service.TenderCollectService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 标讯联网采集接口（AGT-27，R3 触发 + R6 运行监控查询）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/tender-collect")
@RequiredArgsConstructor
@Tag(name = "标讯联网采集", description = "采集触发与运行监控（AGT-27）")
public class TenderCollectController {

    private final TenderCollectService collectService;

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('ROLE_PROJECT_LEADER')")
    @Operation(summary = "触发一次联网采集（同步执行；resume=true 从断点续采）")
    public R<TenderCollectRun> run(@RequestBody RunDTO dto) {
        if (dto.getPlatformId() == null) {
            return R.fail(400, "platformId 不能为空");
        }
        LoginUser user = LoginUser.current();
        TenderCollectRun run = collectService.runCollect(dto.getPlatformId(),
                user == null ? null : user.getUserId(), "MANUAL", dto.getResume() != null && dto.getResume());
        return R.ok("采集完成", run);
    }

    @GetMapping("/runs")
    @Operation(summary = "运行监控分页（平台/状态筛选）")
    public R<PageResult<TenderCollectRun>> runs(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) Long platformId,
                                                @RequestParam(required = false) String status) {
        Page<TenderCollectRun> page = collectService.pageRuns(pageNum, pageSize, platformId, status);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "运行详情（含异常现场快照与断点检查点）")
    public R<TenderCollectRun> runDetail(@PathVariable Long id) {
        return R.ok(collectService.getRun(id));
    }

    @Data
    public static class RunDTO {
        private Long platformId;
        private Boolean resume;
    }
}
