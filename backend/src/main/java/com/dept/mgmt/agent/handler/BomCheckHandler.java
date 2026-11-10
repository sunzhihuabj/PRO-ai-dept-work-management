package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.design.service.BomCheckRules;
import com.dept.mgmt.design.service.DesignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BOM 校验处理器（AGT-11，T-116）：13 条确定性规则引擎，不经 LLM（可复现、准确率 100% 语义）
 * 校验结果留痕 design_bom_check_log；缺陷清单返回工作台供修正
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BomCheckHandler implements CapabilityHandler {

    private static final Pattern SCHEME_ID = Pattern.compile("schemeId\\s*=\\s*(\\d+)");

    private final DesignService designService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-11";
    }

    /** 只读校验 + 留痕，不改业务状态，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long schemeId = null;
        if (instruction != null) {
            Matcher m = SCHEME_ID.matcher(instruction);
            if (m.find()) {
                schemeId = Long.valueOf(m.group(1));
            }
        }
        if (schemeId == null) {
            throw new BusinessException(400, "指令中未找到 schemeId=，无法定位方案");
        }
        BomCheckRules.CheckResult result = designService.checkBom(schemeId, "AGT-11");
        log.info("AGT-11 BOM 校验完成: schemeId={}, passed={}, violations={}",
                schemeId, result.passed(), result.violations().size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-11 BOM 校验");
        out.put("schemeId", schemeId);
        out.put("passed", result.passed());
        out.put("totalItems", result.totalItems());
        out.put("ruleCount", BomCheckRules.RULE_COUNT);
        out.put("violations", result.violations());
        return objectMapper.writeValueAsString(out);
    }
}
