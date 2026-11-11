package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dingtalk.service.DingTalkApprovalCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 钉钉回调接口（T-309 审批回调；白名单路径，服务端 HMAC 验签鉴权）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/dingtalk/callback")
@RequiredArgsConstructor
@Tag(name = "钉钉回调", description = "审批状态回调（验签+任务联动，T-309）")
public class DingTalkCallbackController {

    private final DingTalkApprovalCallbackService callbackService;

    @PostMapping("/approval")
    @Operation(summary = "审批状态回调（agree→任务自动确认 / refuse→任务取消；HMAC 验签）")
    public R<Map<String, Object>> approval(@RequestBody Map<String, String> body) {
        return R.ok("回调处理完成", callbackService.handleApproval(
                body.get("sign"),
                body.get("timestamp"),
                body.get("processInstanceId"),
                body.get("result"),
                body.get("taskNo")));
    }
}
