package com.dept.gateway.controller;

import com.dept.gateway.core.AdminConfigService;
import com.dept.gateway.dto.ProviderConfigDTO;
import com.dept.gateway.entity.AiProviderConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通道配置管理接口（backend 代理前端调用，API-Key 认证）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/admin/ai-configs")
@RequiredArgsConstructor
public class AdminController {

    private final AdminConfigService adminConfigService;

    @GetMapping
    public List<AiProviderConfig> list() {
        return adminConfigService.list();
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody ProviderConfigDTO dto) {
        return Map.of("id", adminConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody ProviderConfigDTO dto) {
        adminConfigService.update(id, dto);
        return Map.of("updated", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        adminConfigService.delete(id);
        return Map.of("deleted", true);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        return adminConfigService.test(id);
    }
}
