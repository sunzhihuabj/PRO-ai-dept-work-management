package com.dept.mgmt.agent.collab;

import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.agent.collab.support.ApprovalActionRegistry;
import com.dept.mgmt.agent.collab.support.HmacUtils;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AgentDecisionAdviceMapper;
import com.dept.mgmt.platform.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 决策建议服务单元测试（阶段一：HMAC 护栏 + 审批动作执行 + A5 OPS 动作注册表）
 *
 * @author dept-mgmt
 */
class DecisionAdviceServiceTest {

    private static final String SECRET = "test-hmac-secret";

    private AgentDecisionAdviceMapper adviceMapper;
    private com.dept.mgmt.service.TaskService taskService;
    private com.dept.mgmt.aiworkspace.ops.SystemOpsService systemOpsService;
    private ApprovalActionRegistry actionRegistry;
    private com.dept.mgmt.mapper.SysUserMapper userMapper;
    private com.dept.mgmt.common.client.HermesService hermesService;
    private DecisionAdviceService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        adviceMapper = mock(AgentDecisionAdviceMapper.class);
        taskService = mock(com.dept.mgmt.service.TaskService.class);
        systemOpsService = mock(com.dept.mgmt.aiworkspace.ops.SystemOpsService.class);
        actionRegistry = mock(ApprovalActionRegistry.class);
        userMapper = mock(com.dept.mgmt.mapper.SysUserMapper.class);
        hermesService = mock(com.dept.mgmt.common.client.HermesService.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.send(any())).thenReturn(
                Mockito.mock(com.dept.mgmt.platform.notify.dto.NotificationSendResult.class));
        ObjectProvider<com.dept.mgmt.service.TaskService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(taskService);
        ObjectProvider<com.dept.mgmt.aiworkspace.ops.SystemOpsService> opsProvider = mock(ObjectProvider.class);
        when(opsProvider.getObject()).thenReturn(systemOpsService);
        service = new DecisionAdviceService(adviceMapper, provider, opsProvider,
                actionRegistry, userMapper, notificationService, objectMapper, hermesService, SECRET);
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

    // ==================== A5：审批动作注册表 ====================

    private com.dept.mgmt.entity.SysUser sysUser(long id, String roleCodes) {
        var u = new com.dept.mgmt.entity.SysUser();
        u.setId(id);
        u.setUsername("user" + id);
        u.setRoleCodes(roleCodes);
        u.setStatus(1);
        return u;
    }

    @Test
    void requestToolApprovalRejectsNonWhitelistedTool() {
        when(actionRegistry.actionOf("ecn.approve")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.requestToolApproval("ecn.approve", Map.of(), 7L, "测试"));
        assertTrue(e.getMessage().contains("白名单外"));
    }

