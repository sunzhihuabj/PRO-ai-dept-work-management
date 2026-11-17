package com.dept.mgmt.agent.collab;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.agent.collab.support.HmacUtils;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AgentDecisionAdviceMapper;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 决策建议服务单元测试（阶段一：HMAC 护栏 + 审批动作执行）
 *
 * @author dept-mgmt
 */
class DecisionAdviceServiceTest {

    private static final String SECRET = "test-hmac-secret";

    private AgentDecisionAdviceMapper adviceMapper;
    private com.dept.mgmt.service.TaskService taskService;
    private DecisionAdviceService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        adviceMapper = mock(AgentDecisionAdviceMapper.class);
        taskService = mock(com.dept.mgmt.service.TaskService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ObjectProvider<com.dept.mgmt.service.TaskService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(taskService);
        service = new DecisionAdviceService(adviceMapper, provider, notificationService, objectMapper, SECRET);
    }

    @Test
    void hmacRoundtrip() {
        String data = "{\"taskId\":1,\"assigneeId\":2}";
        String sig = HmacUtils.sign(SECRET, data);
        assertTrue(HmacUtils.verify(SECRET, data, sig));
        assertFalse(HmacUtils.verify(SECRET, data, "deadbeef"));
        assertFalse(HmacUtils.verify("other-secret", data, sig));
        // 篡改数据后校验失败
        assertFalse(HmacUtils.verify(SECRET, data + " ", sig));
    }

    private com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice advice(String action, String paramsJson) {
        var a = new com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice();
        a.setId(1L);
        a.setStatus(DecisionAdviceService.ST_PENDING);
        a.setApprovalAction(action);
        a.setApprovalParamsJson(paramsJson);
        if (paramsJson != null) {
            a.setApprovalHmac(HmacUtils.sign(SECRET, paramsJson));
        }
        a.setRequiresApproval(1);
        return a;
    }

    @Test
    void approveExecutesAssignAction() {
        String params = "{\"taskId\":11,\"assigneeId\":7}";
        when(adviceMapper.selectById(1L)).thenReturn(advice(DecisionAdviceService.ACTION_AUTO_ASSIGN_TASK, params));

        var result = service.approve(1L, "同意");

        assertEquals(DecisionAdviceService.ST_APPROVED, result.getStatus());
        verify(taskService).assign(11L, 7L, "agent-advice:1");
    }

    @Test
    void approveRejectsTamperedPayload() {
        String params = "{\"taskId\":11,\"assigneeId\":7}";
        var tampered = advice(DecisionAdviceService.ACTION_AUTO_ASSIGN_TASK, params);
        // 审批间隙被篡改：HMAC 与参数不匹配
        tampered.setApprovalParamsJson("{\"taskId\":99,\"assigneeId\":7}");
        when(adviceMapper.selectById(1L)).thenReturn(tampered);

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve(1L, null));
        assertTrue(e.getMessage().contains("完整性校验失败"));
        verify(taskService, never()).assign(any(), any(), any());
    }

    @Test
    void rejectOnlyFlipsStatus() {
        var a = advice(null, null);
        a.setRequiresApproval(0);
        when(adviceMapper.selectById(1L)).thenReturn(a);

        var result = service.reject(1L, "改派给他人");

        assertEquals(DecisionAdviceService.ST_REJECTED, result.getStatus());
        assertEquals("改派给他人", result.getApproverComment());
        verify(taskService, never()).assign(any(), any(), any());
    }

    @Test
    void taskCreatedWithoutAssigneeBecomesApprovalAdvice() {
        var a = advice(null, null);
        a.setRequiresApproval(0);
        // 场景生成：缺 assigneeId → REQUEST 建议（requiresApproval=1）
        Mockito.doAnswer(inv -> {
            var saved = inv.getArgument(0, com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice.class);
            a.setStatus(saved.getStatus());
            a.setRequiresApproval(saved.getRequiresApproval());
            a.setApprovalAction(saved.getApprovalAction());
            assertNotNull(saved.getApprovalHmac());
            return 1;
        }).when(adviceMapper).insert(any(com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice.class));

        service.onTaskCreated(java.util.Map.of(
                "taskId", 5L, "taskName", "编写测试报告", "actorUserId", 7L));

        assertEquals(DecisionAdviceService.ST_PENDING, a.getStatus());
        assertEquals(1, a.getRequiresApproval());
        assertEquals(DecisionAdviceService.ACTION_AUTO_ASSIGN_TASK, a.getApprovalAction());
    }
}
