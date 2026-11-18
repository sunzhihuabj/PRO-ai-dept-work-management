package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.service.HermesConfigService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.fineperm.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Hermes Agent 原生配置接口（V2.0 全功能嵌入补齐）：
 * 模型切换 / agent 参数修改 / 配置摘要查看——仅部门领导（双层防护：
 * @PreAuthorize 角色校验 + @RequirePerm 精细权限基线默认拒绝）。
 * 写操作在 HermesConfigService 内自动备份 config.yaml（.bak-时间戳，保留 5 份）。
 *
 * @author dept-mgmt
 */
@Tag(name = "Hermes原生配置（V2.0）")
@RestController
@RequestMapping("/v1/ai/hermes-config")
@RequiredArgsConstructor
public class HermesConfigController {

    private final HermesConfigService hermesConfigService;

    @Operation(summary = "配置摘要（当前模型/参数/providers 清单；api_key 脱敏）")
    @GetMapping
    public R<Map<String, Object>> summary() {
        return R.ok(hermesConfigService.readSummary());
    }

    @Operation(summary = "模型切换（provider + model；写前自动备份）")
    @PutMapping("/model")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @RequirePerm("api:ai/hermes-config:write")
    public R<Map<String, Object>> switchModel(@RequestBody Map<String, Object> body) {
        return R.ok(hermesConfigService.switchModel(
                str(body.get("provider")), str(body.get("model"))));
    }

    @Operation(summary = "agent 参数修改（max_turns / api_max_retries）")
    @PutMapping("/params")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @RequirePerm("api:ai/hermes-config:write")
    public R<Map<String, Object>> updateParams(@RequestBody Map<String, Object> body) {
        return R.ok(hermesConfigService.updateAgentParams(
                intOrNull(body.get("maxTurns")), intOrNull(body.get("apiMaxRetries"))));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intOrNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(o));
    }
}
