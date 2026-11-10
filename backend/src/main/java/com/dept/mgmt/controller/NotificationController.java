package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心接口
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "通知中心", description = "模板化通知发送")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "发送通知（模板+接收人）")
    @OperLog(title = "通知中心", businessType = 1)
    public R<NotificationSendResult> send(@Valid @RequestBody NotificationSendDTO dto) {
        return R.ok(notificationService.send(dto));
    }
}
