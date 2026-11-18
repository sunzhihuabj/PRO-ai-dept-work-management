package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.entity.AiUserMemory;
import com.dept.mgmt.aiworkspace.service.AiMemoryService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆管理接口（V2.0 M9）：
 * 本人记忆查看/手工新增/确认/删除（合规要求：删除后 recall 不再返回），TTL 90 天自动过期。
 *
 * @author dept-mgmt
 */
@Tag(name = "AI长期记忆（V2.0 M9）")
@RestController
@RequestMapping("/v1/ai/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final AiMemoryService aiMemoryService;

    private static LoginUser mustLogin() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(401, "未登录");
        }
        return user;
    }

    @Operation(summary = "本人记忆列表（status=PROPOSED/APPROVED 可选过滤 + 待确认条数）")
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(required = false) String status) {
        LoginUser user = mustLogin();
        List<AiUserMemory> memories = aiMemoryService.listMemories(user.getUserId(), status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memories", memories);
        result.put("pendingCount", aiMemoryService.pendingCount(user.getUserId()));
        return R.ok(result);
    }

    @Operation(summary = "手工新增本人记忆（直接生效）")
    @PostMapping
    public R<AiUserMemory> add(@RequestBody Map<String, Object> body) {
        LoginUser user = mustLogin();
        return R.ok(aiMemoryService.addMemory(user.getUserId(),
                body.get("content") == null ? null : String.valueOf(body.get("content"))));
    }

    @Operation(summary = "确认记忆（PROPOSED → APPROVED，召回即刻生效）")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        LoginUser user = mustLogin();
        aiMemoryService.approveMemory(id, user.getUserId());
        return R.ok();
    }

    @Operation(summary = "删除本人记忆（合规：删除后不再召回）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        LoginUser user = mustLogin();
        aiMemoryService.deleteMemory(id, user.getUserId());
        return R.ok();
    }
}
