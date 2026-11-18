package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.service.AuditChainService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计链联查接口（V2.0 铁律五/D7）：run_id → 过程事件/工具审计/模型调用/出网留痕 四段贯通。
 * 仅本人数据可查（userId 强制当前登录人）；runId 检索要求 run 属于本人（四段中任一带本人 userId）。
 *
 * @author dept-mgmt
 */
@Tag(name = "AI审计链（V2.0 铁律五）")
@RestController
@RequestMapping("/v1/ai/audit-chain")
@RequiredArgsConstructor
public class AuditChainController {

    private final AuditChainService auditChainService;

    @Operation(summary = "审计链联查（runId 精确 或 用户+时间窗）")
    @GetMapping
    public R<Map<String, Object>> chain(@RequestParam(required = false) String runId,
                                        @RequestParam(required = false) Long userId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        LoginUser user = LoginUser.current();
        // 权限收敛：非本人 userId 查询一律强制回本人（普通用户无全部门审计权限）
        Long effectiveUserId = (userId == null && runId == null)
                ? (user == null ? null : user.getUserId()) : userId;
        return R.ok(auditChainService.chain(runId, effectiveUserId, from, to));
    }
}
