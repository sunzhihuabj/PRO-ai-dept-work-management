package com.dept.mgmt.aiworkspace.ops;

import com.dept.mgmt.aiworkspace.service.AiMemoryService;
import com.dept.mgmt.archive.service.ArchiveService;
import com.dept.mgmt.aftermarket.service.AftermarketService;
import com.dept.mgmt.chnchange.service.EcnService;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.contract.service.ContractService;
import com.dept.mgmt.cost.service.CostService;
import com.dept.mgmt.design.service.DesignService;
import com.dept.mgmt.delivery.service.DeliveryService;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.fineperm.service.FinePermissionService;
import com.dept.mgmt.kb.service.KbService;
import com.dept.mgmt.mapper.AiMessageMapper;
import com.dept.mgmt.mapper.ContractMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.EventService;
import com.dept.mgmt.service.ProjectService;
import com.dept.mgmt.service.TaskService;
import com.dept.mgmt.tender.service.TenderService;
import com.dept.mgmt.workshop.service.WorkshopService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Golden set 测试脚手架（P0-2）：纯 Mockito 环境（无 Spring 上下文/无数据库）。
 * 每个用例独立构建一套 17 依赖的 OpsToolRegistry + SystemOpsService，
 * 把 JSON 用例中的 decision/fixtures/stubs 转成 mock 行为，驱动 tryOps 全链路。
 *
 * @author dept-mgmt
 */
public class OpsTestHarness {

