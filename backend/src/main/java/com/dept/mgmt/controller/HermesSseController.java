package com.dept.mgmt.controller;

import com.dept.mgmt.aiworkspace.service.HermesSseService;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hermes 事件 SSE 下行端点（V2.0 M4）。
 *
 * <p>EventSource 无法携带 Authorization 头，支持 {@code ?token=} query 参数鉴权
 * （JwtAuthenticationFilter 对 SSE 路径的 query token 兜底解析）；
 * 也可直接走常规 Authorization 头（fetch-stream 场景）。
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/ai/ws/hermes")
@RequiredArgsConstructor
@Tag(name = "Hermes SSE", description = "V2.0 M4：run 事件实时下行（风险 Toast）")
public class HermesSseController {

    private final HermesSseService hermesSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅 Hermes 事件流（SSE）")
    public SseEmitter stream() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(401, "未登录");
        }
        return hermesSseService.connect(user.getUserId());
    }
}
