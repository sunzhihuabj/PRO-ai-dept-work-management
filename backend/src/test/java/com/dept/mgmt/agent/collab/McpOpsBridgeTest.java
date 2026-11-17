package com.dept.mgmt.agent.collab.mcp;

import com.dept.mgmt.aiworkspace.ops.OpsParam;
import com.dept.mgmt.aiworkspace.ops.OpsTool;
import com.dept.mgmt.aiworkspace.ops.OpsToolRegistry;
import com.dept.mgmt.aiworkspace.ops.SystemOpsService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpOpsBridge 单元测试（A3 只读桥接 + A4 身份透传与权限校验 + B4 写工具审批化桥接）
 */
@ExtendWith(MockitoExtension.class)
class McpOpsBridgeTest {

    @Mock
    private OpsToolRegistry opsRegistry;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SystemOpsService systemOpsService;
    @Mock
    private com.dept.mgmt.agent.collab.support.ApprovalActionRegistry approvalActionRegistry;
    @Mock
    private com.dept.mgmt.agent.collab.service.DecisionAdviceService adviceService;

    private McpOpsBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new McpOpsBridge(opsRegistry, userMapper, systemOpsService,
                approvalActionRegistry, adviceService);
        ReflectionTestUtils.setField(bridge, "actingUserId", 1L);
        lenient().when(userMapper.selectById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return sysUser(id);
        });
        // 默认放行（权限校验通过）
        lenient().when(systemOpsService.checkPermission(any(), any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        McpCallContext.clear();
    }

    private SysUser sysUser(long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("user" + id);
        u.setRoleCodes("ROLE_WORKER");
        u.setStatus(1);
        return u;
    }

    private OpsTool readTool(String name, boolean readOnly) {
        return OpsTool.builder().name(name).module("测试").title(name).desc("测试工具 " + name)
                .readOnly(readOnly)
                .params(List.of(
                        OpsParam.builder().name("keyword").type("string").desc("关键词").build(),
                        OpsParam.builder().name("projectId").type("number").required(true).desc("项目ID").build(),
                        OpsParam.builder().name("deadline").type("date").desc("截止日期").build(),
                        OpsParam.builder().name("urgent").type("bool").desc("是否紧急").build()))
                .executor((p, u) -> "ok:" + name + ":" + u.getUserId())
                .build();
    }

    /** 白名单模拟：仅 task.create 登记（真实白名单见 ApprovalActionRegistry.ALLOWED_TOOLS） */
    private void stubWhitelist() {
        lenient().when(approvalActionRegistry.actionOf(any())).thenAnswer(inv ->
                "task.create".equals(inv.getArgument(0)) ? "OPS_TASK_CREATE" : null);
    }

    @Test
    void shouldBridgeReadOnlyAndWhitelistedWriteTools() {
        stubWhitelist();
        when(opsRegistry.all()).thenReturn(List.of(
                readTool("task.myTodo", true),             // 跳过：与 task.my_todo 重复
                readTool("project.list", true),            // 跳过：与 project.query 重复
                readTool("tender.collectPlatforms", true), // 跳过：与 tender.platforms 重复
                readTool("task.detail", true),             // 只读桥接
                readTool("kb.search", true),               // 只读桥接
                readTool("task.create", false),            // B4：白名单写工具 → 审批化暴露
                readTool("ecn.approve", false)));          // 白名单外写工具不暴露

        List<McpTool> tools = bridge.bridgeTools();

        assertEquals(3, tools.size());
        assertEquals(List.of("task.detail", "kb.search", "task.create"),
                tools.stream().map(McpTool::name).toList());
    }

    @Test
    void shouldDeclareRequiresApprovalOnWriteTools() {
        stubWhitelist();
        when(opsRegistry.all()).thenReturn(List.of(
                readTool("task.detail", true), readTool("task.create", false)));

        List<McpTool> tools = bridge.bridgeTools();

        assertFalse(tools.get(0).requiresApproval());
        assertTrue(tools.get(1).requiresApproval());
        assertTrue(tools.get(1).description().contains("requiresApproval=true"));
        assertTrue(tools.get(1).description().contains("不直接写入"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRequestApprovalOnWriteToolCallInsteadOfWriting() {
        stubWhitelist();
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.create", false)));
        com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice advice =
                new com.dept.mgmt.agent.collab.entity.AgentDecisionAdvice();
        advice.setId(501L);
        when(adviceService.requestToolApproval(eq("task.create"), any(), eq(7L), any()))
                .thenReturn(advice);
        McpTool tool = bridge.bridgeTools().get(0);
        McpCallContext.setActingUserId(7L);

        Object out = tool.handler().apply(Map.of("projectId", 9, "keyword", "新任务"));

        Map<String, Object> result = (Map<String, Object>) out;
        assertEquals(Boolean.TRUE, result.get("approvalRequired"));
        assertEquals(501L, result.get("adviceId"));
        assertTrue(String.valueOf(result.get("message")).contains("未直接写入"));
        verify(adviceService).requestToolApproval(eq("task.create"), any(), eq(7L), any());
        // 不直接写入：写工具 executor 无触发路径（tool.handler 即审批桥，非原 executor）
    }

    @Test
    void shouldRejectWriteToolCallWhenPermissionDenied() {
        stubWhitelist();
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.create", false)));
        when(systemOpsService.checkPermission(any(), any())).thenReturn("无权执行");
        McpCallContext.setActingUserId(7L);

        McpTool tool = bridge.bridgeTools().get(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tool.handler().apply(Map.of("projectId", 9)));
        assertEquals(403, ex.getCode());
        // 权限被拒时不产生审批建议
        verify(adviceService, never()).requestToolApproval(any(), any(), any(), any());
    }

    @Test
    void shouldGenerateSchemaFromOpsParams() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("contract.detail", true)));

        Map<String, Object> schema = bridge.bridgeTools().get(0).inputSchema();

        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertEquals(4, props.size());
        assertEquals("string", ((Map<?, ?>) props.get("keyword")).get("type"));
        assertEquals("number", ((Map<?, ?>) props.get("projectId")).get("type"));
        assertTrue(String.valueOf(((Map<?, ?>) props.get("deadline")).get("description")).contains("yyyy-MM-dd"));
        assertEquals("boolean", ((Map<?, ?>) props.get("urgent")).get("type"));
        assertEquals(List.of("projectId"), schema.get("required"));
    }

    @Test
    void shouldFallBackToDelegateWhenNoContext() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.detail", true)));

        McpTool tool = bridge.bridgeTools().get(0);
        Object out = tool.handler().apply(new LinkedHashMap<>(Map.of("projectId", 9)));

        // 无透传 → 系统委托身份（userId=1）
        assertEquals("ok:task.detail:1", out);
    }

    @Test
    void shouldExecuteAsActingUserFromContext() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.my_todo_x", true)));
        McpCallContext.setActingUserId(7L);

        Object out = bridge.bridgeTools().get(0).handler().apply(Map.of("projectId", 9));

        assertEquals("ok:task.my_todo_x:7", out);
    }

    @Test
    void shouldRejectInvalidActingUser() {
        when(userMapper.selectById(99L)).thenReturn(null);
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.detail", true)));
        McpCallContext.setActingUserId(99L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bridge.bridgeTools().get(0).handler().apply(Map.of("projectId", 9)));
        assertEquals(401, ex.getCode());
    }

    @Test
    void shouldRejectDisabledActingUser() {
        SysUser disabled = sysUser(8L);
        disabled.setStatus(0);
        when(userMapper.selectById(8L)).thenReturn(disabled);
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.detail", true)));
        McpCallContext.setActingUserId(8L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bridge.bridgeTools().get(0).handler().apply(Map.of("projectId", 9)));
        assertEquals(401, ex.getCode());
    }

    @Test
    void shouldCheckPermissionBeforeExecute() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("contract.bindProject_x", true)));
        when(systemOpsService.checkPermission(any(), any())).thenReturn("「合同绑定项目」需要权限，当前账号无权执行该操作。");
        McpCallContext.setActingUserId(7L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bridge.bridgeTools().get(0).handler().apply(Map.of("projectId", 9)));
        assertEquals(403, ex.getCode());
    }

    @Test
    void shouldPassActingUserToPermissionCheck() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.detail", true)));
        AtomicReference<Long> checkedUser = new AtomicReference<>();
        when(systemOpsService.checkPermission(any(), any())).thenAnswer(inv -> {
            LoginUser u = inv.getArgument(1);
            checkedUser.set(u.getUserId());
            return null;
        });
        McpCallContext.setActingUserId(9L);

        bridge.bridgeTools().get(0).handler().apply(Map.of("projectId", 9));

        assertEquals(9L, checkedUser.get());
    }

    @Test
    void shouldFallbackToMinimalDelegateWhenUserMissing() {
        when(userMapper.selectById(1L)).thenReturn(null);
        when(opsRegistry.all()).thenReturn(List.of(readTool("kb.search", true)));

        Object out = bridge.bridgeTools().get(0).handler().apply(Map.of());

        assertEquals("ok:kb.search:1", out);
        LoginUser u = (LoginUser) ReflectionTestUtils.getField(bridge, "delegateUser");
        assertNotNull(u);
        assertEquals("mcp-delegate", u.getUsername());
    }

    @Test
    void readOnlyDescriptionsMustStayNeutral() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("contract.detail", true)));

        McpTool t = bridge.bridgeTools().get(0);
        // 防工具描述投毒：描述仅陈述功能，不含诱导性指令
        assertFalse(t.description().contains("必须"));
        assertTrue(t.description().startsWith("测试工具"));
    }
}