    static {
        // 注册 Project/SysUser 的 MyBatis-Plus lambda 列缓存：单元测试环境没有 MyBatis 启动流程，
        // 不注册的话 wrapper.getSqlSegment() 会抛「can not find lambda cache」，
        // 导致 like/eq 参数无法物化、实体模糊匹配桩无法模拟 SQL 过滤
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Project.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    /** 13 个业务 Service + 3 个 Mapper + ObjectMapper = registry 的 17 个构造依赖 */
    public final TaskService taskService = mock(TaskService.class);
    public final ProjectService projectService = mock(ProjectService.class);
    public final ContractService contractService = mock(ContractService.class);
    public final TenderService tenderService = mock(TenderService.class);
    public final DesignService designService = mock(DesignService.class);
    public final WorkshopService workshopService = mock(WorkshopService.class);
    public final DeliveryService deliveryService = mock(DeliveryService.class);
    public final EcnService ecnService = mock(EcnService.class);
    public final CostService costService = mock(CostService.class);
    public final AftermarketService aftermarketService = mock(AftermarketService.class);
    public final ArchiveService archiveService = mock(ArchiveService.class);
    public final EventService eventService = mock(EventService.class);
    public final KbService kbService = mock(KbService.class);
    public final ProjectMapper projectMapper = mock(ProjectMapper.class);
    public final SysUserMapper userMapper = mock(SysUserMapper.class);
    public final ContractMapper contractMapper = mock(ContractMapper.class);
    public final ObjectMapper objectMapper = new ObjectMapper();

    public final AiGatewayClient gateway = mock(AiGatewayClient.class);
    public final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    public final FinePermissionService finePermissionService = mock(FinePermissionService.class);
    public final AiMemoryService memoryService = mock(AiMemoryService.class);

    public final OpsToolRegistry registry;
    public final OpsSkillCards skillCards;
    public final OpsPendingStore pendingStore = new OpsPendingStore();
    public final SystemOpsService service;
    public final LoginUser user;

    public OpsTestHarness(String role, long userId) {
        registry = new OpsToolRegistry(taskService, projectService, contractService, tenderService,
                mock(com.dept.mgmt.tender.collect.service.TenderCollectService.class),
                mock(com.dept.mgmt.tender.collect.service.TenderPlatformService.class),
                designService, workshopService, deliveryService, ecnService, costService, aftermarketService,
                archiveService, eventService, kbService, projectMapper, userMapper, contractMapper,
                mock(com.dept.mgmt.mapper.FileInfoMapper.class), mock(com.dept.mgmt.mapper.FileVersionMapper.class),
                objectMapper);
        registry.init();
        skillCards = new OpsSkillCards(registry);
        service = new SystemOpsService(registry, skillCards, pendingStore, gateway, messageMapper,
                finePermissionService, memoryService, objectMapper);
        user = new LoginUser(userId, "tester", role, List.of());
    }

    /** 应用实体匹配桩数据（项目/成员 fixture → 对应 Mapper 的 selectList/selectById） */
    public void applyFixtures(JsonNode fixtures) {
        if (fixtures == null || !fixtures.isObject()) {
            return;
        }
        List<Project> projects = new ArrayList<>();
        for (JsonNode p : fixtures.path("projects")) {
            Project e = new Project();
            e.setId(p.path("id").asLong());
            e.setProjectCode(p.path("code").asText());
            e.setProjectName(p.path("name").asText());
            projects.add(e);
        }
        if (!projects.isEmpty()) {
            // 模拟 SQL like/eq 过滤：按 wrapper 中的参数值对 fixture 做包含匹配；
            // 无参数（全量兜底扫描）返回全部，由业务代码自行做双向包含过滤
            when(projectMapper.selectList(any())).thenAnswer(inv ->
                    filterByWrapperParams(inv.getArgument(0), projects, Project::getProjectName));
            for (Project e : projects) {
                when(projectMapper.selectById(e.getId())).thenReturn(e);
            }
        }
        List<SysUser> users = new ArrayList<>();
        for (JsonNode u : fixtures.path("users")) {
            SysUser e = new SysUser();
            e.setId(u.path("id").asLong());
            e.setUsername(u.path("username").asText());
            e.setRealName(u.path("realName").asText());
            users.add(e);
        }
        if (!users.isEmpty()) {
            when(userMapper.selectList(any())).thenAnswer(inv ->
                    filterByWrapperParams(inv.getArgument(0), users, u2 -> u2.getUsername() + " " + nvl(u2.getRealName())));
            for (SysUser e : users) {
                when(userMapper.selectById(e.getId())).thenReturn(e);
            }
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
     * 从 MyBatis-Plus wrapper 提取 SQL 参数值（如 %三一重能%），对 fixture 做双向包含匹配，
     * 忠实模拟 like 查询语义；参数为空视为无条件查询（全量扫描兜底）返回全部 fixture。
     */
    private <T> List<T> filterByWrapperParams(Object wrapper, List<T> fixtures, java.util.function.Function<T, String> nameFn) {
        java.util.Collection<Object> paramValues;
        try {
            // 先渲染 SQL 段物化 like/eq 参数（lazy 注册），再读取参数表
            wrapper.getClass().getMethod("getSqlSegment").invoke(wrapper);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> pairs = (java.util.Map<String, Object>)
                    wrapper.getClass().getMethod("getParamNameValuePairs").invoke(wrapper);
            paramValues = pairs.values();
        } catch (Exception e) {
            return fixtures;
        }
        if (paramValues.isEmpty()) {
            return fixtures;
        }
        List<T> matched = new ArrayList<>();
        for (Object v : paramValues) {
            String kw = String.valueOf(v).replace("%", "").replace("_", "").trim();
            if (kw.isEmpty()) {
                continue;
            }
            for (T f : fixtures) {
                String name = nameFn.apply(f);
                if (name != null && (kw.contains(name) || name.contains(kw)) && !matched.contains(f)) {
                    matched.add(f);
                }
            }
        }
        return matched;
    }

    /** 应用执行器桩（字符串指令 → 具体 mock 行为） */
    public void applyStubs(JsonNode stubs) {
        if (stubs == null || !stubs.isArray()) {
            return;
        }
        for (JsonNode s : stubs) {
            switch (s.asText()) {
                case "kb.search.empty" -> when(kbService.search(anyString(), anyInt())).thenReturn(List.of());
                case "cost.ledger.empty" -> when(costService.listLedger(any())).thenReturn(List.of());
                case "perm.grant" -> when(finePermissionService.hasPerm(any(), any())).thenReturn(true);
                case "contract.detail.missing" ->
                        when(contractService.detail(any())).thenThrow(new BusinessException(404, "合同不存在"));
                case "task.detail.missing" ->
                        when(taskService.detail(any())).thenThrow(new BusinessException(404, "任务不存在"));
                default -> throw new IllegalArgumentException("未知测试桩指令: " + s.asText());
            }
        }
    }

    /**
     * H2 归属校验默认桩：任务存在且负责人为测试用户（userId=1）。
     * task.changeStatus / task.addWorkHour 的 precheck 会经 taskService.detail 做归属判定；
     * 显式桩（如 task.detail.missing）在 applyStubs 中后注册、覆盖本默认桩。
     */
    public void applyDefaultOwnershipStub() {
        com.dept.mgmt.entity.Task owned = new com.dept.mgmt.entity.Task();
        owned.setAssigneeId(1L);
        when(taskService.detail(any())).thenReturn(owned);
    }
}
