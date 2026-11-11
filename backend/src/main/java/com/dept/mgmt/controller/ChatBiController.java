package com.dept.mgmt.controller;

import com.dept.mgmt.chatbi.entity.SysMetric;
import com.dept.mgmt.chatbi.service.ChatBiService;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ChatBI 数据问答接口（T-307 AGT-25：指标白名单 + NL 受限生成 + 禁止编造）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/chatbi")
@RequiredArgsConstructor
@Tag(name = "ChatBI 数据问答", description = "指标白名单内自然语言问答（禁止编造）")
public class ChatBiController {

    private final ChatBiService chatBiService;

    @PostMapping("/ask")
    @Operation(summary = "数据问答（仅回答指标白名单内问题，未匹配拒绝）")
    public R<Map<String, Object>> ask(@RequestBody Map<String, Object> body) {
        LoginUser user = LoginUser.current();
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        return R.ok("问答完成", chatBiService.ask(
                (String) body.get("question"), params, user == null ? null : user.getUserId()));
    }

    @GetMapping("/metrics")
    @Operation(summary = "指标白名单清单（当前可查指标与口径）")
    public R<List<SysMetric>> metrics() {
        return R.ok(chatBiService.listMetrics());
    }
}
