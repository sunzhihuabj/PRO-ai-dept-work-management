package com.dept.mgmt.agent.handler;

import com.dept.mgmt.aftermarket.service.AftermarketService;
import com.dept.mgmt.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报修受理分级处理器（AGT-23，T-304，Auto）
 * V2 A-02/A-03：报修文本 → 自动登记工单 + 分级（紧急/一般/咨询）+ 响应时效
 * 分级规则为确定性关键词引擎（透明披露，稳定可复核；LLM 分级在真实通道就绪后可切换增强）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepairIntakeHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final AftermarketService aftermarketService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-23";
    }

    /** 确定性分级 + 登记幂等（每次生成新工单），可自动执行 */
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
        String faultDesc = instruction == null ? "" : instruction
                .replaceAll("projectId\\s*=\\s*\\d+", "")
                .replace("报修", "").replace("受理", "").replace("登记", "").trim();
        if (faultDesc.isBlank()) {
            throw new BusinessException(400, "指令中未含故障描述，无法受理");
        }
        Long ticketId = aftermarketService.createRepair(projectId, faultDesc,
                "AGT-23 自动受理");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-23 报修受理分级");
        out.put("ticketId", ticketId);
        out.put("projectId", projectId);
        out.put("faultDesc", faultDesc);
        out.put("level", aftermarketService.classify(faultDesc));
        out.put("sla", SLA_OF(aftermarketService.classify(faultDesc)));
        log.info("AGT-23 报修受理完成: ticketId={} level={}", ticketId, out.get("level"));
        return objectMapper.writeValueAsString(out);
    }

    private String SLA_OF(String level) {
        return switch (level) {
            case "EMERGENCY" -> "紧急：2 小时内响应";
            case "INQUIRY" -> "咨询：48 小时内答复";
            default -> "一般：24 小时内响应";
        };
    }
}
