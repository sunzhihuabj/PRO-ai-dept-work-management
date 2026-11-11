package com.dept.mgmt.agent.handler;

import com.dept.mgmt.aftermarket.entity.WarrantyInfo;
import com.dept.mgmt.aftermarket.service.AftermarketService;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质保金提醒处理器（AGT-24，T-304，Auto）
 * V2 B-32：质保期满前 30 天 → 商务Agent自动触发质保金申请流程提醒；
 * 幂等（reminded 标记防重复提醒），推送走钉钉通道（降级站内信）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarrantyRemindHandler implements CapabilityHandler {

    private final AftermarketService aftermarketService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-24";
    }

    /** 幂等盘点 + 提醒，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        List<WarrantyInfo> expiring = aftermarketService.listExpiringWarranties(30);
        List<Map<String, Object>> reminded = new ArrayList<>();
        for (WarrantyInfo info : expiring) {
            try {
                NotificationSendDTO dto = new NotificationSendDTO();
                dto.setTemplateCode("GENERIC_REMIND");
                dto.setParams(Map.of(
                        "title", "质保金到期提醒",
                        "content", "项目 " + info.getProjectId() + " 质保期将于 " + info.getWarrantyEnd()
                                + " 到期（质保金 " + (info.getDepositAmount() == null ? "未登记" : info.getDepositAmount())
                                + " 元），请发起 B-32 质保金申请流程"));
                dto.setChannel("DINGTALK");
                dto.setReceiverIds(List.of(1L));
                dto.setRelatedId(info.getProjectId());
                NotificationSendResult result = notificationService.send(dto);
                log.info("AGT-24 质保金提醒已发送: warrantyId={}, channel={}, sent={}",
                        info.getId(), result.getChannel(), result.getSent());
            } catch (Exception e) {
                log.warn("AGT-24 提醒发送失败（不阻塞）: {}", e.getMessage());
            }
            aftermarketService.markWarrantyReminded(info.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", info.getProjectId());
            row.put("warrantyEnd", String.valueOf(info.getWarrantyEnd()));
            row.put("depositAmount", info.getDepositAmount());
            reminded.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-24 质保金到期提醒");
        out.put("remindedCount", reminded.size());
        out.put("remindedList", reminded);
        return objectMapper.writeValueAsString(out);
    }
}
