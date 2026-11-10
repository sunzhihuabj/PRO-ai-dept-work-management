package com.dept.mgmt.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.contract.entity.ContractApproval;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.ContractApprovalMapper;
import com.dept.mgmt.mapper.ContractMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    private final CodeEngineService codeEngineService;
    private final EventBusService eventBusService;

    /**
     * 创建合同草稿（编号由 T-101 引擎 HT 规则生成）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Contract dto) {
        validateRatios(dto);
        LoginUser user = LoginUser.current();
        Contract contract = new Contract();
        contract.setContractNo(codeEngineService.nextCode("HT", null));
        contract.setName(dto.getName());
        contract.setCustomerName(dto.getCustomerName());
        contract.setAmount(dto.getAmount());
        contract.setSignDate(dto.getSignDate());
        contract.setWarrantyMonths(dto.getWarrantyMonths() == null ? 24 : dto.getWarrantyMonths());
        contract.setPayRatioPrepay(dto.getPayRatioPrepay());
        contract.setPayRatioDelivery(dto.getPayRatioDelivery());
        contract.setPayRatioAccept(dto.getPayRatioAccept());
        contract.setPayRatioWarranty(dto.getPayRatioWarranty());
        contract.setRemark(dto.getRemark());
        contract.setStatus("DRAFT");
        contractMapper.insert(contract);
        log.info("合同草稿创建: {} by user:{}", contract.getContractNo(), user == null ? "?" : user.getUserId());
        return contract.getId();
    }

    /**
     * 合同分页
     */
    public PageResult<Contract> list(int pageNum, int pageSize, String name, String status) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Contract::getName, name);
        wrapper.eq(StringUtils.hasText(status), Contract::getStatus, status);
        wrapper.orderByDesc(Contract::getCreateTime);
        return PageResult.of(contractMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 合同详情（含审批记录）
     */
    public Map<String, Object> detail(Long id) {
        Contract contract = mustExist(id);
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
     * 关闭/终止合同
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
