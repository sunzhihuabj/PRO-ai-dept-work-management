package com.dept.gateway.controller;

import com.dept.gateway.dto.EmbedRequest;
import com.dept.gateway.dto.EmbedResponse;
import com.dept.gateway.core.EmbedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关向量化接口（backend 知识库 RAG 专用，API-Key 认证）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/gateway")
@RequiredArgsConstructor
public class EmbedController {

    private final EmbedService embedService;

    @PostMapping("/embed")
    public EmbedResponse embed(@Valid @RequestBody EmbedRequest request) {
        return embedService.embed(request);
    }
}
