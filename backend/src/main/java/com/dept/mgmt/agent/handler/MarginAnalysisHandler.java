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
 * 毛利三口径分析处理器（AGT-22，T-303，Ask）
 * V2 F-07：成本定稿后 → 财务Agent自动计算三口径毛利（完全/标准/现金），分解价差
 * 三口径计算式为确定性 SQL 统计（口径定义已在 CostService 披露）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarginAnalysisHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final CostService costService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-22";
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
        Map<String, Object> result = costService.settleProject(projectId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-22 毛利三口径分析");
        out.putAll(result);
        return objectMapper.writeValueAsString(out);
    }
}
