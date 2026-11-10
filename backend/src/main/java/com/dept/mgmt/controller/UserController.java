package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.SysUserDTO;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.service.SysUserService;
import com.dept.mgmt.common.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户CRUD、重置密码")
public class UserController {

    private final SysUserService sysUserService;

    /**
     * 用户列表分页
     */
    @GetMapping
    @Operation(summary = "用户列表分页查询")
    public R<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Integer status) {
        return R.ok(sysUserService.list(pageNum, pageSize, username, realName, status));
    }

    /**
     * 所有启用用户（下拉选择用）
     */
    @GetMapping("/options")
    @Operation(summary = "获取所有启用用户（下拉选择用）")
    public R<List<SysUser>> options() {
        return R.ok(sysUserService.listAllEnabled());
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public R<SysUser> detail(@PathVariable Long id) {
        return R.ok(sysUserService.detail(id));
    }

    /**
     * 创建用户
     */
    @PostMapping
    @Operation(summary = "创建用户")
    @OperLog(title = "用户管理", businessType = 1)
    public R<Long> create(@Valid @RequestBody SysUserDTO dto) {
        Long id = sysUserService.create(dto);
        return R.ok("用户创建成功", id);
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysUserDTO dto) {
        sysUserService.update(id, dto);
        return R.ok("用户更新成功", null);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @OperLog(title = "用户管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return R.ok("用户删除成功", null);
    }

    /**
     * 重置密码
     */
    @PutMapping("/{id}/reset-password")
    @Operation(summary = "重置用户密码")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return R.ok("密码已重置为123456", null);
    }

    /**
     * 切换用户状态
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "切换用户状态")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> toggleStatus(@PathVariable Long id, @RequestBody StatusDTO dto) {
        SysUserDTO update = new SysUserDTO();
        update.setStatus(dto.getStatus());
        sysUserService.update(id, update);
        return R.ok("状态切换成功", null);
    }

    @Data
    public static class StatusDTO {
        private Integer status;
    }
}
