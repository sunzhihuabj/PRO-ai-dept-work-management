package com.dept.mgmt.agent.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.DisclosureRecordMapper;
import com.dept.mgmt.workshop.entity.DisclosureRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技术交底检查处理器（AGT-17，T-203，Auto，拦截类）
 * 与 BR-09 硬卡点同源判定：工单/项目级交底记录存在已签字 → PASS，否则 BLOCK；
 * 真正的开工拦截内嵌在 WorkshopService.startWorkOrder（非事后检查），本能力为开工前例行核查出口
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisclosureCheckHandler implements CapabilityHandler {

    private static final Pattern WORKORDER_ID = Pattern.compile("workorderId\\s*=\\s*(\\d+)");

    private final DisclosureRecordMapper disclosureMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-17";
    }

    /** 只读核查，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long parsedId = null;
        if (instruction != null) {
            Matcher m = WORKORDER_ID.matcher(instruction);
            if (m.find()) {
                parsedId = Long.valueOf(m.group(1));
            }
        }
        if (parsedId == null) {
            throw new BusinessException(400, "指令中未找到 workorderId=，无法定位工单");
        }
        final Long workorderId = parsedId;
        DisclosureRecord signed = disclosureMapper.selectOne(new LambdaQueryWrapper<DisclosureRecord>()
                .eq(DisclosureRecord::getSigned, 1)
                .and(w -> w.eq(DisclosureRecord::getWorkorderId, workorderId)
                        .or().isNull(DisclosureRecord::getWorkorderId))
                .orderByDesc(DisclosureRecord::getId).last("LIMIT 1"));
        String verdict = signed == null ? "BLOCK" : "PASS";
        log.info("AGT-17 交底检查: workorderId={}, verdict={}", workorderId, verdict);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-17 技术交底检查");
        out.put("workorderId", workorderId);
        out.put("verdict", verdict);
        out.put("signedRecordId", signed == null ? null : signed.getId());
        out.put("signTime", signed == null ? null : String.valueOf(signed.getSignTime()));
        out.put("message", "PASS".equals(verdict) ? "交底已签字确认，允许开工"
                : "硬卡点 BR-09：技术交底记录未签字，生产工单不允许开工");
        return objectMapper.writeValueAsString(out);
    }
}
