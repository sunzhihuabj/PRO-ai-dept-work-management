package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.workshop.service.WorkshopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 缺料请购草稿处理器（AGT-14，T-202）：基于 AGT-13 齐套计算结果，
 * 对每个缺料行生成 DRAFT 状态采购订单（请购草稿，下达须人工）
 * 建议级能力：requireConfirm=true，是否执行由 T-110 自主策略级别共同决定
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseDraftHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final WorkshopService workshopService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-14";
    }

    /** 写操作类（生成采购草稿），默认人工确认 */
    @Override
    public boolean requireConfirm() {
        return true;
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
        if (calc.shortages().isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("capability", "AGT-14 缺料请购草稿");
            out.put("projectId", projectId);
            out.put("kitRate", calc.kitRate());
            out.put("drafts", List.of());
            out.put("message", "无缺料项，未生成请购草稿");
            return objectMapper.writeValueAsString(out);
        }
        List<Map<String, Object>> drafts = new ArrayList<>();
        for (WorkshopService.Shortage s : calc.shortages()) {
            Long poId = workshopService.createDraftPo(projectId, s.materialCode(), s.materialName(), s.shortage());
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("poId", poId);
            d.put("materialCode", s.materialCode());
            d.put("materialName", s.materialName());
            d.put("suggestQty", s.shortage());
            drafts.add(d);
        }
        log.info("AGT-14 请购草稿生成: projectId={}, drafts={}", projectId, drafts.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-14 缺料请购草稿");
        out.put("projectId", projectId);
        out.put("kitRate", calc.kitRate());
        out.put("drafts", drafts);
        return objectMapper.writeValueAsString(out);
    }
}
