package com.dept.mgmt.controller;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.platform.identity.dto.EffectivePermissionVO;
import com.dept.mgmt.platform.identity.dto.PermissionCheckResult;
import com.dept.mgmt.platform.identity.entity.SysCapability;
import com.dept.mgmt.platform.identity.service.EffectivePermissionService;
import com.dept.mgmt.mapper.SysCapabilityMapper;
import com.dept.mgmt.security.LoginUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 三身份权限接口
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "三身份权限", description = "有效权限查询、能力点列表、判定校验")
public class PermissionController {

    private final EffectivePermissionService effectivePermissionService;
    private final SysCapabilityMapper sysCapabilityMapper;

    @GetMapping("/effective")
    @Operation(summary = "当前用户有效权限（身份+能力点列表）")
    public R<EffectivePermissionVO> effective() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(effectivePermissionService.listEffective(loginUser.getUserId()));
    }

    @GetMapping("/capabilities")
    @Operation(summary = "能力点列表（只读，管理界面属 T-109）")
    public R<List<SysCapability>> capabilities() {
        return R.ok(sysCapabilityMapper.selectList(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getStatus, 1)
                .orderByAsc(SysCapability::getCapabilityCode)));
    }

    @GetMapping("/check")
    @Operation(summary = "判定当前用户是否具备某能力（调试/前端守卫用）")
    public R<PermissionCheckResult> check(@RequestParam String capabilityCode) {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(effectivePermissionService.checkForUser(loginUser.getUserId(), capabilityCode));
    }
}
