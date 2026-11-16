package com.dept.mgmt.aiworkspace.ops;

import com.dept.mgmt.aiworkspace.service.AiMemoryService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 判定/校验/技能卡/实体匹配的纯逻辑单元测试（P0-2）。
 * 不走 LLM，直接覆盖 parseDecision 鲁棒边界、validate/checkPerm/semanticValidate
 * 与 P0-1/P0-4 的关键行为契约。
 *
 * @author dept-mgmt
 */
class SystemOpsDecisionTest {

    private final ObjectMapper json = new ObjectMapper();

    private OpsTestHarness harness(String role) {
        return new OpsTestHarness(role, 1L);
    }

    // ==================== parseDecision 边界 ====================

    @Test
    void parseDecision应容忍代码块围栏() {
        OpsTestHarness h = harness("ROLE_USER");
        String raw = "判定如下：\n```json\n{\"action\":\"tool\",\"tool\":\"task.list\",\"params\":{\"status\":\"进行中\"}}\n```\n请查收。";
        SystemOpsService.LlmDecision d = h.service.parseDecision(raw);
        assertNotNull(d);
        assertEquals("tool", d.action());
        assertEquals("task.list", d.tool());
        assertEquals("进行中", d.params().get("status"));
    }

    @Test
    void parseDecision应容忍前后杂文() {
        OpsTestHarness h = harness("ROLE_USER");
        String raw = "我认为 {\"action\":\"chat\",\"reply\":\"你好\"} 是合适的回复。";
        SystemOpsService.LlmDecision d = h.service.parseDecision(raw);
        assertNotNull(d);
        assertEquals("chat", d.action());
        assertEquals("你好", d.reply());
    }

    @Test
    void parseDecision非法输出返回null() {
        OpsTestHarness h = harness("ROLE_USER");
        assertNull(h.service.parseDecision("哎呀，我理解不了你的意思呢。"));
        assertNull(h.service.parseDecision(null));
        assertNull(h.service.parseDecision(""));
    }

    @Test
    void parseDecision无action默认chat() {
        OpsTestHarness h = harness("ROLE_USER");
        SystemOpsService.LlmDecision d = h.service.parseDecision("{\"reply\":\"你好呀\"}");
        assertNotNull(d);
        assertEquals("chat", d.action());
    }

    // ==================== validate / checkPerm ====================

    @Test
    void validate缺必填返回补充说明() {
        OpsTestHarness h = harness("ROLE_USER");
        String missing = h.service.validate(h.registry.find("task.create"), Map.of());
        assertNotNull(missing);
        assertTrue(missing.contains("所属项目名称"));
        assertTrue(missing.contains("任务名称"));
        // 参数齐全且枚举合法 → 通过
        assertNull(h.service.validate(h.registry.find("task.create"),
                Map.of("projectName", "三一重能一期", "taskName", "冒烟", "priority", "高")));
    }

    @Test
    void validate非法枚举抛业务异常() {
        OpsTestHarness h = harness("ROLE_USER");
        BusinessException e = assertThrows(BusinessException.class,
                () -> h.service.validate(h.registry.find("task.list"), Map.of("status", "进行")));
        assertTrue(e.getMessage().contains("任务状态」仅支持"));
    }

    @Test
    void checkPerm部门领导直接放行() {
        OpsTestHarness h = harness("ROLE_DEPT_LEADER");
        assertNull(h.service.checkPerm(h.registry.find("ecn.approve"), h.user));
        assertNull(h.service.checkPerm(h.registry.find("contract.bindProject"), h.user));
    }

    @Test
    void checkPerm角色门槛拦截() {
        OpsTestHarness h = harness("ROLE_USER");
        String denied = h.service.checkPerm(h.registry.find("ecn.approve"), h.user);
        assertNotNull(denied);
        assertTrue(denied.contains("部门领导"));
    }

