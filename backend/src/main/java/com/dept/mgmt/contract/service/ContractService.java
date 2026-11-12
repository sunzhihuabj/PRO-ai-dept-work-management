package com.dept.mgmt.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.contract.entity.ContractApproval;
import com.dept.mgmt.contract.entity.ContractProject;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.ContractApprovalMapper;
import com.dept.mgmt.mapper.ContractMapper;
import com.dept.mgmt.mapper.ContractProjectMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * W2 合同域服务（T-111）
 * 审批流（状态机实现，Flowable 预留）：
 * DRAFT --提交会签--> COUNTERSIGN --会签通过--> APPROVING --审批通过--> SEALING --用印登记+G2--> EFFECTIVE
 * 决策门：G1 = 四类收款比例之和必须为 100（提交会签时校验）；G2 = 用印登记（合同实体签署完成）后才可生效
 * 生效时经事件总线发布 contract.effective（触发下游：预付款提醒 T-113 / 设计域建档）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractMapper contractMapper;
    private final ContractApprovalMapper approvalMapper;
    private final ContractProjectMapper contractProjectMapper;
    private final CodeEngineService codeEngineService;
    private final EventBusService eventBusService;
    /**
     * 跨域只读例外（T-406 披露）：项目存在性校验与关联展示需实时项目名称/状态，
     * Phase 1 以只读 selectById/selectBatchIds 实现，未做任何写操作；
     * Phase 2 关联能力独立建域后改为 RPC/事件投影
     */
    private final ProjectMapper projectMapper;
    private final com.dept.mgmt.fineperm.service.FinePermCacheService finePermCacheService;

    /**
     * 创建合同草稿（建档，T-407）。
     * 编号规则：用户/AI 提供了 contractNo 则校验唯一后采用，否则由 T-101 引擎 HT 规则生成；
     * 核心字段（甲乙方/付款/交货/联系人/验收/摘要）支持 AI 预填 + 人工校验编辑后保存
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Contract dto) {
        validateRatios(dto);
        LoginUser user = LoginUser.current();
        Contract contract = new Contract();
        String contractNo = StringUtils.hasText(dto.getContractNo()) ? dto.getContractNo().trim() : null;
        if (contractNo != null) {
            Long dup = contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getContractNo, contractNo));
            if (dup != null && dup > 0) {
                throw new BusinessException(400, "合同编号已存在: " + contractNo
                        + "（AI 提取编号与台账冲突，请人工核对修改后保存）");
            }
        } else {
            contractNo = codeEngineService.nextCode("HT", null);
        }
        contract.setContractNo(contractNo);
        contract.setName(dto.getName());
        contract.setCustomerName(dto.getCustomerName());
        contract.setPartyB(dto.getPartyB());
        contract.setAmount(dto.getAmount());
        contract.setSignDate(dto.getSignDate());
        contract.setWarrantyMonths(dto.getWarrantyMonths() == null ? 24 : dto.getWarrantyMonths());
        contract.setPayRatioPrepay(dto.getPayRatioPrepay());
        contract.setPayRatioDelivery(dto.getPayRatioDelivery());
        contract.setPayRatioAccept(dto.getPayRatioAccept());
        contract.setPayRatioWarranty(dto.getPayRatioWarranty());
        // 建档扩展字段（T-407）
        contract.setPaymentTerms(dto.getPaymentTerms());
        contract.setPaymentMethod(dto.getPaymentMethod());
        contract.setDeliveryTime(dto.getDeliveryTime());
        contract.setDeliveryPlace(dto.getDeliveryPlace());
        contract.setContactInfo(dto.getContactInfo());
        contract.setAcceptanceCondition(dto.getAcceptanceCondition());
        contract.setSummary(dto.getSummary());
        contract.setFilingMainFileId(dto.getFilingMainFileId());
        contract.setRemark(dto.getRemark());
        contract.setStatus("DRAFT");
        contractMapper.insert(contract);
        log.info("合同建档完成: {} by user:{}", contract.getContractNo(), user == null ? "?" : user.getUserId());
        return contract.getId();
    }

    /**
     * 合同分页（列表项附实时关联项目数 + 敏感字段按权限脱敏）
     */
    public PageResult<Contract> list(int pageNum, int pageSize, String name, String status) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Contract::getName, name);
        wrapper.eq(StringUtils.hasText(status), Contract::getStatus, status);
        wrapper.orderByDesc(Contract::getCreateTime);
        Page<Contract> page = contractMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillProjectCount(page.getRecords());
        page.getRecords().forEach(this::maskSensitive);
        return PageResult.of(page);
    }

    /**
     * 合同详情（含审批记录；敏感字段按权限脱敏）
     */
    public Map<String, Object> detail(Long id) {
        Contract contract = mustExist(id);
        maskSensitive(contract);
        List<ContractApproval> approvals = approvalMapper.selectList(new LambdaQueryWrapper<ContractApproval>()
                .eq(ContractApproval::getContractId, id)
                .orderByAsc(ContractApproval::getCreateTime));
        return Map.of("contract", contract, "approvals", approvals);
    }

    /**
     * 提交会签（DRAFT → COUNTERSIGN）。决策门 G1：收款比例之和 = 100
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitCountersign(Long id) {
        Contract contract = mustExist(id);
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new BusinessException(400, "仅草稿状态可提交会签，当前: " + contract.getStatus());
        }
        validateRatios(contract);
        contract.setStatus("COUNTERSIGN");
        contractMapper.updateById(contract);
    }

    /**
     * 会签（COUNTERSIGN 节点）。通过 → APPROVING；驳回 → DRAFT
     */
    @Transactional(rollbackFor = Exception.class)
    public void countersign(Long id, boolean approved, String comment) {
        Contract contract = mustExist(id);
        requireStatus(contract, "COUNTERSIGN", "会签");
        record(id, "COUNTERSIGN", approved, comment);
        contract.setStatus(approved ? "APPROVING" : "DRAFT");
        contractMapper.updateById(contract);
    }

    /**
     * 审批（APPROVING 节点）。通过 → SEALING；驳回 → DRAFT
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, boolean approved, String comment) {
        Contract contract = mustExist(id);
        requireStatus(contract, "APPROVING", "审批");
        record(id, "APPROVE", approved, comment);
        contract.setStatus(approved ? "SEALING" : "DRAFT");
        contractMapper.updateById(contract);
    }

    /**
     * 用印登记（SEALING 节点，决策门 G2：用印完成 = 合同实体签署完成）。
     * 通过 → EFFECTIVE，并发布 contract.effective 事件（跨域触发预付款提醒/设计域建档）
     */
    @Transactional(rollbackFor = Exception.class)
    public void seal(Long id, String comment) {
        Contract contract = mustExist(id);
        requireStatus(contract, "SEALING", "用印");
        record(id, "SEAL", true, comment);
        contract.setStatus("EFFECTIVE");
        contract.setEffectiveDate(LocalDate.now());
        contractMapper.updateById(contract);
        // 跨域联动走事件总线：W2 → 预付款收款提醒（T-113）/ 设计域（T-115）
        eventBusService.publish(DomainEventType.CONTRACT_EFFECTIVE, Map.of(
                "contractId", contract.getId(),
                "contractNo", contract.getContractNo(),
                "name", contract.getName(),
                "customerName", contract.getCustomerName(),
                "amount", contract.getAmount(),
                "payRatioPrepay", contract.getPayRatioPrepay()));
        log.info("合同生效并发布事件: {}", contract.getContractNo());
    }

    /**
     * 关闭/终止合同（发布 contract.closed 事件 → 关联项目侧状态联动通知）
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, boolean terminated) {
        Contract contract = mustExist(id);
        String target = terminated ? "TERMINATED" : "CLOSED";
        if (!List.of("DRAFT", "EFFECTIVE").contains(contract.getStatus())) {
            throw new BusinessException(400, "当前状态不可关闭/终止: " + contract.getStatus());
        }
        contract.setStatus(target);
        contractMapper.updateById(contract);
        // 跨域联动（T-406）：通知关联项目负责人合同已关闭/终止
        eventBusService.publish(DomainEventType.CONTRACT_CLOSED, Map.of(
                "contractId", contract.getId(),
                "contractNo", contract.getContractNo(),
                "name", contract.getName(),
                "terminated", terminated));
        log.info("合同关闭/终止并发布事件: {} -> {}", contract.getContractNo(), target);
    }

    // ===== 合同-项目关联（T-406）=====

    /**
     * 合同关联的项目列表（实时 join 项目表，无快照脏数据）
     */
    public List<Map<String, Object>> listProjects(Long contractId) {
        mustExist(contractId);
        List<ContractProject> links = contractProjectMapper.selectList(
                new LambdaQueryWrapper<ContractProject>()
                        .eq(ContractProject::getContractId, contractId)
                        .orderByDesc(ContractProject::getCreateTime));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = links.stream().map(ContractProject::getProjectId).toList();
        Map<Long, Project> projectMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        return links.stream().map(link -> {
            Project project = projectMap.get(link.getProjectId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", link.getProjectId());
            item.put("projectName", project == null ? "（项目已删除）" : project.getProjectName());
            item.put("projectCode", project == null ? null : project.getProjectCode());
            item.put("projectType", project == null ? null : project.getProjectType());
            item.put("projectStatus", project == null ? null : project.getStatus());
            item.put("projectLeader", project == null ? null : project.getProjectLeader());
            item.put("bindBy", link.getBindBy());
            item.put("bindTime", link.getCreateTime());
            return item;
        }).toList();
    }

    /**
     * 绑定项目：仅已生效（EFFECTIVE）合同允许建立关联；重复绑定拒绝（唯一约束兜底）
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long contractId, Long projectId) {
        Contract contract = mustExist(contractId);
        if (!"EFFECTIVE".equals(contract.getStatus())) {
            throw new BusinessException(400, "仅已生效合同可关联项目，当前状态: " + statusLabel(contract.getStatus()));
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "关联项目不存在（可能已被删除），请刷新后重选");
        }
        Long exists = contractProjectMapper.selectCount(new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getContractId, contractId)
                .eq(ContractProject::getProjectId, projectId));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "该合同已关联此项目，请勿重复绑定");
        }
        LoginUser user = LoginUser.current();
        ContractProject link = new ContractProject();
        link.setContractId(contractId);
        link.setProjectId(projectId);
        link.setBindBy(user == null ? "system" : user.getUsername());
        contractProjectMapper.insert(link);
        log.info("合同绑定项目: contractId={}, projectId={}, bindBy={}",
                contractId, projectId, link.getBindBy());
    }

    /**
     * 解除绑定：任何状态允许（解绑不产生数据破坏，仅断开关联）
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long contractId, Long projectId) {
        ContractProject link = contractProjectMapper.selectOne(new LambdaQueryWrapper<ContractProject>()
                .eq(ContractProject::getContractId, contractId)
                .eq(ContractProject::getProjectId, projectId));
        if (link == null) {
            throw new BusinessException(404, "关联关系不存在（可能已被解除），请刷新列表");
        }
        contractProjectMapper.deleteById(link.getId());
        log.info("合同解除项目绑定: contractId={}, projectId={}", contractId, projectId);
    }

    /**
     * 项目关联的合同列表（项目侧「关联合同」卡片数据源，实时 join 合同表）
     */
    public List<Map<String, Object>> listByProject(Long projectId) {
        List<ContractProject> links = contractProjectMapper.selectList(
                new LambdaQueryWrapper<ContractProject>()
                        .eq(ContractProject::getProjectId, projectId)
                        .orderByDesc(ContractProject::getCreateTime));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> contractIds = links.stream().map(ContractProject::getContractId).toList();
        Map<Long, Contract> contractMap = contractMapper.selectBatchIds(contractIds).stream()
                .collect(Collectors.toMap(Contract::getId, c -> c));
        return links.stream().map(link -> {
            Contract contract = contractMap.get(link.getContractId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("contractId", link.getContractId());
            item.put("contractNo", contract == null ? null : contract.getContractNo());
            item.put("contractName", contract == null ? "（合同已删除）" : contract.getName());
            item.put("customerName", contract == null ? null : contract.getCustomerName());
            item.put("amount", contract == null ? null : contract.getAmount());
            item.put("contractStatus", contract == null ? null : contract.getStatus());
            item.put("bindBy", link.getBindBy());
            item.put("bindTime", link.getCreateTime());
            return item;
        }).toList();
    }

    // ===== 私有辅助 =====

    private Contract mustExist(Long id) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException(404, "合同不存在");
        }
        return contract;
    }

    private void requireStatus(Contract contract, String expected, String action) {
        if (!expected.equals(contract.getStatus())) {
            throw new BusinessException(400, action + "操作要求状态为 " + expected + "，当前: " + contract.getStatus());
        }
    }

    /** 决策门 G1：四类收款比例之和必须为 100 */
    private void validateRatios(Contract dto) {
        int sum = nvl(dto.getPayRatioPrepay()) + nvl(dto.getPayRatioDelivery())
                + nvl(dto.getPayRatioAccept()) + nvl(dto.getPayRatioWarranty());
        if (sum != 100) {
            throw new BusinessException(400, "四类收款比例之和必须为 100，当前: " + sum);
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "合同金额必须大于 0");
        }
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 敏感字段脱敏（T-407）：金额与联系人信息
     * 规则：部门领导明文；未启用精细权限（灰度三维度均空）不受影响；
     * 已启用精细权限且持有 api:contract:sensitive:view → 明文，否则置空 + amountMasked 标记
     */
    private void maskSensitive(Contract c) {
        if (c == null) {
            return;
        }
        LoginUser user = LoginUser.current();
        if (user == null) {
            return;
        }
        if (user.getRoles() != null && user.getRoles().contains("ROLE_DEPT_LEADER")) {
            return;
        }
        var entry = finePermCacheService.get(user.getUserId());
        if (!entry.finePermEnabled() || entry.grantedPerms().contains("api:contract:sensitive:view")) {
            return;
        }
        c.setAmount(null);
        c.setAmountMasked(true);
        if (StringUtils.hasText(c.getContactInfo())) {
            c.setContactInfo("已脱敏（无敏感字段明文查看权限）");
        }
    }

    /** 列表页批量填充关联项目数（in 批查，避免 N+1） */
    private void fillProjectCount(List<Contract> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return;
        }
        List<Long> ids = contracts.stream().map(Contract::getId).toList();
        Map<Long, Long> counts = contractProjectMapper.selectList(
                        new LambdaQueryWrapper<ContractProject>().in(ContractProject::getContractId, ids))
                .stream()
                .collect(Collectors.groupingBy(ContractProject::getContractId, Collectors.counting()));
        contracts.forEach(c -> c.setProjectCount(counts.getOrDefault(c.getId(), 0L).intValue()));
    }

    /** 状态中文标签（异常提示用） */
    private String statusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "COUNTERSIGN" -> "会签中";
            case "APPROVING" -> "审批中";
            case "SEALING" -> "用印中";
            case "EFFECTIVE" -> "已生效";
            case "CLOSED" -> "已关闭";
            case "TERMINATED" -> "已终止";
            default -> status;
        };
    }

    private void record(Long contractId, String node, boolean approved, String comment) {
        LoginUser user = LoginUser.current();
        ContractApproval approval = new ContractApproval();
        approval.setContractId(contractId);
        approval.setNode(node);
        approval.setApproverId(user == null ? 0L : user.getUserId());
        approval.setApproverName(user == null ? "系统" : user.getUsername());
        approval.setResult(approved ? "APPROVED" : "REJECTED");
        approval.setComment(comment);
        approvalMapper.insert(approval);
    }
}
