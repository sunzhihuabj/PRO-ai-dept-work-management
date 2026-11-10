package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.EventCreateDTO;
import com.dept.mgmt.dto.EventHandleDTO;
import com.dept.mgmt.entity.EventHandle;
import com.dept.mgmt.entity.EventInfo;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.EventService;
import com.dept.mgmt.common.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 事件审批接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
@Tag(name = "事件审批", description = "售后/变更/异常等事件的创建、审批、处理闭环")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "事件列表分页查询")
    public R<PageResult<EventInfo>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer eventType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer urgency,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) String keyword) {
        return R.ok(eventService.list(pageNum, pageSize, projectId, eventType, status, urgency, handlerId, applicantId, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "事件详情")
    public R<EventInfo> detail(@PathVariable Long id) {
        return R.ok(eventService.detail(id));
    }

    @PostMapping
    @Operation(summary = "创建事件")
    @OperLog(title = "事件审批", businessType = 1)
    public R<Long> create(@Valid @RequestBody EventCreateDTO dto) {
        return R.ok("事件创建成功", eventService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新事件（仅申请人，待审批/已驳回状态）")
    @OperLog(title = "事件审批", businessType = 2)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody EventCreateDTO dto) {
        eventService.update(id, dto);
        return R.ok("事件更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除事件（仅申请人，待审批/已驳回状态）")
    @OperLog(title = "事件审批", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return R.ok("事件删除成功", null);
    }

    @PostMapping("/{id}/handle")
    @Operation(summary = "处理事件（受理/驳回/转派/处理/完成/关闭）")
    @OperLog(title = "事件审批", businessType = 2)
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody EventHandleDTO dto) {
        eventService.handle(id, dto);
        return R.ok("处理成功", null);
    }

    @GetMapping("/{id}/handles")
    @Operation(summary = "事件处理记录")
    public R<List<EventHandle>> listHandles(@PathVariable Long id) {
        return R.ok(eventService.listHandles(id));
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我待处理的事件")
    public R<List<EventInfo>> myPending() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(eventService.myPending(loginUser.getUserId()));
    }

    @GetMapping("/my-created")
    @Operation(summary = "我创建的事件")
    public R<List<EventInfo>> myCreated() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(eventService.myCreated(loginUser.getUserId()));
    }
}
