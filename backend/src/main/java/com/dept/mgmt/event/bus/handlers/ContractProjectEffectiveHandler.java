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
 * 合同生效 → 关联项目联动处理器（T-406）：消费 contract.effective 事件
 * 用印生效时若合同已关联项目（补绑场景），通知项目侧跟进执行依据；
 * 常规流程先生效后绑定，此时无关联记录则静默跳过。
 *
 * <p>幂等披露：事件重试会导致重复通知，与 ContractEffectivePaymentHandler 同口径；
 * Phase 2 由消费记录表兜底。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractProjectEffectiveHandler implements DomainEventHandler {

    private final ContractProjectMapper contractProjectMapper;
    private final ProjectMapper projectMapper;
    private final NotificationService notificationService;

    @Override
    public DomainEventType eventType() {
        return DomainEventType.CONTRACT_EFFECTIVE;
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
            log.info("合同生效事件无关联项目，跳过联动通知: eventId={}", eventId);
            return;
        }
        Map<Long, Project> projects = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        String projectNames = projectIds.stream()
                .map(pid -> projects.containsKey(pid) ? "《" + projects.get(pid).getProjectName() + "》" : "项目#" + pid)
                .collect(Collectors.joining("、"));
        String contractNo = String.valueOf(payload.getOrDefault("contractNo", ""));
        String title = "合同生效联动提醒";
        String content = "合同 " + contractNo + "《" + payload.getOrDefault("name", "") + "》已完成用印并正式生效，"
                + "关联项目：" + projectNames + "，请各项目负责人以合同为执行依据推进交付与回款。";
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTemplateCode("GENERIC_REMIND");
        dto.setParams(Map.of("title", title, "content", content));
        // Phase 1 简化口径：通知部门领导（1 号账号），Phase 2 按项目负责人 ID 精准投递
        dto.setReceiverIds(List.of(1L));
        dto.setRelatedId(contractId);
        NotificationSendResult result = notificationService.send(dto);
        log.info("合同生效联动通知完成: eventId={}, contractNo={}, projects={}, sent={}",
                eventId, contractNo, projectNames, result.getSent());
    }
}
