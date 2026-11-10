package com.dept.mgmt.agent.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.delivery.entity.Acceptance;
import com.dept.mgmt.delivery.entity.Shipment;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.AcceptanceMapper;
import com.dept.mgmt.mapper.ShipmentMapper;
import com.dept.mgmt.mapper.UserMessageMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 到货款/验收款收款提醒处理器（AGT-20，T-207，Auto）
 * V2 B-29 批量发货后 → 到货款收款提醒；B-30 终验通过后 → 验收款收款提醒
 * 只读盘点 + 站内信提醒（幂等：同单号已提醒过则跳过，通知失败不阻塞盘点结论）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReminderHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final ShipmentMapper shipmentMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final UserMessageMapper userMessageMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-20";
    }

    /** 只读盘点 + 提醒，不改业务状态，可自动执行 */
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
        List<Map<String, Object>> arrivalReminded = new ArrayList<>();
        List<Map<String, Object>> acceptanceReminded = new ArrayList<>();
        int skipped = 0;

        // ===== 到货款（B-29：批量发货后）=====
        List<Shipment> shipments = shipmentMapper.selectList(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getStatus, "SUBMITTED")
                .eq(projectId != null, Shipment::getProjectId, projectId)
                .orderByAsc(Shipment::getId));
        for (Shipment shipment : shipments) {
            if (remindedAlready("到货款", shipment.getShipmentNo())) {
                skipped++;
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "到货款");
            row.put("shipmentNo", shipment.getShipmentNo());
            row.put("projectId", shipment.getProjectId());
            row.put("qty", shipment.getQty());
            row.put("shippedAt", String.valueOf(shipment.getShippedAt()));
            boolean sent = sendReminder("到货款收款提醒",
                    "发货单 " + shipment.getShipmentNo() + " 已发运（项目 " + shipment.getProjectId()
                            + "，" + shipment.getQty() + " 台），请发起 B-29 到货款收款申请");
            row.put("notified", sent);
            arrivalReminded.add(row);
        }

        // ===== 验收款（B-30：终验通过后）=====
        List<Acceptance> finals = acceptanceMapper.selectList(new LambdaQueryWrapper<Acceptance>()
                .eq(Acceptance::getStage, "FINAL")
                .eq(Acceptance::getStatus, "PASS")
                .eq(projectId != null, Acceptance::getProjectId, projectId)
                .orderByAsc(Acceptance::getId));
        for (Acceptance acceptance : finals) {
            String ref = "PROJECT-" + acceptance.getProjectId();
            if (remindedAlready("验收款", ref)) {
                skipped++;
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "验收款");
            row.put("projectId", acceptance.getProjectId());
            row.put("acceptedAt", String.valueOf(acceptance.getAcceptedAt()));
            boolean sent = sendReminder("验收款收款提醒",
                    "项目 " + acceptance.getProjectId() + " 终验已通过（三方签字生效），请发起 B-30 验收款收款申请");
            row.put("notified", sent);
            acceptanceReminded.add(row);
        }

        log.info("AGT-20 收款提醒完成: 到货款 {} 验收款 {} 跳过(已提醒) {}",
                arrivalReminded.size(), acceptanceReminded.size(), skipped);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-20 到货款/验收款提醒");
        out.put("projectId", projectId);
        out.put("arrivalReminded", arrivalReminded);
        out.put("acceptanceReminded", acceptanceReminded);
        out.put("skipped", skipped);
        return objectMapper.writeValueAsString(out);
    }

    /** 幂等：站内信已存在同类型同单号提醒则跳过 */
    private boolean remindedAlready(String typeKeyword, String refNo) {
        return userMessageMapper.selectCount(new LambdaQueryWrapper<UserMessage>()
                .like(UserMessage::getTitle, typeKeyword)
                .like(UserMessage::getContent, refNo)) > 0;
    }

    private boolean sendReminder(String title, String content) {
        try {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTemplateCode("GENERIC_REMIND");
            dto.setParams(Map.of("title", title, "content", content));
            dto.setReceiverIds(List.of(1L));
            NotificationSendResult result = notificationService.send(dto);
            return result.getSent() > 0;
        } catch (Exception e) {
            log.warn("AGT-20 提醒发送失败（不阻塞）: {}", e.getMessage());
            return false;
        }
    }
}
