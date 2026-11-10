package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.SysNotice;
import com.dept.mgmt.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统通知公告接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/notices")
@RequiredArgsConstructor
@Tag(name = "通知公告", description = "公告管理、发布（发布时写全员站内消息）")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    @Operation(summary = "公告管理列表（管理员）")
    public R<List<SysNotice>> list(@RequestParam(required = false) Integer noticeType,
                                   @RequestParam(required = false) Integer status) {
        return R.ok(noticeService.list(noticeType, status));
    }

    @GetMapping("/published")
    @Operation(summary = "已发布公告（全员）")
    public R<List<SysNotice>> published() {
        return R.ok(noticeService.listPublished());
    }

    @PostMapping
    @Operation(summary = "新建公告")
    @OperLog(title = "通知公告", businessType = 1)
    public R<Long> create(@RequestBody SysNotice dto) {
        return R.ok("创建成功", noticeService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新公告")
    @OperLog(title = "通知公告", businessType = 2)
    public R<Void> update(@PathVariable Long id, @RequestBody SysNotice dto) {
        noticeService.update(id, dto);
        return R.ok("更新成功", null);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布公告（写全员站内消息）")
    @OperLog(title = "通知公告", businessType = 2)
    public R<Map<String, Integer>> publish(@PathVariable Long id) {
        return R.ok("发布成功", Map.of("reached", noticeService.publish(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告")
    @OperLog(title = "通知公告", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return R.ok("删除成功", null);
    }
}
