package com.dept.mgmt.tender.collect.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import com.dept.mgmt.tender.collect.service.PlatformSessionService;
import com.dept.mgmt.tender.collect.service.TenderPlatformService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 招标商务平台管理接口（AGT-27，R1）
 * 写操作仅部门负责人（ROLE_DEPT_LEADER）；查询开放给登录用户（投标域商务人员需查看平台清单）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/tender-platforms")
@RequiredArgsConstructor
@Tag(name = "采集平台管理", description = "招标商务平台配置：增删改查 + 登录测试")
public class TenderPlatformController {

    private final TenderPlatformService platformService;
    private final PlatformSessionService sessionService;

    @GetMapping
    @Operation(summary = "分页查询平台（凭据脱敏，仅返回 authConfigured 标识）")
    public R<PageResult<TenderPlatform>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) String keyword) {
        Page<TenderPlatform> page = platformService.page(pageNum, pageSize, keyword);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/enabled")
    @Operation(summary = "启用中的平台清单（采集触发下拉用）")
    public R<List<TenderPlatform>> enabled() {
        return R.ok(platformService.listEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "平台详情（凭据脱敏）")
    public R<TenderPlatform> detail(@PathVariable Long id) {
        return R.ok(platformService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "新增平台（authParams 明文仅入参，服务端即时加密落库）")
    public R<Long> create(@RequestBody PlatformDTO dto) {
        return R.ok("已创建", platformService.create(dto.getPlatformName(), dto.getLoginUrl(), dto.getListUrl(),
                dto.getDetailUrlPrefix(), dto.getAuthType(), dto.getAuthParams(), dto.getCollectRules(),
                dto.getRequestIntervalMs(), dto.getJitterMs(), dto.getMaxPages(), dto.getRemark()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "编辑平台（authParams 为 null 表示保留原凭据）")
    public R<Void> update(@PathVariable Long id, @RequestBody PlatformDTO dto) {
        platformService.update(id, dto.getPlatformName(), dto.getLoginUrl(), dto.getListUrl(),
                dto.getDetailUrlPrefix(), dto.getAuthType(), dto.getAuthParams(), dto.getCollectRules(),
                dto.getRequestIntervalMs(), dto.getJitterMs(), dto.getMaxPages(), dto.getRemark());
        return R.ok("已更新", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "删除平台")
    public R<Void> delete(@PathVariable Long id) {
        platformService.delete(id);
        return R.ok("已删除", null);
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "启用/停用")
    public R<Void> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        platformService.toggle(id, enabled);
        return R.ok("已更新", null);
    }

    @PostMapping("/{id}/test-login")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "测试平台登录（FORM 实际登录 / COOKIE 校验有效性 / NONE 探活）")
    public R<Map<String, Object>> testLogin(@PathVariable Long id) {
        return R.ok(sessionService.testLogin(platformService.mustExist(id)));
    }

    /** 平台配置 DTO（authParams：username/password/cookie 明文，仅入参） */
    @Data
    public static class PlatformDTO {
        private String platformName;
        private String loginUrl;
        private String listUrl;
        private String detailUrlPrefix;
        private String authType;
        /** 明文凭据（仅入参；编辑时不传=保留原值） */
        private Map<String, Object> authParams;
        private String collectRules;
        private Integer requestIntervalMs;
        private Integer jitterMs;
        private Integer maxPages;
        private String remark;
    }
}
