package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.UserMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户消息接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/messages")
@RequiredArgsConstructor
@Tag(name = "消息中心", description = "站内消息查询、已读标记")
public class UserMessageController {

    private final UserMessageService userMessageService;

    private Long requireUserId() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(401, "未登录");
        }
        return loginUser.getUserId();
    }

    @GetMapping
    @Operation(summary = "我的消息分页")
    public R<PageResult<UserMessage>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer isRead) {
        return R.ok(userMessageService.myMessages(pageNum, pageSize, requireUserId(), isRead));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读消息数量")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", userMessageService.unreadCount(requireUserId())));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public R<Void> markRead(@PathVariable Long id) {
        userMessageService.markRead(id, requireUserId());
        return R.ok("已标记已读", null);
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public R<Map<String, Integer>> markAllRead() {
        return R.ok("已全部标记已读", Map.of("count", userMessageService.markAllRead(requireUserId())));
    }
}
