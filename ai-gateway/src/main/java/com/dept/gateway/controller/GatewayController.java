package com.dept.gateway.controller;

import com.dept.gateway.core.GatewayService;
import com.dept.gateway.dto.ChatRequest;
import com.dept.gateway.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关对话接口（backend 专用，API-Key 认证）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/gateway")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayService gatewayService;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return gatewayService.chat(request);
    }
}
