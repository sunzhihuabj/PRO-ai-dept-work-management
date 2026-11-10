package com.dept.mgmt.controller;

import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.agent.service.MasterAgentService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent 任务接口（T-107 MasterAgent + T-109 工作台后端）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/agent/tasks")
@RequiredArgsConstructor
@Tag(name = "Agent任务", description = "指令提交、任务状态机、确认/取消")
public class AgentTaskController {

    private final MasterAgentService masterAgentService;

    @PostMapping
    @Operation(summary = "提交指令（自动意图识别并路由能力点）")
    public R<AgentTask> submit(@RequestBody Map<String, String> body) {
        String instruction = body.get("instruction");
        String capabilityCode = body.get("capabilityCode");
        return R.ok("任务已受理", masterAgentService.submit(instruction, capabilityCode));
    }

    @GetMapping
    @Operation(summary = "我的任务列表（最近50条）")
    public R<List<AgentTask>> myTasks() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(masterAgentService.myTasks(user.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务详情（含状态流转时间线）")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(masterAgentService.detail(id, user.getUserId()));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认执行（待确认任务重新入队）")
    public R<Void> confirm(@PathVariable Long id) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        masterAgentService.confirm(id, user.getUserId());
        return R.ok("已确认执行", null);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消任务")
    public R<Void> cancel(@PathVariable Long id) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        masterAgentService.cancel(id, user.getUserId());
        return R.ok("已取消", null);
    }

    @PostMapping("/{id}/feedback")
    @Operation(summary = "结果三态反馈（采纳/修改/驳回，驱动渐进自主升降级）")
    public R<Void> feedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        String feedback = body.get("feedback");
        masterAgentService.feedback(id, user.getUserId(), feedback);
        return R.ok("反馈已记录", null);
    }

    @GetMapping("/autonomy/policies")
    @Operation(summary = "能力自主策略列表（四级状态机+三态统计）")
    public R<Object> autonomyPolicies() {
        return R.ok(masterAgentService.autonomyPolicies());
    }

    @PostMapping("/autonomy/{capabilityCode}/level")
    @Operation(summary = "手动调整自主级别（仅部门领导；永久禁止类能力封顶建议级）")
    public R<Void> adjustLevel(@PathVariable String capabilityCode, @RequestBody Map<String, Object> body) {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        int level = Integer.parseInt(String.valueOf(body.get("level")));
        masterAgentService.adjustLevel(capabilityCode, level, user.getUserId());
        return R.ok("级别已调整", null);
    }

    @PostMapping("/autonomy/evaluate")
    @Operation(summary = "手动触发升降级评估（默认每日02:00自动执行）")
    public R<Object> evaluate() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return R.ok(masterAgentService.evaluateAll(user.getUserId()));
    }
}