    @Test
    void requestToolApprovalRejectsInvalidActingUser() {
        when(actionRegistry.actionOf("task.create")).thenReturn("OPS_TASK_CREATE");
        when(actionRegistry.toolOf("OPS_TASK_CREATE"))
                .thenReturn(com.dept.mgmt.aiworkspace.ops.OpsTool.builder()
                        .name("task.create").title("创建任务").readOnly(false)
                        .params(java.util.List.of()).build());
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.requestToolApproval("task.create", Map.of(), 99L, "测试"));
        assertEquals(401, e.getCode());
    }

    @Test
    void requestToolApprovalRejectsInsufficientPermission() {
        when(actionRegistry.actionOf("cost.settle")).thenReturn("OPS_COST_SETTLE");
        when(actionRegistry.toolOf("OPS_COST_SETTLE"))
                .thenReturn(com.dept.mgmt.aiworkspace.ops.OpsTool.builder()
                        .name("cost.settle").title("完工成本核算").readOnly(false)
                        .params(java.util.List.of()).build());
        when(userMapper.selectById(7L)).thenReturn(sysUser(7L, "ROLE_WORKER"));
        when(systemOpsService.checkPermission(any(), any())).thenReturn("「完工成本核算」需要部门领导权限");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.requestToolApproval("cost.settle", Map.of(), 7L, "测试"));
        assertEquals(403, e.getCode());
    }

    @Test
    void approveExecutesOpsToolAction() {
        String paramsJson = "{\"actingUserId\":7,\"toolName\":\"task.create\","
                + "\"params\":{\"projectName\":\"测试项目\",\"taskName\":\"AI 委托建的任务\"}}";
        var a = advice("OPS_TASK_CREATE", paramsJson);
        a.setEvidenceJson("[]");
        when(adviceMapper.selectById(1L)).thenReturn(a);
        when(actionRegistry.actionOf("task.create")).thenReturn("OPS_TASK_CREATE");
        when(actionRegistry.toolOf("OPS_TASK_CREATE")).thenReturn(
                com.dept.mgmt.aiworkspace.ops.OpsTool.builder()
                        .name("task.create").title("创建任务").readOnly(false)
                        .params(java.util.List.of()).build());
        when(userMapper.selectById(7L)).thenReturn(sysUser(7L, "ROLE_WORKER"));
        when(systemOpsService.checkPermission(any(), any())).thenReturn(null);
        when(actionRegistry.execute(eq("OPS_TASK_CREATE"), any(), any())).thenReturn("任务创建成功：AI 委托建的任务");

        var result = service.approve(1L, "同意执行");

        assertEquals(DecisionAdviceService.ST_APPROVED, result.getStatus());
        verify(actionRegistry).execute(eq("OPS_TASK_CREATE"), any(), any());
        // 执行结果回写 evidence_json
        assertTrue(result.getEvidenceJson().contains("任务创建成功"));
        assertTrue(result.getEvidenceJson().contains("user7"));
    }

    @Test
    void approveOpsActionRejectsToolActionMismatch() {
        // 审批间隙被篡改：toolName 与登记动作不一致（HMAC 虽重算一致，但一致性校验拦截伪造编码）
        String paramsJson = "{\"actingUserId\":7,\"toolName\":\"ecn.approve\",\"params\":{}}";
        var a = advice("OPS_ECN_APPROVE", paramsJson);
        when(adviceMapper.selectById(1L)).thenReturn(a);
        when(actionRegistry.actionOf("ecn.approve")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve(1L, null));
        assertTrue(e.getMessage().contains("不一致"));
    }

    @Test
    void approveOpsActionRechecksPermission() {
        String paramsJson = "{\"actingUserId\":7,\"toolName\":\"task.create\",\"params\":{}}";
        var a = advice("OPS_TASK_CREATE", paramsJson);
        when(adviceMapper.selectById(1L)).thenReturn(a);
        when(actionRegistry.actionOf("task.create")).thenReturn("OPS_TASK_CREATE");
        when(actionRegistry.toolOf("OPS_TASK_CREATE")).thenReturn(
                com.dept.mgmt.aiworkspace.ops.OpsTool.builder()
                        .name("task.create").title("创建任务").readOnly(false)
                        .params(java.util.List.of()).build());
        when(userMapper.selectById(7L)).thenReturn(sysUser(7L, "ROLE_WORKER"));
        // 待确认期间委托人权限被收回 → 拦截
        when(systemOpsService.checkPermission(any(), any())).thenReturn("当前账号无权执行该操作。");

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve(1L, null));
        assertEquals(403, e.getCode());
        verify(actionRegistry, never()).execute(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registryRoundtripPreservesCamelCase() {
        com.dept.mgmt.aiworkspace.ops.OpsToolRegistry registry = mock(com.dept.mgmt.aiworkspace.ops.OpsToolRegistry.class);
        when(registry.find(any())).thenReturn(com.dept.mgmt.aiworkspace.ops.OpsTool.builder()
                .name("task.changeStatus").title("任务状态流转").readOnly(false)
                .params(java.util.List.of()).build());
        ApprovalActionRegistry real = new ApprovalActionRegistry(providerOf(registry));
        // 驼峰段不产生额外下划线（changeStatus → CHANGESTATUS）
        assertEquals("OPS_TASK_CHANGESTATUS", real.actionOf("task.changeStatus"));
        assertEquals("OPS_TENDER_UPDATESTATUS", real.actionOf("tender.updateStatus"));
        assertEquals("OPS_ARCHIVE_BORROW", real.actionOf("archive.borrow"));
        // 驼峰还原（大写编码经双向映射，非字符串还原）
        assertNotNull(real.toolOf("OPS_TASK_CHANGESTATUS"));
        assertEquals("task.changeStatus", real.toolOf("OPS_TASK_CHANGESTATUS").getName());
        // 白名单外（审批本身）不可登记/还原
        assertNull(real.actionOf("ecn.approve"));
        assertNull(real.toolOf("OPS_ECN_APPROVE"));
        assertTrue(real.allowedActions().contains("OPS_ARCHIVE_BORROW"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void registryExecuteRunsPrecheckFirst() {
        var tool = com.dept.mgmt.aiworkspace.ops.OpsTool.builder()
                .name("archive.borrow").title("档案借阅").readOnly(false)
                .precheck((p, u) -> "档案 9 已借出，不可重复借出")
                .params(java.util.List.of())
                .executor((p, u) -> "should-not-run")
                .build();
        com.dept.mgmt.aiworkspace.ops.OpsToolRegistry registry = mock(com.dept.mgmt.aiworkspace.ops.OpsToolRegistry.class);
        when(registry.find("archive.borrow")).thenReturn(tool);
        ApprovalActionRegistry real = new ApprovalActionRegistry(providerOf(registry));

        BusinessException e = assertThrows(BusinessException.class,
                () -> real.execute("OPS_ARCHIVE_BORROW", new LinkedHashMap<>(Map.of("archiveId", 9)),
                        new com.dept.mgmt.security.LoginUser(7L, "user7", "ROLE_WORKER", null)));
        assertTrue(e.getMessage().contains("前置校验未通过"));
        assertEquals(409, e.getCode());
    }

    // ==================== B5：Hermes 审批回调双向闭环 ====================

    @Test
    void approveHermesResolveSendsOnceCallback() {
        String paramsJson = "{\"runId\":\"run_ap1\",\"requestId\":\"req_77\",\"actingUserId\":7}";
        var a = advice(DecisionAdviceService.ACTION_HERMES_RESOLVE, paramsJson);
        when(adviceMapper.selectById(1L)).thenReturn(a);
        when(hermesService.resolveApproval("run_ap1", "req_77", true)).thenReturn(true);

        var result = service.approve(1L, "放行一次");

        assertEquals(DecisionAdviceService.ST_APPROVED, result.getStatus());
        verify(hermesService).resolveApproval("run_ap1", "req_77", true);
    }

    @Test
    void rejectHermesResolveSendsDenyCallback() {
        String paramsJson = "{\"runId\":\"run_ap2\",\"requestId\":\"req_78\",\"actingUserId\":7}";
        var a = advice(DecisionAdviceService.ACTION_HERMES_RESOLVE, paramsJson);
        when(adviceMapper.selectById(1L)).thenReturn(a);
        when(hermesService.resolveApproval("run_ap2", "req_78", false)).thenReturn(true);

        var result = service.reject(1L, "不允许执行");

        assertEquals(DecisionAdviceService.ST_REJECTED, result.getStatus());
        verify(hermesService).resolveApproval("run_ap2", "req_78", false);
    }

    @Test
    void approveHermesResolveRollsBackWhenCallbackFails() {
        String paramsJson = "{\"runId\":\"run_ap3\",\"requestId\":\"req_79\",\"actingUserId\":7}";
        var a = advice(DecisionAdviceService.ACTION_HERMES_RESOLVE, paramsJson);
        when(adviceMapper.selectById(1L)).thenReturn(a);
        when(hermesService.resolveApproval("run_ap3", "req_79", true)).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve(1L, null));
        assertTrue(e.getMessage().contains("回调失败"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createHermesApprovalIsIdempotentPerRequestId() {
        // 已存在同 requestId 的 PENDING 建议 → 幂等跳过，不重复落库
        var existing = new com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice();
        existing.setId(88L);
        when(adviceMapper.selectOne(any())).thenReturn(existing);

        Long adviceId = service.createHermesApproval("run_ap4", "req_80", "执行 shell 命令", 7L);

        assertEquals(88L, adviceId);
        verify(adviceMapper, never()).insert(any(com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice.class));
    }

    /** ObjectProvider 测试桩（getObject 返回给定实例） */
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> providerOf(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> p = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(p.getObject()).thenReturn(instance);
        return p;
    }
}
