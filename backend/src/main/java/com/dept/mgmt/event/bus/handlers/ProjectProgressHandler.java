package com.dept.mgmt.event.bus.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.contract.entity.ContractProject;
import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.ContractProjectMapper;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 项目关键进度 → 关联合同反馈处理器（T-406）：消费 project.progress.updated 事件
 * 项目阶段完成 / 里程碑完成后通知合同侧：履约进度推进，请关注收款与验收节点。
 *
 * <p>幂等披露：事件重试会导致重复通知，Phase 2 由消费记录表兜底。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectProgressHandler implements DomainEventHandler {

    private final ContractProjectMapper contractProjectMapper;
    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.PROJECT_PROGRESS_UPDATED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) {
        Object projectIdObj = payload.get("projectId");
        if (projectIdObj == null) {
            return;
        }
        Long projectId = Long.valueOf(String.valueOf(projectIdObj));
        List<ContractProject> links = contractProjectMapper.selectList(
                new LambdaQueryWrapper<ContractProject>().eq(ContractProject::getProjectId, projectId));
        if (links.isEmpty()) {
            log.info("项目进度事件无关联合同，跳过联动通知: eventId={}", eventId);
            return;
        }
        boolean isMilestone = "MILESTONE".equals(String.valueOf(payload.get("itemType")));
        String itemType = isMilestone ? "里程碑" : "阶段";
        String itemName = String.valueOf(payload.getOrDefault("itemName", ""));
        String projectName = String.valueOf(payload.getOrDefault("projectName", ""));
        String contractIds = links.stream().map(l -> "#" + l.getContractId())
                .collect(java.util.stream.Collectors.joining("、"));
        String title = "项目进度反馈提醒";
        String content = "项目《" + projectName + "》" + itemType + "「" + itemName + "」已完成，"
                + "该项目关联合同（" + contractIds + "）履约进度已推进，请关注验收条件与收款节点。";
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of("title", title, "content", content));
        dto.setReceiverIds(List.of(1L));
        dto.setRelatedId(projectId);
        NotificationSendResult result = notificationService.send(dto);
        log.info("项目进度联动通知完成: eventId={}, projectId={}, item={}/{}, sent={}",
                eventId, projectId, itemType, itemName, result.getSent());
    }
}
