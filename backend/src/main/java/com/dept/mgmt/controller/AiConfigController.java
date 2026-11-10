package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 通道配置管理接口（代理前端到 ai-gateway admin，服务端持 API-Key，Key 永不出网关）
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/ai-configs")
@RequiredArgsConstructor
@Tag(name = "AI服务配置", description = "AI 通道配置管理与测试连接")
public class AiConfigController {

    private final AiGatewayClient aiGatewayClient;

    @Value("${ai.gateway.base-url:http://localhost:8090}")
    private String gatewayBaseUrl;

    @Value("${ai.gateway.api-key:dept-gateway-internal-key-2026}")
    private String gatewayApiKey;

    private final org.springframework.web.client.RestClient.Builder restBuilder;

    /** 转发到网关 admin（GET/POST/PUT/DELETE + test），服务端注入 X-API-Key */
    @SuppressWarnings("unchecked")
    private Object proxy(String method, String path, Object body) {
        org.springframework.web.client.RestClient client = restBuilder.clone()
                .baseUrl(gatewayBaseUrl)
                .defaultHeader("X-API-Key", gatewayApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        try {
            Object result;
            switch (method) {
                case "GET" -> result = client.get().uri(path).retrieve().body(Object.class);
                // body 为 null 时（如 test 接口）不得调用 .body(null)，RestClient 会抛异常
                case "POST" -> result = (body == null)
                        ? client.post().uri(path).retrieve().body(Object.class)
                        : client.post().uri(path).body(body).retrieve().body(Object.class);
                case "PUT" -> result = client.put().uri(path).body(body).retrieve().body(Object.class);
                case "DELETE" -> result = client.delete().uri(path).retrieve().body(Object.class);
                default -> throw new BusinessException(400, "不支持的转发方法");
            }
            return result;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("AI 配置代理转发失败: {} {} status={}", method, path, status);
            if (status == 404) {
                throw new BusinessException(404, "配置不存在");
            }
            if (status == 409) {
                throw new BusinessException(409, "配置编码已存在");
            }
            throw new BusinessException(502, "AI 网关配置服务不可用，请检查网关是否启动");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 配置代理转发异常: {} {}", method, path, e);
            throw new BusinessException(502, "AI 网关连接失败，请检查网关是否启动");
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "通道配置列表（Key 脱敏回显）")
    public Object list() {
        return R.ok(proxy("GET", "/v1/admin/ai-configs", null));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "创建通道配置")
    @OperLog(title = "AI服务配置", businessType = 1)
    public Object create(@RequestBody Map<String, Object> dto) {
        return R.ok("创建成功", proxy("POST", "/v1/admin/ai-configs", dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "更新配置（api_key 留空不修改）")
    @OperLog(title = "AI服务配置", businessType = 2)
    public Object update(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        return R.ok("更新成功", proxy("PUT", "/v1/admin/ai-configs/" + id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "删除配置")
    @OperLog(title = "AI服务配置", businessType = 3)
    public Object delete(@PathVariable Long id) {
        return R.ok("删除成功", proxy("DELETE", "/v1/admin/ai-configs/" + id, null));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "测试连接（发一条最小请求验证可达性）")
    public Object test(@PathVariable Long id) {
        return R.ok(proxy("POST", "/v1/admin/ai-configs/" + id + "/test", null));
    }
}
