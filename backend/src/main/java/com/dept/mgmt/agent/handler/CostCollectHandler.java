package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.cost.service.CostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成本自动归集处理器（AGT-21，T-303，Auto）
 * V2 F-03：业务事件触发 → 财务Agent自动归集人工+物料+费用到项目；
 * 本期物料成本自动归集（入库数量×标准价，幂等），人工/费用由录入端补齐
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostCollectHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final CostService costService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-21";
    }

    /** 归集幂等（唯一键防重），可自动执行 */
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
        Map<String, Object> result = costService.collectMaterialCost(projectId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-21 成本自动归集");
        out.putAll(result);
        return objectMapper.writeValueAsString(out);
    }
}
