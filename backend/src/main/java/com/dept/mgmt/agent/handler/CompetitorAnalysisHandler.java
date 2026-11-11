package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.tender.service.TenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开标竞品分析处理器（AGT-05，T-301，Ask）
 * V2 B-12：各家报价录入后 → 竞品分析报告（统计 + 策略规则，确定性可复核）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompetitorAnalysisHandler implements CapabilityHandler {

    private static final Pattern TENDER_ID = Pattern.compile("tenderId\\s*=\\s*(\\d+)");

    private final TenderService tenderService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-05";
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long tenderId = parseTenderId(instruction);
        if (tenderId == null) {
            throw new BusinessException(400, "指令中未找到 tenderId=，无法定位标讯");
        }
        Map<String, Object> result = tenderService.analyzeCompetitors(tenderId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-05 开标竞品分析");
        out.putAll(result);
        return objectMapper.writeValueAsString(out);
    }

    private Long parseTenderId(String instruction) {
        if (instruction == null) {
            return null;
        }
        Matcher m = TENDER_ID.matcher(instruction);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