    @Test
    void checkPerm精细权限点拦截与放行() {
        OpsTestHarness h = harness("ROLE_USER");
        when(h.finePermissionService.hasPerm(any(), any())).thenReturn(false);
        assertTrue(h.service.checkPerm(h.registry.find("contract.bindProject"), h.user).contains("未获授权"));
        when(h.finePermissionService.hasPerm(any(), any())).thenReturn(true);
        assertNull(h.service.checkPerm(h.registry.find("contract.bindProject"), h.user));
    }

    // ==================== semanticValidate（P0-4） ====================

    @Test
    void semanticValidate唯一命中时规范化项目名() {
        OpsTestHarness h = harness("ROLE_USER");
        Project p = new Project();
        p.setId(1L);
        p.setProjectCode("PRJ001");
        p.setProjectName("三一重能一期");
        when(h.projectMapper.selectList(any())).thenReturn(List.of(p));
        Map<String, Object> params = new java.util.LinkedHashMap<>(
                Map.of("projectName", "三一重能", "taskName", "冒烟"));
        String err = h.service.semanticValidate(h.registry.find("task.create"), params, h.user);
        assertNull(err);
        assertEquals("三一重能一期", params.get("projectName"), "口语称呼应被规范化为库内标准项目名");
    }

    @Test
    void semanticValidate未找到项目返回核对提示() {
        OpsTestHarness h = harness("ROLE_USER");
        when(h.projectMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> params = new java.util.LinkedHashMap<>(Map.of("projectName", "神秘项目"));
        String err = h.service.semanticValidate(h.registry.find("project.detail"), params, h.user);
        assertNotNull(err);
        assertTrue(err.contains("未找到项目「神秘项目」"));
        assertTrue(err.contains("查一下项目"));
    }

    @Test
    void semanticValidate项目歧义列出编号候选() {
        OpsTestHarness h = harness("ROLE_USER");
        Project a = new Project();
        a.setId(1L);
        a.setProjectCode("PRJ001");
        a.setProjectName("三一重能一期");
        Project b = new Project();
        b.setId(2L);
        b.setProjectCode("PRJ002");
        b.setProjectName("三一重能二期");
        when(h.projectMapper.selectList(any())).thenReturn(List.of(a, b));
        Map<String, Object> params = new java.util.LinkedHashMap<>(Map.of("projectName", "三一重能"));
        String err = h.service.semanticValidate(h.registry.find("project.detail"), params, h.user);
        assertNotNull(err);
        assertTrue(err.contains("匹配到多个"));
        assertTrue(err.contains("1. PRJ001 三一重能一期"));
        assertTrue(err.contains("2. PRJ002 三一重能二期"));
    }

    @Test
    void semanticValidate成员未找到返回提示() {
        OpsTestHarness h = harness("ROLE_USER");
        when(h.userMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> params = new java.util.LinkedHashMap<>(Map.of("assignee", "赵六"));
        String err = h.service.semanticValidate(h.registry.find("task.list"), params, h.user);
        assertNotNull(err);
        assertTrue(err.contains("未找到成员「赵六」"));
    }

    @Test
    void semanticValidate成员唯一命中规范化为账号() {
        OpsTestHarness h = harness("ROLE_USER");
        SysUser u = new SysUser();
        u.setId(11L);
        u.setUsername("zhangsan");
        u.setRealName("张三");
        when(h.userMapper.selectList(any())).thenReturn(List.of(u));
        Map<String, Object> params = new java.util.LinkedHashMap<>(Map.of("assignee", "张三"));
        String err = h.service.semanticValidate(h.registry.find("task.list"), params, h.user);
        assertNull(err);
        assertEquals("zhangsan", params.get("assignee"), "成员参数应规范化为账号");
    }

    @Test
    void semanticValidate写入前置校验拦截不存在任务() {
        OpsTestHarness h = harness("ROLE_USER");
        when(h.taskService.detail(any())).thenThrow(new BusinessException(404, "任务不存在"));
        Map<String, Object> params = new java.util.LinkedHashMap<>(Map.of("taskId", 5, "targetStatus", "进行中"));
        String err = h.service.semanticValidate(h.registry.find("task.changeStatus"), params, h.user);
        assertNotNull(err);
        assertTrue(err.contains("任务 5 不存在"));
    }

    @Test
    void semanticValidate前置校验异常收敛为无法执行() {
        OpsTestHarness h = harness("ROLE_USER");
        when(h.finePermissionService.hasPerm(any(), any())).thenReturn(true);
        // 项目需先唯一命中，才能走到合同前置校验
        Project p = new Project();
        p.setId(1L);
        p.setProjectCode("PRJ001");
        p.setProjectName("三一重能一期");
        when(h.projectMapper.selectList(any())).thenReturn(List.of(p));
        when(h.contractService.detail(any())).thenThrow(new BusinessException(404, "合同不存在"));
        Map<String, Object> params = new java.util.LinkedHashMap<>(Map.of("contractId", 9, "projectName", "三一重能一期"));
        String err = h.service.semanticValidate(h.registry.find("contract.bindProject"), params, h.user);
        assertNotNull(err);
        assertTrue(err.contains("不存在或不可访问"));
    }

    // ==================== 技能卡 / 目录 / 实体匹配（P0-1） ====================

    @Test
    void 技能卡触发词命中正确版块() {
        OpsTestHarness h = harness("ROLE_USER");
        assertEquals(1, h.skillCards.match("查一下生产工单齐套率").size());
        assertEquals("生产管理", h.skillCards.match("查一下生产工单齐套率").get(0).name());
        assertEquals(2, h.skillCards.match("查一下三一重能的任务和生产工单").size());
        assertTrue(h.skillCards.match("你好").isEmpty());
        assertEquals(13, h.skillCards.all().size());
        assertFalse(OpsSkillCards.GLOBAL_RULES.isBlank());
    }

    @Test
    void 摘要目录覆盖全部13个版块与工具名() {
        OpsTestHarness h = harness("ROLE_USER");
        String brief = h.skillCards.briefCatalog();
        for (OpsSkillCards.OpsSkillCard card : h.skillCards.all()) {
            assertTrue(brief.contains(card.name()), "摘要目录缺少版块：" + card.name());
        }
        assertTrue(brief.contains("task.myTodo"));
        assertTrue(brief.contains("kb.search"));
    }

    @Test
    void 目录支持按版块过滤() {
        OpsTestHarness h = harness("ROLE_USER");
        String taskCatalog = h.registry.catalog("任务管理");
        assertTrue(taskCatalog.contains("task.myTodo"));
        assertTrue(taskCatalog.contains("task.create"));
        assertFalse(taskCatalog.contains("contract.list"));
        // 全量目录仍然可用
        assertTrue(h.registry.catalog().contains("contract.list"));
    }

    @Test
    void 实体匹配按ID精确命中() {
        OpsTestHarness h = harness("ROLE_USER");
        Project p = new Project();
        p.setId(1L);
        p.setProjectCode("PRJ001");
        p.setProjectName("三一重能一期");
        when(h.projectMapper.selectById(1L)).thenReturn(p);
        OpsToolRegistry.EntityMatch<Project> match = h.registry.matchProject("1");
        assertNotNull(match.entity());
        assertEquals("三一重能一期", match.entity().getProjectName());
        assertTrue(match.candidates().isEmpty());
    }

    @Test
    void 实体匹配无结果返回none() {
        OpsTestHarness h = harness("ROLE_USER");
        when(h.projectMapper.selectList(any())).thenReturn(List.of());
        OpsToolRegistry.EntityMatch<Project> match = h.registry.matchProject("不存在的东西");
        assertNull(match.entity());
        assertTrue(match.candidates().isEmpty());
    }
}
