package com.dept.mgmt.aiworkspace.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.archive.entity.ArchiveBorrow;
import com.dept.mgmt.archive.entity.ArchiveInfo;
import com.dept.mgmt.archive.service.ArchiveService;
import com.dept.mgmt.aftermarket.entity.RepairTicket;
import com.dept.mgmt.aftermarket.service.AftermarketService;
import com.dept.mgmt.chnchange.entity.EcnChange;
import com.dept.mgmt.chnchange.service.EcnService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.contract.service.ContractService;
import com.dept.mgmt.cost.service.CostService;
import com.dept.mgmt.delivery.service.DeliveryService;
import com.dept.mgmt.design.service.DesignService;
import com.dept.mgmt.dto.EventHandleDTO;
import com.dept.mgmt.dto.ProjectDTO;
import com.dept.mgmt.dto.TaskDTO;
import com.dept.mgmt.dto.WorkHourDTO;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.entity.EventInfo;
import com.dept.mgmt.kb.dto.KbSearchHit;
import com.dept.mgmt.kb.service.KbService;
import com.dept.mgmt.mapper.ContractMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.EventService;
import com.dept.mgmt.service.ProjectService;
import com.dept.mgmt.service.TaskService;
import com.dept.mgmt.tender.collect.entity.TenderCollectRun;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import com.dept.mgmt.tender.service.TenderService;
import com.dept.mgmt.workshop.service.WorkshopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * AI 系统操作工具注册表（T-411）
 *
 * <p>覆盖 13 个业务版块 33 个高频操作：查询/统计类同步执行，写入类经用户确认。
 * executor 一律复用现有 Service，不新增 SQL；红线（审批/删除/主数据）不注册。
 * 同时提供注入 LLM 的「版块知识卡 + 工具目录」，让助手 100% 对齐系统业务口径。
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpsToolRegistry {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TaskService taskService;
    private final ProjectService projectService;
    private final ContractService contractService;
    private final TenderService tenderService;
    private final com.dept.mgmt.tender.collect.service.TenderCollectService tenderCollectService;
    private final com.dept.mgmt.tender.collect.service.TenderPlatformService tenderPlatformService;
    private final DesignService designService;
    private final WorkshopService workshopService;
    private final DeliveryService deliveryService;
    private final EcnService ecnService;
    private final CostService costService;
    private final AftermarketService aftermarketService;
    private final ArchiveService archiveService;
    private final EventService eventService;
    private final KbService kbService;
    private final ProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final ContractMapper contractMapper;
    private final ObjectMapper objectMapper;

    private final Map<String, OpsTool> tools = new LinkedHashMap<>();

    // 说明：原 93 行整块「系统版块知识」已按 P0-1 渐进披露改造拆分至 OpsSkillCards（13 张版块技能卡），
    // 常驻注入 LLM 的仅剩 briefCatalog() 摘要目录，命中版块后才注入完整业务口径。

    // ==================== 工具注册 ====================

    @PostConstruct
    public void init() {
        registerTaskTools();
        registerProjectTools();
        registerContractTools();
        registerTenderTools();
        registerDesignTools();
        registerWorkshopTools();
        registerDeliveryTools();
        registerEcnTools();
        registerCostTools();
        registerAftermarketTools();
        registerArchiveTools();
        registerCollabTools();
        log.info("AI 系统操作工具注册完成: {} 个", tools.size());
    }

    public Collection<OpsTool> all() {
        return tools.values();
    }

    public OpsTool find(String name) {
        return name == null ? null : tools.get(name);
    }

    /** 紧凑工具目录（注入 LLM）：名称|版块|说明|参数 */
    public String catalog() {
        return catalog(null);
    }

    /** 按版块过滤的紧凑工具目录（P0-1 渐进披露：命中版块后仅注入该版块工具的完整参数详情） */
    public String catalog(String module) {
        StringBuilder sb = new StringBuilder();
        for (OpsTool t : tools.values()) {
            if (module != null && !module.equals(t.getModule())) {
                continue;
            }
            sb.append(t.getName()).append('|').append(t.getModule()).append('|').append(t.getDesc()).append('|');
            if (t.getParams().isEmpty()) {
                sb.append("无参数");
            } else {
                sb.append(t.getParams().stream()
                        .map(p -> p.getName() + (p.isRequired() ? "(必填," : "(可选,") + p.getType()
                                + (p.getEnums() == null ? "" : ":" + String.join("/", p.getEnums()))
                                + "," + p.getDesc() + ")")
                        .collect(Collectors.joining(" ")));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void tool(OpsTool t) {
        tools.put(t.getName(), t);
    }

    // ---------- 任务 ----------

    private void registerTaskTools() {
        tool(OpsTool.builder().name("task.myTodo").module("任务管理").title("我的待办任务")
                .desc("查询当前用户的待办任务列表").readOnly(true).params(List.of())
                .displayCols(cols("taskName=任务", "projectName=项目", "status=状态", "priority=优先级", "deadline=截止", "progress=进度%"))
                .executor((p, u) -> renderList(taskService.myTodoTasks(u.getUserId()),
                        "task", cols("taskName=任务", "status=状态", "priority=优先级", "deadline=截止", "progress=进度%"), true))
                .build());

        tool(OpsTool.builder().name("task.list").module("任务管理").title("查询任务")
                .desc("按项目/状态/负责人/关键词查询任务列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").build(),
                        OpsParam.builder().name("status").type("string").desc("任务状态").enums(List.of("待办", "进行中", "待审核", "已完成", "已关闭")).build(),
                        OpsParam.builder().name("priority").type("string").desc("优先级").enums(List.of("高", "中", "低")).build(),
                        OpsParam.builder().name("assignee").type("string").desc("负责人姓名或账号").resolve("user").build(),
                        OpsParam.builder().name("keyword").type("string").desc("任务名称关键词").build()))
                .executor((p, u) -> {
                    SysUser assignee = p.containsKey("assignee") ? resolveAssignee((String) p.get("assignee")) : null;
                    PageResult<Task> page = taskService.list(1, 10, null, str(p, "projectName"),
                            p.containsKey("status") ? taskStatus((String) p.get("status")) : null,
                            p.containsKey("priority") ? taskPriority((String) p.get("priority")) : null,
                            assignee == null ? null : assignee.getId(), str(p, "keyword"));
                    return renderPage(page, "task", cols("taskName=任务", "projectName=项目", "status=状态",
                            "priority=优先级", "assigneeName=负责人", "deadline=截止", "progress=进度%"));
                })
                .build());

        tool(OpsTool.builder().name("task.detail").module("任务管理").title("任务详情")
                .desc("按任务ID查详情（ID可从 task.list 结果获取）").readOnly(true)
                .params(List.of(OpsParam.builder().name("taskId").type("number").required(true).desc("任务ID").build()))
                .executor((p, u) -> renderCard(taskService.detail(num(p, "taskId").longValue()), "task",
                        cols("taskName=任务名称", "projectId=项目ID", "status=状态", "priority=优先级", "progress=进度%",
                                "planHours=计划工时", "actualHours=实际工时", "startDate=开始日期", "deadline=截止日期", "description=描述")))
                .build());

        tool(OpsTool.builder().name("task.create").module("任务管理").title("创建任务")
                .desc("创建新任务（需用户确认后执行）").readOnly(false)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").required(true).desc("所属项目名称").resolve("project").build(),
                        OpsParam.builder().name("taskName").type("string").required(true).desc("任务名称").build(),
                        OpsParam.builder().name("assignee").type("string").desc("负责人姓名或账号").resolve("user").build(),
                        OpsParam.builder().name("priority").type("string").desc("优先级").enums(List.of("高", "中", "低")).build(),
                        OpsParam.builder().name("deadline").type("date").desc("截止日期 yyyy-MM-dd").build(),
                        OpsParam.builder().name("description").type("string").desc("任务描述").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    TaskDTO dto = new TaskDTO();
                    dto.setProjectId(project.getId());
                    dto.setTaskName(str(p, "taskName"));
                    if (p.containsKey("assignee")) {
                        dto.setAssigneeId(resolveAssignee(str(p, "assignee")).getId());
                    }
                    if (p.containsKey("priority")) {
                        dto.setPriority(taskPriority(str(p, "priority")));
                    }
                    if (p.containsKey("deadline")) {
                        dto.setDeadline(date(p, "deadline"));
                    }
                    dto.setDescription(str(p, "description"));
                    Long id = taskService.create(dto);
                    return "任务创建成功：" + str(p, "taskName") + "（ID=" + id + "，项目：" + project.getProjectName() + "）";
                })
                // H2 整改：写入前置校验——确认前先核对项目存在（建任务为全员场景，权限由项目归属校验承接）
                .precheck((p, u) -> {
                    try {
                        resolveProject(str(p, "projectName"));
                        return null;
                    } catch (BusinessException e) {
                        return e.getMessage();
                    }
                })
                .build());

        tool(OpsTool.builder().name("task.changeStatus").module("任务管理").title("任务状态流转")
                .desc("变更任务状态（需用户确认后执行）").readOnly(false)
                .params(List.of(
                        OpsParam.builder().name("taskId").type("number").required(true).desc("任务ID").build(),
                        OpsParam.builder().name("targetStatus").type("string").required(true).desc("目标状态")
                                .enums(List.of("待办", "进行中", "待审核", "已完成", "已关闭")).build(),
                        OpsParam.builder().name("comment").type("string").desc("流转说明").build()))
                .executor((p, u) -> {
                    taskService.changeStatus(num(p, "taskId").longValue(), taskStatus(str(p, "targetStatus")), str(p, "comment"));
                    return "任务 " + num(p, "taskId") + " 状态已流转为「" + str(p, "targetStatus") + "」";
                })
                // 写入前置校验（P0-4 + H2）：确认前核对任务存在，并校验操作归属——
                // 仅任务负责人本人、项目负责人或部门领导可变更他人任务状态
                .precheck((p, u) -> {
                    try {
                        Task t = taskService.detail(num(p, "taskId").longValue());
                        return checkTaskOwnership(t, p.get("taskId"), u);
                    } catch (BusinessException e) {
                        return "任务 " + p.get("taskId") + " 不存在，请先查询任务列表核对任务ID";
                    }
                })
                .build());

        tool(OpsTool.builder().name("task.addWorkHour").module("任务管理").title("录入工时")
                .desc("为任务录入工作工时（0.5-24 小时，需用户确认后执行）").readOnly(false)
                .params(List.of(
                        OpsParam.builder().name("taskId").type("number").required(true).desc("任务ID").build(),
                        OpsParam.builder().name("hours").type("number").required(true).desc("工时数（0.5-24）").build(),
                        OpsParam.builder().name("workDate").type("date").required(true).desc("工作日期 yyyy-MM-dd").build(),
                        OpsParam.builder().name("description").type("string").required(true).desc("工作内容").build()))
                .executor((p, u) -> {
                    WorkHourDTO dto = new WorkHourDTO();
                    dto.setHours(BigDecimal.valueOf(((Number) p.get("hours")).doubleValue()));
                    dto.setWorkDate(date(p, "workDate"));
                    dto.setDescription(str(p, "description"));
                    taskService.addWorkHour(num(p, "taskId").longValue(), dto);
                    return "任务 " + num(p, "taskId") + " 已录入工时 " + p.get("hours") + " 小时（" + str(p, "workDate") + "）";
                })
                // 写入前置校验（P0-4 + H2）：确认前核对任务存在，并校验操作归属
                .precheck((p, u) -> {
                    try {
                        Task t = taskService.detail(num(p, "taskId").longValue());
                        return checkTaskOwnership(t, p.get("taskId"), u);
                    } catch (BusinessException e) {
                        return "任务 " + p.get("taskId") + " 不存在，请先查询任务列表核对任务ID";
                    }
                })
                .build());
    }

    /**
     * H2 归属校验：仅任务负责人本人、项目负责人或部门领导可操作该任务（录入工时/变更状态）。
     * 目标存在时返回 null 放行；越权返回拒绝文案。
     */
    private String checkTaskOwnership(Task t, Object taskId, LoginUser u) {
        if (t == null) {
            return "任务 " + taskId + " 不存在，请先查询任务列表核对任务ID";
        }
        boolean isOwner = t.getAssigneeId() != null && t.getAssigneeId().equals(u.getUserId());
        boolean isLeader = u.getRoles() != null && (u.getRoles().contains("ROLE_DEPT_LEADER")
                || u.getRoles().contains("ROLE_PROJECT_LEADER"));
        if (!isOwner && !isLeader) {
            return "任务 " + taskId + " 的负责人不是您，仅任务负责人本人、项目负责人或部门领导可执行该操作";
        }
        return null;
    }

    // ---------- 项目 ----------

    private void registerProjectTools() {
        tool(OpsTool.builder().name("project.list").module("项目管理").title("查询项目")
                .desc("按名称/状态/类型查询项目列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("name").type("string").desc("项目名称关键词").build(),
                        OpsParam.builder().name("status").type("string").desc("项目状态").enums(List.of("待启动", "进行中", "已完成", "已关闭", "暂停")).build(),
                        OpsParam.builder().name("projectType").type("string").desc("项目类型").enums(List.of("整机厂", "技改", "研发")).build()))
                .executor((p, u) -> renderPage(projectService.list(1, 10, str(p, "name"),
                                p.containsKey("status") ? projectStatus(str(p, "status")) : null,
                                p.containsKey("projectType") ? projectType(str(p, "projectType")) : null),
                        "project", cols("projectCode=编号", "projectName=项目名称", "projectType=类型",
                                "status=状态", "customerName=客户", "projectLeader=负责人", "planEndDate=计划完工")))
                .build());

        tool(OpsTool.builder().name("project.detail").module("项目管理").title("项目详情")
                .desc("查项目详情（含阶段/里程碑概要）").readOnly(true)
                .params(List.of(OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    StringBuilder sb = new StringBuilder("**项目详情**\n\n");
                    sb.append("- 编号：").append(nvl(project.getProjectCode())).append('\n');
                    sb.append("- 名称：").append(nvl(project.getProjectName())).append('\n');
                    sb.append("- 类型：").append(cn("project.projectType", project.getProjectType()))
                            .append("；状态：").append(cn("project.status", project.getStatus())).append('\n');
                    sb.append("- 客户：").append(nvl(project.getCustomerName()))
                            .append("；负责人：").append(nvl(project.getProjectLeader())).append('\n');
                    sb.append("- 合同金额：").append(project.getContractAmount() == null ? "—" : project.getContractAmount() + " 元").append('\n');
                    sb.append("- 计划周期：").append(project.getPlanStartDate() == null ? "—" : project.getPlanStartDate())
                            .append(" ~ ").append(project.getPlanEndDate() == null ? "—" : project.getPlanEndDate()).append('\n');
                    return sb.toString();
                })
                .build());

        tool(OpsTool.builder().name("project.create").module("项目管理").title("创建项目")
                .desc("创建新项目（必填：名称/类型/客户/计划起止日期；需用户确认后执行）").readOnly(false)
                .minRole("ROLE_DEPT_LEADER")
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").build(),
                        OpsParam.builder().name("projectType").type("string").required(true).desc("项目类型").enums(List.of("整机厂", "技改", "研发")).build(),
                        OpsParam.builder().name("customerName").type("string").required(true).desc("客户名称").build(),
                        OpsParam.builder().name("planStartDate").type("date").required(true).desc("计划开始日期 yyyy-MM-dd").build(),
                        OpsParam.builder().name("planEndDate").type("date").required(true).desc("计划结束日期 yyyy-MM-dd").build(),
                        OpsParam.builder().name("projectLeader").type("string").desc("项目负责人姓名").build(),
                        OpsParam.builder().name("contractAmount").type("number").desc("合同金额（元）").build(),
                        OpsParam.builder().name("description").type("string").desc("项目描述").build()))
                .executor((p, u) -> {
                    ProjectDTO dto = new ProjectDTO();
                    dto.setProjectName(str(p, "projectName"));
                    dto.setProjectType(projectType(str(p, "projectType")));
                    dto.setCustomerName(str(p, "customerName"));
                    dto.setPlanStartDate(date(p, "planStartDate"));
                    dto.setPlanEndDate(date(p, "planEndDate"));
                    dto.setProjectLeader(str(p, "projectLeader"));
                    if (p.containsKey("contractAmount")) {
                        dto.setContractAmount(bd(p, "contractAmount"));
                    }
                    dto.setDescription(str(p, "description"));
                    Long id = projectService.create(dto);
                    return "项目创建成功：" + str(p, "projectName") + "（ID=" + id + "，编号自动生成）";
                })
                .build());
    }

    // ---------- 合同 ----------

    private void registerContractTools() {
        tool(OpsTool.builder().name("contract.list").module("合同管理").title("查询合同")
                .desc("按名称/状态查询合同列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("name").type("string").desc("合同名称/编号关键词").build(),
                        OpsParam.builder().name("status").type("string").desc("合同状态")
                                .enums(List.of("草稿", "会签", "审批", "用印", "生效", "关闭", "终止")).build()))
                .executor((p, u) -> renderPage(contractService.list(1, 10, str(p, "name"),
                                p.containsKey("status") ? contractStatus(str(p, "status")) : null),
                        "contract", cols("contractNo=编号", "name=合同名称", "customerName=客户",
                                "amount=金额(元)", "status=状态", "riskLevel=风险等级")))
                .build());

        tool(OpsTool.builder().name("contract.detail").module("合同管理").title("合同详情")
                .desc("按合同ID或编号查详情").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("contractId").type("number").desc("合同ID").build(),
                        OpsParam.builder().name("contractNo").type("string").desc("合同编号（HT开头）").build()))
                .executor((p, u) -> {
                    Long id = p.containsKey("contractId") ? num(p, "contractId").longValue() : resolveContractId(str(p, "contractNo"));
                    Map<String, Object> detail = contractService.detail(id);
                    StringBuilder sb = new StringBuilder("**合同详情**\n\n");
                    appendField(sb, detail, "contractNo", "编号");
                    appendField(sb, detail, "name", "名称");
                    appendField(sb, detail, "customerName", "客户");
                    appendField(sb, detail, "amount", "金额(元)");
                    appendField(sb, detail, "status", "状态");
                    appendField(sb, detail, "riskLevel", "风险等级");
                    appendField(sb, detail, "riskSummary", "风险摘要");
                    appendField(sb, detail, "paymentTerms", "收款条款");
                    appendField(sb, detail, "deliveryTime", "交付时间");
                    sb.append("- 关联项目：");
                    List<Map<String, Object>> projects = contractService.listProjects(id);
                    sb.append(projects.isEmpty() ? "无" : projects.stream()
                            .map(m -> String.valueOf(m.get("projectName"))).collect(Collectors.joining("、"))).append('\n');
                    return sb.toString();
                })
                .build());

        tool(OpsTool.builder().name("contract.bindProject").module("合同管理").title("合同绑定项目")
                .desc("将生效合同与项目建立关联（需用户确认后执行）").readOnly(false)
                .permCode("api:contract:link")
                .params(List.of(
                        OpsParam.builder().name("contractId").type("number").required(true).desc("合同ID").build(),
                        OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    contractService.bind(num(p, "contractId").longValue(), project.getId());
                    return "合同 " + num(p, "contractId") + " 已与项目「" + project.getProjectName() + "」建立关联";
                })
                // 写入前置校验（P0-4）：确认前先核对合同存在且可访问
                .precheck((p, u) -> {
                    try {
                        contractService.detail(num(p, "contractId").longValue());
                        return null;
                    } catch (BusinessException e) {
                        return "合同 " + p.get("contractId") + " 不存在或不可访问，请先查询合同核对ID";
                    }
                })
                .build());
    }

    // ---------- 投标 ----------

    private void registerTenderTools() {
        tool(OpsTool.builder().name("tender.list").module("投标管理").title("查询标讯")
                .desc("按状态/关键词查询投标标讯列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("status").type("string").desc("标讯状态")
                                .enums(List.of("新标讯", "跟踪中", "已投标", "备标中", "已定标", "已中标", "未中标")).build(),
                        OpsParam.builder().name("keyword").type("string").desc("标题关键词").build()))
                .executor((p, u) -> renderPage(tenderService.listTenders(1, 10,
                                p.containsKey("status") ? tenderStatus(str(p, "status")) : null, str(p, "keyword")),
                        "tender", cols("tenderNo=编号", "title=标题", "platform=平台", "budget=预算(元)",
                                "deadline=截标时间", "status=状态")))
                .build());

        tool(OpsTool.builder().name("tender.updateStatus").module("投标管理").title("推进标讯状态")
                .desc("更新标讯跟进状态（需用户确认后执行）").readOnly(false)
                // H2 整改：写入工具强制权限声明——标讯推进为项目负责人级操作
                .minRole("ROLE_PROJECT_LEADER")
                .params(List.of(
                        OpsParam.builder().name("tenderId").type("number").required(true).desc("标讯ID").build(),
                        OpsParam.builder().name("status").type("string").required(true).desc("目标状态")
                                .enums(List.of("新标讯", "跟踪中", "已投标", "备标中", "已定标", "已中标", "未中标")).build(),
                        OpsParam.builder().name("remark").type("string").desc("备注").build()))
                .executor((p, u) -> {
                    tenderService.updateStatus(num(p, "tenderId").longValue(), tenderStatus(str(p, "status")), str(p, "remark"));
                    return "标讯 " + num(p, "tenderId") + " 状态已更新为「" + str(p, "status") + "」";
                })
                .build());

        // AGT-27（T-415）：AI 对话触发联网采集——按平台配置登录+采集+提取+登记
        tool(OpsTool.builder().name("tender.collect").module("投标管理").title("联网采集标讯")
                .desc("按采集平台配置联网抓取最新标讯，AI 提取关键信息后自动登记到标讯台账（同步执行，耗时与平台页数相关）")
                .readOnly(false)
                // 触发外呼采集并写入台账 → 项目负责人级
                .minRole("ROLE_PROJECT_LEADER")
                .params(List.of(
                        OpsParam.builder().name("platformId").type("number").required(true).desc("采集平台ID（tender.collectPlatforms 查询）").build(),
                        OpsParam.builder().name("resume").type("boolean").desc("是否从上次断点续采").build()))
                .executor((p, u) -> {
                    TenderPlatform platform = tenderPlatformService.mustExist(num(p, "platformId").longValue());
                    if (platform.getEnabled() == null || platform.getEnabled() != 1) {
                        throw new BusinessException(400, "平台「" + platform.getPlatformName() + "」已停用");
                    }
                    TenderCollectRun run = tenderCollectService.runCollect(platform.getId(),
                            u == null ? null : u.getUserId(), "AGENT_DIALOG",
                            Boolean.parseBoolean(String.valueOf(p.getOrDefault("resume", "false"))));
                    return "平台「" + platform.getPlatformName() + "」采集完成（运行 #" + run.getId() + "，状态 "
                            + run.getStatus() + "）：发现 " + run.getItemsFound() + " 条，登记 "
                            + run.getRegisterOk() + " 条，去重 " + run.getRegisterDup() + " 条，待人工核验 "
                            + run.getExtractFailed() + " 条。可在投标管理中查询。";
                })
                .build());

        // AGT-27（T-415）：查询可用的采集平台清单（配套 tender.collect 使用）
        tool(OpsTool.builder().name("tender.collectPlatforms").module("投标管理").title("查询采集平台")
                .desc("查询已启用的标讯采集平台清单（含ID/名称/认证方式）").readOnly(true)
                .params(List.of())
                .executor((p, u) -> {
                    var platforms = tenderPlatformService.listEnabled();
                    if (platforms.isEmpty()) {
                        return "暂无启用的采集平台，请联系管理员在「采集平台管理」页添加";
                    }
                    var sb = new StringBuilder("启用中的采集平台：\n");
                    for (var pl : platforms) {
                        sb.append("- ID=").append(pl.getId()).append("  ").append(pl.getPlatformName())
                                .append("（认证方式 ").append(pl.getAuthType()).append("，单次最多 ")
                                .append(pl.getMaxPages()).append(" 页）\n");
                    }
                    return sb.toString();
                })
                .build());
    }

    // ---------- 设计 ----------

    private void registerDesignTools() {
        tool(OpsTool.builder().name("design.schemeList").module("设计管理").title("查询设计方案")
                .desc("按项目/状态查询设计方案列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").resolve("project").build(),
                        OpsParam.builder().name("status").type("string").desc("方案状态").enums(List.of("草稿", "已发布", "已归档")).build()))
                .executor((p, u) -> {
                    Project project = p.containsKey("projectName") ? resolveProject(str(p, "projectName")) : null;
                    PageResult<?> page = designService.listSchemes(1, 10,
                            project == null ? null : project.getId(),
                            p.containsKey("status") ? designStatus(str(p, "status")) : null);
                    return renderPage(page, "scheme", cols("schemeId=ID", "title=方案标题", "versionNo=版本",
                            "genType=来源", "status=状态", "releaseTime=发布时间"));
                })
                .build());
    }

    // ---------- 生产 ----------

    private void registerWorkshopTools() {
        tool(OpsTool.builder().name("production.woList").module("生产管理").title("查询生产工单")
                .desc("按项目/状态查询生产工单列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").resolve("project").build(),
                        OpsParam.builder().name("status").type("string").desc("工单状态").enums(List.of("草稿", "已开工", "已完工")).build()))
                .executor((p, u) -> {
                    Project project = p.containsKey("projectName") ? resolveProject(str(p, "projectName")) : null;
                    PageResult<?> page = workshopService.listWorkOrders(1, 10,
                            project == null ? null : project.getId(),
                            p.containsKey("status") ? woStatus(str(p, "status")) : null);
                    return renderPage(page, "workorder", cols("woNo=工单号", "orderType=类型", "qty=台数",
                            "status=状态", "startedAt=开工时间", "closedAt=完工时间"));
                })
                .build());

        tool(OpsTool.builder().name("production.kitting").module("生产管理").title("齐套率计算")
                .desc("计算项目生产物料齐套率（开工卡点≥95%）").readOnly(true)
                .params(List.of(OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    Object calc = workshopService.calculateKitting(project.getId());
                    return "**齐套率计算**（项目：" + project.getProjectName() + "）\n\n"
                            + renderCardRaw(calc, cols("kitRate=齐套率", "totalItems=物料总数", "readyItems=已齐套",
                            "missingCount=缺料数", "missingItems=缺料明细"));
                })
                .build());
    }

    // ---------- 交付 ----------

    private void registerDeliveryTools() {
        tool(OpsTool.builder().name("delivery.shipmentList").module("交付管理").title("查询发货单")
                .desc("按项目/状态查询发货单列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").resolve("project").build(),
                        OpsParam.builder().name("status").type("string").desc("发货状态").enums(List.of("草稿", "已发运")).build()))
                .executor((p, u) -> {
                    Project project = p.containsKey("projectName") ? resolveProject(str(p, "projectName")) : null;
                    PageResult<?> page = deliveryService.listShipments(1, 10,
                            project == null ? null : project.getId(),
                            p.containsKey("status") ? shipmentStatus(str(p, "status")) : null);
                    return renderPage(page, "shipment", cols("shipmentNo=发货单号", "batchNo=批次", "qty=数量",
                            "carrier=承运商", "status=状态", "shippedAt=发运时间"));
                })
                .build());

        tool(OpsTool.builder().name("delivery.progress").module("交付管理").title("交付进度")
                .desc("查询项目累计交付工程量进度（百分比）").readOnly(true)
                .params(List.of(OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    BigDecimal progress = deliveryService.currentProgress(project.getId());
                    return "项目「" + project.getProjectName() + "」累计交付进度：" + progress + "%";
                })
                .build());

        tool(OpsTool.builder().name("delivery.acceptanceList").module("交付管理").title("查询验收单")
                .desc("按项目查询阶段验收单列表").readOnly(true)
                .params(List.of(OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    return renderList(deliveryService.listAcceptances(project.getId(), null, null),
                            "acceptance", cols("stage=验收阶段", "progressPct=进度%", "status=状态",
                                    "ownerSign=业主签字", "supervisorSign=监理签字", "clientSign=客户签字"), true);
                })
                .build());
    }

    // ---------- 变更 ----------

    private void registerEcnTools() {
        tool(OpsTool.builder().name("ecn.list").module("变更管理").title("查询变更单")
                .desc("按项目/状态查询 ECN 变更单列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").resolve("project").build(),
                        OpsParam.builder().name("status").type("string").desc("变更状态")
                                .enums(List.of("草稿", "待审批", "已批准", "已驳回", "已实施", "已关闭")).build()))
                .executor((p, u) -> {
                    Project project = p.containsKey("projectName") ? resolveProject(str(p, "projectName")) : null;
                    PageResult<EcnChange> page = ecnService.listEcns(1, 10,
                            project == null ? null : project.getId(),
                            p.containsKey("status") ? ecnStatus(str(p, "status")) : null);
                    return renderPage(page, "ecn", cols("ecnNo=编号", "title=变更标题", "changeType=类型",
                            "impactLevel=影响等级", "status=状态", "applicant=申请人"));
                })
                .build());

        tool(OpsTool.builder().name("ecn.approve").module("变更管理").title("ECN 审批")
                .desc("审批通过/驳回变更单（仅部门领导，需用户确认后执行）").readOnly(false)
                .minRole("ROLE_DEPT_LEADER")
                .params(List.of(
                        OpsParam.builder().name("ecnId").type("number").required(true).desc("变更单ID").build(),
                        OpsParam.builder().name("approved").type("bool").required(true).desc("是否通过（true通过/false驳回）").build(),
                        OpsParam.builder().name("remark").type("string").desc("审批意见").build()))
                .executor((p, u) -> {
                    boolean approved = Boolean.TRUE.equals(p.get("approved"))
                            || "true".equalsIgnoreCase(String.valueOf(p.get("approved")));
                    ecnService.approveEcn(num(p, "ecnId").longValue(), u.getUsername(), approved, str(p, "remark"));
                    return "变更单 " + num(p, "ecnId") + " 已" + (approved ? "批准" : "驳回");
                })
                .build());
    }

    // ---------- 成本 ----------

    private void registerCostTools() {
        tool(OpsTool.builder().name("cost.ledger").module("成本管理").title("查询成本台账")
                .desc("查询项目成本台账明细与合计").readOnly(true)
                .params(List.of(OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    List<?> ledger = costService.listLedger(project.getId());
                    StringBuilder sb = new StringBuilder(renderList(ledger, "cost",
                            cols("costType=类型", "sourceType=来源", "amount=金额(元)", "occurredDate=发生日期", "remark=备注"), true));
                    BigDecimal total = ledger.stream()
                            .map(item -> toMap(item))
                            .map(m -> m.get("amount") instanceof Number n ? n : null)
                            .filter(java.util.Objects::nonNull)
                            .map(Number::doubleValue)
                            .map(BigDecimal::valueOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    sb.append("\n**合计成本：").append(total).append(" 元**（项目：").append(project.getProjectName()).append("）");
                    return sb.toString();
                })
                .build());

        tool(OpsTool.builder().name("cost.settle").module("成本管理").title("完工成本核算")
                .desc("执行项目完工核算并生成核算单（毛利三口径，需用户确认后执行）").readOnly(false)
                .minRole("ROLE_DEPT_LEADER")
                .params(List.of(OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    Map<String, Object> result = costService.settleProject(project.getId());
                    return "**完工核算完成**（项目：" + project.getProjectName() + "）\n\n" + renderCardRaw(result,
                            cols("totalCost=总成本(元)", "contractAmount=合同额(元)", "grossProfit=毛利(元)",
                                    "grossMargin=毛利率", "settleNo=核算单号"));
                })
                .build());
    }

    // ---------- 售后 ----------

    private void registerAftermarketTools() {
        tool(OpsTool.builder().name("aftermarket.repairList").module("售后服务").title("查询报修单")
                .desc("按级别/状态/项目查询报修单列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("level").type("string").desc("报修级别").enums(List.of("紧急", "一般", "咨询")).build(),
                        OpsParam.builder().name("status").type("string").desc("处理状态").enums(List.of("待处理", "处理中", "已闭环")).build(),
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").resolve("project").build()))
                .executor((p, u) -> {
                    Project project = p.containsKey("projectName") ? resolveProject(str(p, "projectName")) : null;
                    PageResult<RepairTicket> page = aftermarketService.listRepairs(1, 10,
                            project == null ? null : project.getId(),
                            p.containsKey("status") ? repairStatus(str(p, "status")) : null,
                            p.containsKey("level") ? repairLevel(str(p, "level")) : null);
                    return renderPage(page, "repair", cols("ticketNo=工单号", "faultDesc=故障描述", "level=级别",
                            "status=状态", "reporter=报修人", "handler=处理人", "faultTime=故障时间"));
                })
                .build());

        tool(OpsTool.builder().name("aftermarket.repairCreate").module("售后服务").title("报修受理")
                .desc("登记故障报修（AI 自动分级，需用户确认后执行）").readOnly(false)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").required(true).desc("项目名称").resolve("project").build(),
                        OpsParam.builder().name("faultDesc").type("string").required(true).desc("故障描述").build()))
                .executor((p, u) -> {
                    Project project = resolveProject(str(p, "projectName"));
                    Long id = aftermarketService.createRepair(project.getId(), str(p, "faultDesc"), u.getUsername());
                    return "报修单已受理（ID=" + id + "，项目：" + project.getProjectName() + "），系统已自动分级并生成处理建议";
                })
                // H2 整改：写入前置校验——确认前先核对项目存在
                .precheck((p, u) -> {
                    try {
                        resolveProject(str(p, "projectName"));
                        return null;
                    } catch (BusinessException e) {
                        return e.getMessage();
                    }
                })
                .build());
    }

    // ---------- 档案 ----------

    private void registerArchiveTools() {
        tool(OpsTool.builder().name("archive.list").module("档案管理").title("查询档案")
                .desc("按项目/状态/关键词查询档案列表").readOnly(true)
                .params(List.of(
                        OpsParam.builder().name("projectName").type("string").desc("项目名称").build(),
                        OpsParam.builder().name("status").type("string").desc("档案状态").enums(List.of("正常", "借出", "已销毁")).build(),
                        OpsParam.builder().name("keyword").type("string").desc("标题关键词").build()))
                .executor((p, u) -> {
                    PageResult<ArchiveInfo> page = archiveService.listArchives(1, 10, null,
                            str(p, "projectName"), p.containsKey("status") ? archiveStatus(str(p, "status")) : null);
                    String rendered = renderPage(page, "archive", cols("archiveNo=档号", "title=题名",
                            "archiveType=类型", "medium=介质", "location=存放位置", "status=状态"));
                    if (p.containsKey("keyword")) {
                        rendered = filterLines(rendered, str(p, "keyword"));
                    }
                    return rendered;
                })
                .build());

        tool(OpsTool.builder().name("archive.borrow").module("档案管理").title("档案借阅")
                .desc("登记档案借阅（电子档案不可实物借出，需用户确认后执行）").readOnly(false)
                .params(List.of(
                        OpsParam.builder().name("archiveId").type("number").required(true).desc("档案ID").build(),
                        OpsParam.builder().name("purpose").type("string").required(true).desc("借阅用途").build(),
                        OpsParam.builder().name("dueDays").type("number").desc("借阅天数（默认7天）").build()))
                .executor((p, u) -> {
                    int dueDays = p.containsKey("dueDays") ? num(p, "dueDays").intValue() : 7;
                    Long borrowId = archiveService.borrowArchive(num(p, "archiveId").longValue(),
                            u.getUsername(), str(p, "purpose"), dueDays, u.getUsername());
                    return "借阅登记成功（借阅单 ID=" + borrowId + "，借期 " + dueDays + " 天，逾期将标记超期）";
                })
                // H2 整改：写入前置校验——确认前核对档案可借状态（存在/未借出/非电子载体）
                .precheck((p, u) -> {
                    try {
                        archiveService.preAssertBorrowable(num(p, "archiveId").longValue());
                        return null;
                    } catch (BusinessException e) {
                        return e.getMessage();
                    }
                })
                .build());

        tool(OpsTool.builder().name("archive.return").module("档案管理").title("档案归还")
                .desc("归还已借出档案（需用户确认后执行）").readOnly(false)
                .params(List.of(OpsParam.builder().name("borrowId").type("number").required(true).desc("借阅单ID").build()))
                .executor((p, u) -> {
                    archiveService.returnArchive(num(p, "borrowId").longValue());
                    return "借阅单 " + num(p, "borrowId") + " 已归还登记";
                })
                .build());

        tool(OpsTool.builder().name("archive.overdue").module("档案管理").title("逾期借阅查询")
                .desc("查询逾期未归还的档案借阅").readOnly(true).params(List.of())
                .executor((p, u) -> renderList(archiveService.overdueBorrows(), "borrow",
                        cols("archiveId=档案ID", "borrower=借阅人", "purpose=用途", "dueDate=应还日期", "status=状态"), true))
                .build());
    }

    // ---------- 协同 ----------

    private void registerCollabTools() {
        tool(OpsTool.builder().name("event.myPending").module("事件审批").title("我的待审批事件")
                .desc("查询当前用户待处理的事件审批").readOnly(true).params(List.of())
                .executor((p, u) -> renderList(eventService.myPending(u.getUserId()), "event",
                        cols("title=事件标题", "eventType=类型", "status=状态", "creator=发起人", "createTime=发起时间"), true))
                .build());

        tool(OpsTool.builder().name("event.handle").module("事件审批").title("处理事件审批")
                .desc("处理事件（受理/驳回/处理/完成/关闭，需用户确认后执行）").readOnly(false)
                .params(List.of(
                        OpsParam.builder().name("eventId").type("number").required(true).desc("事件ID").build(),
                        OpsParam.builder().name("handleType").type("string").required(true).desc("处理类型")
                                .enums(List.of("受理", "驳回", "处理", "完成", "关闭")).build(),
                        OpsParam.builder().name("handleDesc").type("string").desc("处理描述").build()))
                .executor((p, u) -> {
                    EventHandleDTO dto = new EventHandleDTO();
                    dto.setHandleType(str(p, "handleType"));
                    dto.setHandleDesc(str(p, "handleDesc"));
                    eventService.handle(num(p, "eventId").longValue(), dto);
                    return "事件 " + num(p, "eventId") + " 已执行「" + str(p, "handleType") + "」";
                })
                // H2 整改：写入前置校验——确认前核对事件存在
                .precheck((p, u) -> {
                    try {
                        eventService.detail(num(p, "eventId").longValue());
                        return null;
                    } catch (BusinessException e) {
                        return "事件 " + p.get("eventId") + " 不存在，请先查询待审批事件核对事件ID";
                    }
                })
                .build());

        tool(OpsTool.builder().name("kb.search").module("知识库").title("知识库检索")
                .desc("按关键词检索知识库（含溯源引用）").readOnly(true)
                .params(List.of(OpsParam.builder().name("query").type("string").required(true).desc("检索问题").build()))
                .executor((p, u) -> {
                    List<KbSearchHit> hits = kbService.search(str(p, "query"), 5);
                    if (hits.isEmpty()) {
                        return "知识库未检索到相关内容。";
                    }
                    StringBuilder sb = new StringBuilder("**知识库检索结果（Top " + hits.size() + "）**\n\n");
                    int i = 1;
                    for (KbSearchHit hit : hits) {
                        sb.append(i++).append(". **").append(nvl(hit.getDocumentTitle())).append("**（相关度 ")
                                .append(String.format("%.2f", hit.getScore())).append("，文档ID=").append(hit.getDocumentId()).append("）\n");
                        String content = hit.getContent() == null ? "" : hit.getContent().replaceAll("\n+", " ");
                        sb.append("   ").append(content.length() > 150 ? content.substring(0, 150) + "…" : content).append("\n\n");
                    }
                    return sb.toString();
                })
                .build());
    }

    // ==================== 参数解析辅助 ====================

    // ==================== 实体模糊匹配（P0-4 参数语义校验） ====================

    /** 实体匹配结果：entity 唯一命中；candidates 非空且 entity 为 null 表示歧义（需用户澄清） */
    public record EntityMatch<T>(T entity, List<String> candidates) {

        /** 未匹配到任何实体 */
        public static <T> EntityMatch<T> none() {
            return new EntityMatch<>(null, List.of());
        }

        /** 匹配到多个实体（候选清单最多 5 条） */
        public static <T> EntityMatch<T> ambiguous(List<String> candidates) {
            return new EntityMatch<>(null, candidates);
        }

        /** 唯一命中 */
        public static <T> EntityMatch<T> unique(T entity) {
            return new EntityMatch<>(entity, List.of());
        }
    }

    /** 项目名/ID → 实体匹配（ID精确 → like → 去「项目」后缀 → 双向包含；不抛异常，由调用方决定追问策略） */
    public EntityMatch<Project> matchProject(String nameOrId) {
        if (!StringUtils.hasText(nameOrId)) {
            return EntityMatch.none();
        }
        String input = nameOrId.trim();
        List<Project> matched = new ArrayList<>();
        if (input.matches("\\d+")) {
            Project project = projectMapper.selectById(Long.valueOf(input));
            matched = project == null ? List.of() : new ArrayList<>(List.of(project));
        }
        if (matched.isEmpty()) {
            matched = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .like(Project::getProjectName, input).last("LIMIT 11"));
        }
        // 口语化容错：去掉结尾「项目」二字重试（「三一重能项目」→「三一重能」）
        if (matched.isEmpty() && input.endsWith("项目") && input.length() > 4) {
            String trimmed = input.substring(0, input.length() - 2).trim();
            matched = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .like(Project::getProjectName, trimmed).last("LIMIT 11"));
        }
        // 双向包含兜底：用户输入包含库内项目名（如「三一重能的一期项目进展」）
        if (matched.isEmpty() && !input.matches("\\d+")) {
            List<Project> all = projectMapper.selectList(new LambdaQueryWrapper<Project>().last("LIMIT 100"));
            matched = all.stream()
                    .filter(pr -> input.contains(pr.getProjectName())
                            || (pr.getProjectName() != null && pr.getProjectName().contains(input)))
                    .limit(11)
                    .collect(Collectors.toList());
        }
        return toEntityMatch(matched, pr -> pr.getProjectCode() + " " + pr.getProjectName());
    }

    /** 姓名/账号/ID → 成员实体匹配（精确账号/姓名 like → 双向包含；不抛异常） */
    public EntityMatch<SysUser> matchAssignee(String nameOrId) {
        if (!StringUtils.hasText(nameOrId)) {
            return EntityMatch.none();
        }
        String input = nameOrId.trim();
        List<SysUser> matched = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .and(w -> w.eq(SysUser::getUsername, input).or().like(SysUser::getRealName, input))
                .last("LIMIT 11"));
        // 双向包含兜底（如「张三师傅」→「张三」）
        if (matched.isEmpty()) {
            List<SysUser> all = userMapper.selectList(new LambdaQueryWrapper<SysUser>().last("LIMIT 200"));
            matched = all.stream()
                    .filter(usr -> (usr.getRealName() != null && (input.contains(usr.getRealName()) || usr.getRealName().contains(input)))
                            || (usr.getUsername() != null && (input.contains(usr.getUsername()) || usr.getUsername().contains(input))))
                    .limit(11)
                    .collect(Collectors.toList());
        }
        return toEntityMatch(matched, usr -> usr.getUsername() + "(" + nvl(usr.getRealName()) + ")");
    }

    /** 匹配结果收敛：0条=none；多条=ambiguous（候选限5条）；1条=unique */
    private <T> EntityMatch<T> toEntityMatch(List<T> matched, java.util.function.Function<T, String> formatter) {
        if (matched == null || matched.isEmpty()) {
            return EntityMatch.none();
        }
        if (matched.size() > 1) {
            return EntityMatch.ambiguous(matched.stream().limit(5).map(formatter).collect(Collectors.toList()));
        }
        return EntityMatch.unique(matched.get(0));
    }

    /** 项目名/ID → Project（唯一匹配；未找到/歧义抛业务异常，供 executor 内直接使用） */
    private Project resolveProject(String nameOrId) {
        if (!StringUtils.hasText(nameOrId)) {
            throw new BusinessException(400, "请提供项目名称");
        }
        EntityMatch<Project> match = matchProject(nameOrId);
        if (match.entity() == null) {
            if (!match.candidates().isEmpty()) {
                throw new BusinessException(400, "匹配到多个项目，请指明是哪一个：" + String.join("；", match.candidates()));
            }
            throw new BusinessException(400, "未找到项目「" + nameOrId + "」，请确认项目名称（可先说「查一下项目」核对）");
        }
        return match.entity();
    }

    /** 姓名/账号/ID → SysUser（唯一匹配；未找到/歧义抛业务异常） */
    private SysUser resolveAssignee(String nameOrId) {
        if (!StringUtils.hasText(nameOrId)) {
            throw new BusinessException(400, "请提供负责人姓名");
        }
        EntityMatch<SysUser> match = matchAssignee(nameOrId);
        if (match.entity() == null) {
            if (!match.candidates().isEmpty()) {
                throw new BusinessException(400, "匹配到多位成员，请指明账号：" + String.join("；", match.candidates()));
            }
            throw new BusinessException(400, "未找到成员「" + nameOrId + "」，请确认姓名或账号");
        }
        return match.entity();
    }

    private Long resolveContractId(String contractNo) {
        if (!StringUtils.hasText(contractNo)) {
            throw new BusinessException(400, "请提供合同编号或合同ID");
        }
        Contract contract = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                .like(Contract::getContractNo, contractNo.trim()).last("LIMIT 2"));
        if (contract == null) {
            throw new BusinessException(400, "未找到合同「" + contractNo + "」");
        }
        return contract.getId();
    }

    // ==================== 类型转换（声明式校验） ====================

    private String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private Number num(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n;
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "参数 " + key + " 应为数字");
        }
    }

    private BigDecimal bd(Map<String, Object> params, String key) {
        try {
            return new BigDecimal(String.valueOf(params.get(key)));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "参数 " + key + " 应为数字金额");
        }
    }

    private LocalDate date(Map<String, Object> params, String key) {
        String v = str(params, key);
        try {
            return LocalDate.parse(v, DATE_FMT);
        } catch (Exception e) {
            throw new BusinessException(400, "参数 " + key + " 应为 yyyy-MM-dd 格式日期");
        }
    }

    // ==================== 枚举映射 ====================

    private Integer taskStatus(String cn) {
        return switch (cn) {
            case "待办" -> 0;
            case "进行中" -> 1;
            case "待审核" -> 2;
            case "已完成" -> 3;
            case "已关闭" -> 4;
            default -> throw new BusinessException(400, "任务状态仅支持：待办/进行中/待审核/已完成/已关闭");
        };
    }

    private Integer taskPriority(String cn) {
        return switch (cn) {
            case "高" -> 1;
            case "中" -> 2;
            case "低" -> 3;
            default -> throw new BusinessException(400, "优先级仅支持：高/中/低");
        };
    }

    private Integer projectStatus(String cn) {
        return switch (cn) {
            case "待启动" -> 0;
            case "进行中" -> 1;
            case "已完成" -> 2;
            case "已关闭" -> 3;
            case "暂停" -> 4;
            default -> null;
        };
    }

    private Integer projectType(String cn) {
        return switch (cn) {
            case "整机厂" -> 1;
            case "技改" -> 2;
            case "研发" -> 3;
            default -> throw new BusinessException(400, "项目类型仅支持：整机厂/技改/研发");
        };
    }

    private String contractStatus(String cn) {
        return switch (cn) {
            case "草稿" -> "DRAFT";
            case "会签" -> "COUNTERSIGN";
            case "审批" -> "APPROVING";
            case "用印" -> "SEALING";
            case "生效" -> "EFFECTIVE";
            case "关闭" -> "CLOSED";
            case "终止" -> "TERMINATED";
            default -> throw new BusinessException(400, "合同状态仅支持：草稿/会签/审批/用印/生效/关闭/终止");
        };
    }

    private String tenderStatus(String cn) {
        return switch (cn) {
            case "新标讯" -> "NEW";
            case "跟踪中" -> "FOLLOWED";
            case "已投标" -> "APPLIED";
            case "备标中" -> "PREPARED";
            case "已定标" -> "DECIDED";
            case "已中标" -> "WON";
            case "未中标" -> "LOST";
            default -> throw new BusinessException(400, "标讯状态仅支持：新标讯/跟踪中/已投标/备标中/已定标/已中标/未中标");
        };
    }

    private String designStatus(String cn) {
        return switch (cn) {
            case "草稿" -> "DRAFT";
            case "已发布" -> "RELEASED";
            case "已归档" -> "ARCHIVED";
            default -> null;
        };
    }

    private String woStatus(String cn) {
        return switch (cn) {
            case "草稿" -> "DRAFT";
            case "已开工" -> "STARTED";
            case "已完工" -> "CLOSED";
            default -> null;
        };
    }

    private String shipmentStatus(String cn) {
        return switch (cn) {
            case "草稿" -> "DRAFT";
            case "已发运" -> "SUBMITTED";
            default -> null;
        };
    }

    private String ecnStatus(String cn) {
        return switch (cn) {
            case "草稿" -> "DRAFT";
            case "待审批" -> "PENDING_APPROVAL";
            case "已批准" -> "APPROVED";
            case "已驳回" -> "REJECTED";
            case "已实施" -> "IMPLEMENTED";
            case "已关闭" -> "CLOSED";
            default -> null;
        };
    }

    private String repairStatus(String cn) {
        return switch (cn) {
            case "待处理" -> "OPEN";
            case "处理中" -> "PROCESSING";
            case "已闭环" -> "CLOSED";
            default -> null;
        };
    }

    private String repairLevel(String cn) {
        return switch (cn) {
            case "紧急" -> "EMERGENCY";
            case "一般" -> "GENERAL";
            case "咨询" -> "INQUIRY";
            default -> null;
        };
    }

    private String archiveStatus(String cn) {
        return switch (cn) {
            case "正常" -> "NORMAL";
            case "借出" -> "BORROWED";
            case "已销毁" -> "DESTROYED";
            default -> null;
        };
    }

    // ==================== 渲染辅助（贴合系统数据规范） ====================

    private Map<String, String> cols(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    /** 状态枚举中文翻译（键：工具域.字段.值） */
    private String cn(String domainField, Object value) {
        String v = String.valueOf(value);
        return switch (domainField + "." + v) {
            case "task.status.0" -> "待办";
            case "task.status.1" -> "进行中";
            case "task.status.2" -> "待审核";
            case "task.status.3" -> "已完成";
            case "task.status.4" -> "已关闭";
            case "task.priority.1" -> "高";
            case "task.priority.2" -> "中";
            case "task.priority.3" -> "低";
            case "project.projectType.1" -> "整机厂";
            case "project.projectType.2" -> "技改";
            case "project.projectType.3" -> "研发";
            case "project.status.0" -> "待启动";
            case "project.status.1" -> "进行中";
            case "project.status.2" -> "已完成";
            case "project.status.3" -> "已关闭";
            case "project.status.4" -> "暂停";
            default -> v;
        };
    }

    /** 渲染 PageResult 为 Markdown 表格（最多10条 + 总数） */
    private String renderPage(PageResult<?> page, String domain, Map<String, String> cols) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return "未查询到符合条件的记录。";
        }
        String table = renderList(page.getRecords(), domain, cols, false);
        return table + "\n（共 " + page.getTotal() + " 条，最多显示 10 条，可用更精确的条件筛选）";
    }

    /** 渲染实体列表为 Markdown 表格 */
    private String renderList(List<?> list, String domain, Map<String, String> cols, boolean emptyTip) {
        if (list == null || list.isEmpty()) {
            return emptyTip ? "当前没有符合条件的记录。" : "未查询到符合条件的记录。";
        }
        List<Map<String, Object>> rows = list.stream().map(this::toMap).collect(Collectors.toList());
        return renderRows(rows, domain, cols);
    }

    /** 渲染单实体为 Markdown 字段卡 */
    private String renderCard(Object entity, String domain, Map<String, String> cols) {
        if (entity == null) {
            return "未查询到该记录（可能已被删除或不存在）。";
        }
        Map<String, Object> map = toMap(entity);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> col : cols.entrySet()) {
            Object v = map.get(col.getKey());
            sb.append("- ").append(col.getValue()).append("：")
                    .append(v == null ? "—" : translate(domain, col.getKey(), v)).append('\n');
        }
        return sb.toString();
    }

    /** 渲染 Map 结果为字段卡（不经过实体转换） */
    private String renderCardRaw(Object result, Map<String, String> cols) {
        if (result == null) {
            return "执行完成，未返回明细。";
        }
        Map<String, Object> map = result instanceof Map<?, ?> m ? (Map<String, Object>) m : toMap(result);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> col : cols.entrySet()) {
            if (map.containsKey(col.getKey())) {
                sb.append("- ").append(col.getValue()).append("：").append(nvl(String.valueOf(map.get(col.getKey())))).append('\n');
            }
        }
        // 兜底：声明列之外的键也输出，避免信息丢失
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!cols.containsKey(e.getKey()) && !"class".equals(e.getKey())) {
                sb.append("- ").append(e.getKey()).append("：").append(brief(e.getValue())).append('\n');
            }
        }
        return sb.toString();
    }

    private String renderRows(List<Map<String, Object>> rows, String domain, Map<String, String> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append("| # |");
        for (String title : cols.values()) {
            sb.append(' ').append(title).append(" |");
        }
        sb.append('\n').append("|---|");
        sb.append("---|".repeat(cols.size())).append('\n');
        int i = 1;
        for (Map<String, Object> row : rows) {
            sb.append("| ").append(i++).append(" |");
            for (String field : cols.keySet()) {
                Object v = row.get(field);
                sb.append(' ').append(v == null ? "—" : translate(domain, field, v)).append(" |");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 单元格翻译：任务/项目域按 cn()；其他域英文枚举就地输出 */
    private String translate(String domain, String field, Object v) {
        String s = String.valueOf(v);
        if ("task".equals(domain) && ("status".equals(field) || "priority".equals(field))) {
            return cn(domain + "." + field, v);
        }
        if ("project".equals(domain) && ("status".equals(field) || "projectType".equals(field))) {
            return cn(domain + "." + field, v);
        }
        if (s.length() > 40) {
            return s.substring(0, 40) + "…";
        }
        return s.replace("|", "\\|").replace("\n", " ");
    }

    /** 实体/DTO → Map（含 BaseEntity 字段） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object entity) {
        try {
            return objectMapper.convertValue(entity, Map.class);
        } catch (Exception e) {
            log.warn("实体转 Map 失败: {}", entity == null ? "null" : entity.getClass().getSimpleName());
            return Map.of();
        }
    }

    /** 项目名称补充列（任务列表透出项目名，减少用户二次查询） */
    private void appendField(StringBuilder sb, Map<String, Object> map, String key, String title) {
        Object v = map.get(key);
        sb.append("- ").append(title).append("：").append(v == null ? "—" : brief(v)).append('\n');
    }

    private String brief(Object v) {
        String s = String.valueOf(v);
        return s.length() > 100 ? s.substring(0, 100) + "…" : s.replace("\n", " ");
    }

    private String nvl(String s) {
        return StringUtils.hasText(s) ? s : "—";
    }

    /** 按关键词过滤已渲染表格的行（archive.list keyword 兜底过滤） */
    private String filterLines(String rendered, String keyword) {
        List<String> kept = new ArrayList<>();
        for (String line : rendered.split("\n")) {
            if (line.startsWith("|") || !line.contains("|") || line.contains(keyword)) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }
}
