package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.dept.mgmt.workshop.entity.PurchaseOrder;
import com.dept.mgmt.workshop.service.WorkshopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采购到货跟踪处理器（AGT-15，T-203，Auto）
 * PO 交期盘点：未完结且期望到货日期早于今日 → 逾期预警；
 * 逾期存在时经通知中心发站内信（通知失败不阻塞盘点结论）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArrivalTrackHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final WorkshopService workshopService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-15";
    }

    /** 只读盘点 + 通知，不改业务状态，可自动执行 */
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
        LocalDate today = LocalDate.now();
        List<PurchaseOrder> overdue = workshopService.listOverduePos(projectId, today);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseOrder po : overdue) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("poId", po.getId());
            row.put("poNo", po.getPoNo());
            row.put("materialCode", po.getMaterialCode());
            row.put("qty", po.getQty());
            row.put("arrivedQty", po.getArrivedQty());
            row.put("expectedDate", String.valueOf(po.getExpectedDate()));
            row.put("overdueDays", java.time.temporal.ChronoUnit.DAYS.between(po.getExpectedDate(), today));
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            // 逾期预警 → 站内信（Phase 1 简化通知 1 号领导账号）
            try {
                NotificationSendDTO dto = new NotificationSendDTO();
                dto.setTemplateCode("GENERIC_REMIND");
                dto.setParams(Map.of("title", "PO 到货逾期预警",
                        "content", "项目 " + projectId + " 存在 " + rows.size() + " 张逾期未完结 PO，请跟进催货"));
                dto.setReceiverIds(List.of(1L));
                NotificationSendResult result = notificationService.send(dto);
                log.info("AGT-15 逾期预警已通知: sent={}", result.getSent());
            } catch (Exception e) {
                log.warn("AGT-15 预警通知发送失败（不阻塞）: {}", e.getMessage());
            }
        }
        log.info("AGT-15 到货盘点完成: projectId={}, overdue={}", projectId, rows.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-15 采购到货跟踪");
        out.put("projectId", projectId);
        out.put("checkedAt", String.valueOf(LocalDate.now()));
        out.put("overdueCount", rows.size());
        out.put("overdueList", rows);
        return objectMapper.writeValueAsString(out);
    }
}
