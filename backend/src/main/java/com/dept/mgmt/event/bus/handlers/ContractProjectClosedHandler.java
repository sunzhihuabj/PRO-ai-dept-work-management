package com.dept.mgmt.event.bus.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.contract.entity.ContractProject;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.event.bus.DomainEventHandler;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.ContractProjectMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 合同关闭/终止 → 关联项目联动处理器（T-406）：消费 contract.closed 事件
 * 合同关闭/终止后通知关联项目侧：执行依据变化，注意收尾与结算安排。
 *
 * <p>幂等披露：事件重试会导致重复通知，Phase 2 由消费记录表兜底。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractProjectClosedHandler implements DomainEventHandler {

    private final ContractProjectMapper contractProjectMapper;
    private final ProjectMapper projectMapper;
    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.CONTRACT_CLOSED;
    }

    @Override
    public void handle(String eventId, Map<String, Object> payload) {
        Object contractIdObj = payload.get("contractId");
        if (contractIdObj == null) {
            return;
        }
        Long contractId = Long.valueOf(String.valueOf(contractIdObj));
        List<Long> projectIds = contractProjectMapper.selectList(new LambdaQueryWrapper<ContractProject>()
                        .eq(ContractProject::getContractId, contractId))
                .stream().map(ContractProject::getProjectId).toList();
        if (projectIds.isEmpty()) {
            log.info("合同关闭事件无关联项目，跳过联动通知: eventId={}", eventId);
            return;
        }
        Map<Long, Project> projects = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        String projectNames = projectIds.stream()
                .map(pid -> projects.containsKey(pid) ? "《" + projects.get(pid).getProjectName() + "》" : "项目#" + pid)
                .collect(Collectors.joining("、"));
        boolean terminated = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("terminated", "false")));
        String action = terminated ? "终止" : "关闭";
        String contractNo = String.valueOf(payload.getOrDefault("contractNo", ""));
        String title = "合同" + action + "联动提醒";
        String content = "合同 " + contractNo + "《" + payload.getOrDefault("name", "") + "》已" + action
                + "，关联项目：" + projectNames + "。执行依据已变化，请项目负责人确认收尾、结算与后续安排。";
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of("title", title, "content", content));
        dto.setReceiverIds(List.of(1L));
        dto.setRelatedId(contractId);
        NotificationSendResult result = notificationService.send(dto);
        log.info("合同{}联动通知完成: eventId={}, contractNo={}, projects={}, sent={}",
                action, eventId, contractNo, projectNames, result.getSent());
    }
}
