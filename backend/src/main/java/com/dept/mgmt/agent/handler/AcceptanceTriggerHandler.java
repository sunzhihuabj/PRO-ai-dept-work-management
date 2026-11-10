package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.delivery.service.DeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验收自动触发处理器（AGT-19，T-207，Auto）
 * V2 Q-13/14：工程量达 30%/85% 质量Agent 自动触发中期/后期验收任务（BR-03/04）
 * 确定性规则（DeliveryService.triggerAcceptanceChecks），不经 LLM
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcceptanceTriggerHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-19";
    }

    /** 确定性触发检查（防重复触发），可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long projectId = null;
        if (instruction != null) {
            Matcher m = PROJECT_ID.matcher(instruction);
            if (m.find()) {
                projectId = Long.valueOf(m.group(1));
            }
        }
        if (projectId == null) {
            throw new BusinessException(400, "指令中未找到 projectId=，无法定位项目");
        }
        Map<String, Object> result = deliveryService.triggerAcceptanceChecks(projectId, "AGENT");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-19 验收自动触发");
        out.put("projectId", projectId);
        out.put("progress", result.get("progress"));
        out.put("triggered", result.get("triggered"));
        log.info("AGT-19 验收触发检查完成: projectId={} progress={} triggered={}",
                projectId, result.get("progress"), ((List<?>) result.get("triggered")).size());
        return objectMapper.writeValueAsString(out);
    }
}
