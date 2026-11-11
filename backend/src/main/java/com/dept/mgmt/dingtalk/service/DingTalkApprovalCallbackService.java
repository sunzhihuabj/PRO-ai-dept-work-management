package com.dept.mgmt.dingtalk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.agent.service.MasterAgentService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.AgentTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉审批回调服务（T-309 审批回调打通）
 * 钉钉内嵌审批单（Ask 级任务确认/用印/资金等）状态回调 → HMAC 验签 → Agent 任务联动：
 * agree → 任务自动确认执行（等效用户在系统内点击确认）；refuse → 任务取消
 * 每次回调落领域事件 dingtalk.approval.completed（台账）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkApprovalCallbackService {

    private final DingTalkAuthService dingTalkAuthService;
    private final AgentTaskMapper taskMapper;
    private final MasterAgentService masterAgentService;
    private final EventBusService eventBusService;

    /**
     * 审批回调处理
     *
     * @param sign             钉钉回调签名（HmacSHA256(timestamp+"\n"+signKey, key=signKey) 的 Base64）
     * @param timestamp        回调时间戳（毫秒，±1 小时窗口）
     * @param processInstanceId 钉钉审批实例ID
     * @param result           审批结果（agree/refuse）
     * @param taskNo           关联 Agent 任务单号（bizData 映射）
     */
    public Map<String, Object> handleApproval(String sign, String timestamp,
                                              String processInstanceId, String result, String taskNo) {
        // 1. 参数完整性
        if (!StringUtils.hasText(sign) || !StringUtils.hasText(timestamp)) {
            throw new BusinessException(401, "回调签名/时间戳缺失");
        }
        // 2. 验签密钥必须已配置（未配置=回调功能关闭，拒绝一切回调）
        String signKey = dingTalkAuthService.auditSignKey();
        if (!StringUtils.hasText(signKey)) {
            throw new BusinessException(401, "审批回调验签密钥未配置，回调已拒绝");
        }
        // 3. 时间戳防重放（±1 小时窗口）
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(401, "回调时间戳非法");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > 3600_000L) {
            throw new BusinessException(401, "回调时间戳超出允许窗口");
        }
        // 4. 钉钉官方口径验签：stringToSign = timestamp + "\n" + signKey，HmacSHA256(key=signKey)
        String expected = hmacSha256Base64(timestamp + "\n" + signKey, signKey);
        if (!expected.equals(sign.trim())) {
            log.warn("钉钉审批回调验签失败: processInstanceId={}, timestamp={}", processInstanceId, timestamp);
            throw new BusinessException(401, "回调验签失败");
        }
        if (!StringUtils.hasText(taskNo)) {
            throw new BusinessException(400, "回调缺少关联任务单号（taskNo）");
        }
        // 5. 关联 Agent 任务（pending_confirm 才可联动）
        AgentTask task = taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTaskNo, taskNo).last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(404, "关联任务不存在: " + taskNo);
        }
        boolean agreed = "agree".equalsIgnoreCase(result);
        String action;
        try {
            if (agreed) {
                // Ask 级任务状态机：提交→queued→running→pending_confirm（执行完成等待采纳）。
                // 钉钉审批回调可能早于任务执行完成到达（审批秒批场景），短暂等待任务进入
                // pending_confirm（最多 5 秒）再确认，避免时序竞争导致联动丢失。
                action = "SKIPPED";
                for (int i = 0; i < 5; i++) {
                    AgentTask current = taskMapper.selectById(task.getId());
                    if (current != null && "pending_confirm".equals(current.getStatus())) {
                        // 钉钉审批通过 = 提交人授权确认（以任务提交人身份执行系统内确认动作）
                        masterAgentService.confirm(task.getId(), task.getCallerUserId());
                        action = "CONFIRMED";
                        break;
                    }
                    if (current == null || List.of("done", "failed", "cancelled").contains(current.getStatus())) {
                        action = "SKIPPED(任务已终态)";
                        break;
                    }
                    Thread.sleep(1000);
                }
            } else {
                masterAgentService.cancel(task.getId(), task.getCallerUserId());
                action = "CANCELLED";
            }
        } catch (BusinessException e) {
            // 任务不在待确认态等业务异常：回调本身有效，联动结果如实返回
            action = "SKIPPED(" + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            action = "SKIPPED(等待中断)";
        }
        // 6. 领域事件台账
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processInstanceId", processInstanceId);
        payload.put("taskNo", taskNo);
        payload.put("result", agreed ? "agree" : "refuse");
        payload.put("action", action);
        String eventId = eventBusService.publish(DomainEventType.DINGTALK_APPROVAL_COMPLETED, payload);
        log.info("钉钉审批回调处理完成: processInstanceId={} taskNo={} result={} action={}",
                processInstanceId, taskNo, result, action);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", eventId);
        out.put("taskNo", taskNo);
        out.put("action", action);
        return out;
    }

    /** HmacSHA256 → Base64（钉钉回调签名口径） */
    private String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(500, "回调验签计算失败");
        }
    }
}
