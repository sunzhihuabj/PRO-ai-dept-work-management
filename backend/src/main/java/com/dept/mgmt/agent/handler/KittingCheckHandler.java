package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.design.service.BomCheckRules;
import com.dept.mgmt.workshop.service.WorkshopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 齐套检查处理器（AGT-13，T-202）：确定性计算，不经 LLM（规划验收口径准确率 100%）
 * 需求=项目最新方案 BOM；供给=PO 已到货汇总；结果落 kitting_check（BR-08 数据源）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KittingCheckHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final WorkshopService workshopService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-13";
    }

    /** 只读计算 + 审查留痕，不改业务状态，可自动执行 */
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
        WorkshopService.KittingCalc calc = workshopService.calculateKitting(projectId);
        // 审查留痕（AGT-13 结果即 BR-08 判定依据）
        workshopService.runKitting(projectId, calc.kitRate(),
                objectMapper.writeValueAsString(Map.of("shortages", calc.shortages())), "AGT-13");
        log.info("AGT-13 齐套检查完成: projectId={}, rate={}, shortages={}",
                projectId, calc.kitRate(), calc.shortages().size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-13 齐套检查");
        out.put("projectId", projectId);
        out.put("kitRate", calc.kitRate());
        out.put("totalItems", calc.totalItems());
        out.put("shortages", calc.shortages());
        return objectMapper.writeValueAsString(out);
    }
}
