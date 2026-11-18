package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice;
import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.mapper.AgentDecisionAdviceMapper;
import com.dept.mgmt.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2.0 M4 流程管控治理服务单测（催办扫描/幂等/巡检/SLA 报告）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessGovernanceServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private DecisionAdviceService decisionAdviceService;
    @Mock
    private AgentDecisionAdviceMapper adviceMapper;

    private ProcessGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new ProcessGovernanceService(taskMapper, decisionAdviceService, adviceMapper);
    }

    private Task overdueTask(long id, String name, int minusDays) {
        Task t = new Task();
        t.setId(id);
        t.setTaskName(name);
        t.setStatus(1);
        t.setAssigneeId(9L);
        t.setDeadline(LocalDate.now().minusDays(minusDays));
        return t;
    }

    @Test
    void shouldFireOverdueReminder() {
        when(taskMapper.selectList(any())).thenReturn(List.of(overdueTask(1L, "土建验收", 3)));
        when(adviceMapper.selectCount(any())).thenReturn(0L);

        service.checkOverdueTasks();

        ArgumentCaptor<String> sceneCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        verify(decisionAdviceService).createGovernanceAdvice(sceneCap.capture(), eq("TASK"),
                eq(1L), titleCap.capture(), any(), eq(9L), any());
        assertEquals(DecisionAdviceService.SCENE_TASK_OVERDUE_REMIND, sceneCap.getValue());
        assertTrue(titleCap.getValue().contains("土建验收"));
        assertTrue(titleCap.getValue().contains("3"));
    }

    @Test
    void shouldSkipWhenRemindedToday() {
        when(taskMapper.selectList(any())).thenReturn(List.of(overdueTask(1L, "土建验收", 3)));
        when(adviceMapper.selectCount(any())).thenReturn(1L);

        service.checkOverdueTasks();

        verify(decisionAdviceService, never()).createGovernanceAdvice(any(), any(), anyLong(),
                any(), any(), anyLong(), any());
    }

    @Test
    void shouldNotFireWhenNoOverdue() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        service.checkOverdueTasks();

        verify(decisionAdviceService, never()).createGovernanceAdvice(any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void slaPatrolShouldSkipWhenNoBreach() {
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(adviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.slaPatrol();

        verify(decisionAdviceService, never()).createGovernanceAdvice(any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void slaPatrolShouldFireWhenOverdueExists() {
        when(taskMapper.selectList(any())).thenReturn(List.of(overdueTask(2L, "合同评审", 7)));
        // 第一次 selectCount（幂等检查）=0，第二次（审批超 SLA）=2
        when(adviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 2L);

        service.slaPatrol();

        ArgumentCaptor<String> sceneCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> targetCap = ArgumentCaptor.forClass(Long.class);
        verify(decisionAdviceService).createGovernanceAdvice(sceneCap.capture(), eq("GOVERNANCE"),
                eq(0L), any(), any(), targetCap.capture(), any());
        assertEquals(DecisionAdviceService.SCENE_PROCESS_SLA_WARNING, sceneCap.getValue());
        assertTrue(targetCap.getValue() == null);
    }

    @Test
    void slaReportShouldCarryOverdueDetails() {
        when(taskMapper.selectList(any())).thenReturn(List.of(overdueTask(3L, "设备采购", 5)));
        when(adviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        var report = service.slaReport();

        assertEquals(1, ((Number) report.get("overdueCount")).intValue());
        assertEquals(1, ((Number) report.get("approvalSlaBreachCount")).intValue());
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) report.get("overdueItems");
        assertEquals("设备采购", items.get(0).get("taskName"));
        assertEquals(5L, ((Number) items.get(0).get("overdueDays")).longValue());
    }
}
