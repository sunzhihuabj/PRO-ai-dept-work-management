package com.dept.gateway.controller;

import com.dept.gateway.core.AdminConfigService;
import com.dept.gateway.core.ModelProbeService;
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
    private final ModelProbeService modelProbeService;

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

    /** 未保存配置的模型列表探测（表单填写中调用；apiKey 一次性提交不存储） */
    @PostMapping("/probe-models")
    public Map<String, Object> probeModels(@RequestBody Map<String, String> body) {
        return modelProbeService.probe(body.get("providerType"), body.get("endpoint"), body.get("apiKey"));
    }

    /** 已保存通道的模型列表探测（服务端解密 Key） */
    @GetMapping("/{id}/models")
    public Map<String, Object> listChannelModels(@PathVariable Long id) {
        return adminConfigService.probeModels(id);
    }
}
