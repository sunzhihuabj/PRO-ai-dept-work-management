package com.dept.mgmt.agent.collab.mcp;

import com.dept.mgmt.aiworkspace.ops.OpsParam;
import com.dept.mgmt.aiworkspace.ops.OpsTool;
import com.dept.mgmt.aiworkspace.ops.OpsToolRegistry;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * McpOpsBridge 单元测试（A3：Ops 只读工具自动桥接）
 */
@ExtendWith(MockitoExtension.class)
class McpOpsBridgeTest {

    @Mock
    private OpsToolRegistry opsRegistry;
    @Mock
    private SysUserMapper userMapper;

    private McpOpsBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new McpOpsBridge(opsRegistry, userMapper);
        ReflectionTestUtils.setField(bridge, "actingUserId", 1L);
        lenient().when(userMapper.selectById(anyLong())).thenReturn(sysUser());
    }

    private SysUser sysUser() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setRoleCodes("ROLE_DEPT_LEADER");
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

    @Test
    void shouldBridgeReadOnlyOnlyAndSkipDuplicates() {
        when(opsRegistry.all()).thenReturn(List.of(
                readTool("task.myTodo", true),          // 跳过：与 task.my_todo 重复
                readTool("project.list", true),          // 跳过：与 project.query 重复
                readTool("tender.collectPlatforms", true), // 跳过：与 tender.platforms 重复
                readTool("task.detail", true),           // 桥接
                readTool("kb.search", true),             // 桥接
                readTool("task.create", false)));        // 跳过：写入类不暴露

        List<McpTool> tools = bridge.bridgeTools();

        assertEquals(2, tools.size());
        assertEquals(List.of("task.detail", "kb.search"),
                tools.stream().map(McpTool::name).toList());
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
    void shouldExecuteWithDelegateUser() {
        when(opsRegistry.all()).thenReturn(List.of(readTool("task.detail", true)));

        McpTool tool = bridge.bridgeTools().get(0);
        Object out = tool.handler().apply(new LinkedHashMap<>(Map.of("projectId", 9)));

        assertEquals("ok:task.detail:1", out);
    }

    @Test
    void shouldFallbackToMinimalDelegateWhenUserMissing() {
        when(userMapper.selectById(anyLong())).thenReturn(null);
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
