package com.dept.gateway.controller;

import com.dept.gateway.entity.AiProviderConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查（放行，无认证）
 *
 * @author dept-mgmt
 */
@RestController
public class HealthController {

    @GetMapping("/v1/gateway/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "ai-gateway");
    }
}
