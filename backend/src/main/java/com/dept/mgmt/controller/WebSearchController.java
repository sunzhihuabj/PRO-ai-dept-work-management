package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.service.WebSearchService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 联网检索接口（V2.0 M5）：前端与 Hermes REST 侧统一的脱敏检索入口。
 * 检索词经 WebDesensitizer 脱敏后出网，全程 outbound_log 留痕（铁律四/五）。
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/ai/websearch")
@RequiredArgsConstructor
@Tag(name = "AI 联网检索", description = "V2.0 M5：脱敏 proxy 联网搜索")
public class WebSearchController {

    private final WebSearchService webSearchService;

    /** body: { query: 检索词 } */
    @PostMapping
    @Operation(summary = "联网检索（脱敏出网 + outbound_log 留痕）")
    public R<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        Object q = body.get("query");
        if (q == null || String.valueOf(q).isBlank()) {
            throw new BusinessException(400, "query 不能为空");
        }
        return R.ok(webSearchService.search(String.valueOf(q), user.getUserId()));
    }
}
