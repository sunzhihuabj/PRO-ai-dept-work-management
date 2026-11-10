package com.dept.mgmt.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.agent.entity.AgentCapabilityPolicy;
import com.dept.mgmt.agent.enums.AutonomyLevel;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AgentCapabilityPolicyMapper;
import com.dept.mgmt.mapper.SysCapabilityMapper;
import com.dept.mgmt.platform.identity.entity.SysCapability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 渐进自主引擎（T-110）
 * 四级状态机 + 采纳/修改/驳回三态统计 + 每日自动升降级评估
 *
 * <p>规则（V2 文档缺失，按规划红线自主制定并在汇报中披露）：
 * 升级：采纳率 ≥90% 且累计执行 ≥10 次 → 升一级（上限 AUTO_BATCH；is_never 能力封顶 SUGGESTION）；
 * 降级：错误率 >10% → 降一级并通知部门领导；升降级均落 level_change_note 留痕。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutonomyService {

    /** 升级门槛：采纳率 */
    private static final double PROMOTE_ACCEPT_RATE = 0.90;

    /** 升级门槛：最少执行次数 */
    private static final int PROMOTE_MIN_TOTAL = 10;

    /** 降级门槛：错误率 */
    private static final double DEMOTE_ERROR_RATE = 0.10;

    private final AgentCapabilityPolicyMapper policyMapper;
    private final SysCapabilityMapper capabilityMapper;
    private final com.dept.mgmt.platform.notify.service.NotificationService notificationService;

    /**
     * 查询能力自主级别（无策略时默认建议级；is_never 能力点强制封顶建议级）
     */
    public AutonomyLevel levelOf(String capabilityCode) {
        AgentCapabilityPolicy policy = policyOf(capabilityCode);
        AutonomyLevel level = AutonomyLevel.of(policy == null ? null : policy.getAutonomyLevel());
        // 红线防御：永久禁止类能力点任何情况下不得自动执行
        SysCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getCapabilityCode, capabilityCode));
        if (capability != null && capability.getIsNever() != null && capability.getIsNever() == 1
                && level.getCode() > AutonomyLevel.SUGGESTION.getCode()) {
            return AutonomyLevel.SUGGESTION;
        }
        return level;
    }

    /**
     * 任务结果三态反馈：采纳/修改/驳回（自动级结果的 24h 回滚窗口语义载体）
     */
    @Transactional(rollbackFor = Exception.class)
    public void feedback(String capabilityCode, String feedback) {
        AgentCapabilityPolicy policy = policyOf(capabilityCode);
        if (policy == null) {
            throw new BusinessException(404, "能力策略不存在: " + capabilityCode);
        }
        switch (feedback) {
            case "ACCEPTED" -> policy.setAcceptCount(policy.getAcceptCount() + 1);
            case "MODIFIED" -> policy.setModifyCount(policy.getModifyCount() + 1);
            case "REJECTED" -> policy.setRejectCount(policy.getRejectCount() + 1);
            default -> throw new BusinessException(400, "反馈仅允许 ACCEPTED/MODIFIED/REJECTED");
        }
        policyMapper.updateById(policy);
    }

    /**
     * 记录一次执行（成功计总数；失败计总数+错误数）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordExecution(String capabilityCode, boolean success) {
        AgentCapabilityPolicy policy = policyOf(capabilityCode);
        if (policy == null) {
            policy = new AgentCapabilityPolicy();
            policy.setCapabilityCode(capabilityCode);
            policy.setAutonomyLevel(AutonomyLevel.SUGGESTION.getCode());
            policy.setAcceptCount(0);
            policy.setModifyCount(0);
            policy.setRejectCount(0);
            policy.setTotalCount(0);
            policy.setErrorCount(0);
            policyMapper.insert(policy);
        }
        policy.setTotalCount(policy.getTotalCount() + 1);
        if (!success) {
            policy.setErrorCount(policy.getErrorCount() + 1);
        }
        policyMapper.updateById(policy);
    }

    /**
     * 每日 02:00 自动升降级评估
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void evaluateDaily() {
        int changed = evaluateAll();
        if (changed > 0) {
            log.info("渐进自主评估完成，{} 个能力点级别发生变化", changed);
        }
    }

    /**
     * 评估全部策略（供定时任务与手动触发）
     *
     * @return 发生级别变化的能力点数
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluateAll() {
        List<AgentCapabilityPolicy> policies = policyMapper.selectList(null);
        int changed = 0;
        for (AgentCapabilityPolicy policy : policies) {
            policy.setLastEvaluatedAt(LocalDateTime.now());
            AutonomyLevel current = AutonomyLevel.of(policy.getAutonomyLevel());
            // 红线封顶
            SysCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<SysCapability>()
                    .eq(SysCapability::getCapabilityCode, policy.getCapabilityCode()));
            boolean never = capability != null && capability.getIsNever() != null && capability.getIsNever() == 1;
            int ceiling = never ? AutonomyLevel.SUGGESTION.getCode() : AutonomyLevel.AUTO_BATCH.getCode();
            // 无反馈数据时不评估（避免冷启动误判）
            int feedbackTotal = policy.getAcceptCount() + policy.getModifyCount() + policy.getRejectCount();
            if (feedbackTotal == 0 && policy.getTotalCount() == 0) {
                policyMapper.updateById(policy);
                continue;
            }
            double acceptRate = feedbackTotal == 0 ? 0 : (double) policy.getAcceptCount() / feedbackTotal;
            double errorRate = policy.getTotalCount() == 0 ? 0 : (double) policy.getErrorCount() / policy.getTotalCount();
            String note = null;
            // 降级优先：错误率超阈值
            if (errorRate > DEMOTE_ERROR_RATE && current.getCode() > AutonomyLevel.DISABLED.getCode()) {
                AutonomyLevel target = AutonomyLevel.of(Math.max(1, current.getCode() - 1));
                if (target != current) {
                    note = "错误率 " + String.format("%.1f%%", errorRate * 100) + " 超阈值，自动降级为 " + target.getLabel();
                    policy.setAutonomyLevel(target.getCode());
                    changed++;
                }
            } else if (feedbackTotal >= PROMOTE_MIN_TOTAL && acceptRate >= PROMOTE_ACCEPT_RATE
                    && current.getCode() < ceiling) {
                AutonomyLevel target = AutonomyLevel.of(Math.min(ceiling, current.getCode() + 1));
                if (target != current) {
                    note = "采纳率 " + String.format("%.1f%%", acceptRate * 100) + "（" + feedbackTotal + " 次反馈），自动升级为 " + target.getLabel();
                    policy.setAutonomyLevel(target.getCode());
                    changed++;
                }
            }
            if (note != null) {
                policy.setLevelChangeNote(note);
                log.info("能力自主级别变更: {} -> {}", policy.getCapabilityCode(), note);
                notifyLeaders(policy.getCapabilityCode(), note);
            }
            policyMapper.updateById(policy);
        }
        return changed;
    }

    /**
     * 手动调整级别（部门领导）
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustLevel(String capabilityCode, int levelCode) {
        // 红线：永久禁止类能力（审批/资金/主数据建档/删除）任何情况下不得设置为自动执行
        SysCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<SysCapability>()
                .eq(SysCapability::getCapabilityCode, capabilityCode));
        boolean never = capability != null && capability.getIsNever() != null && capability.getIsNever() == 1;
        if (never && levelCode > AutonomyLevel.SUGGESTION.getCode()) {
            throw new BusinessException(403, "永久禁止类能力（审批/资金/建档/删除）不可设置为自动执行");
        }
        AgentCapabilityPolicy policy = policyOf(capabilityCode);
        if (policy == null) {
            policy = new AgentCapabilityPolicy();
            policy.setCapabilityCode(capabilityCode);
            policy.setAcceptCount(0);
            policy.setModifyCount(0);
            policy.setRejectCount(0);
            policy.setTotalCount(0);
            policy.setErrorCount(0);
            policy.setAutonomyLevel(levelCode);
            policy.setLevelChangeNote("管理员手动设置");
            policyMapper.insert(policy);
            return;
        }
        policy.setAutonomyLevel(levelCode);
        policy.setLevelChangeNote("管理员手动调整为 " + AutonomyLevel.of(levelCode).getLabel());
        policyMapper.updateById(policy);
    }

    /**
     * 策略列表
     */
    public List<AgentCapabilityPolicy> list() {
        return policyMapper.selectList(new LambdaQueryWrapper<AgentCapabilityPolicy>()
                .orderByAsc(AgentCapabilityPolicy::getCapabilityCode));
    }

    private AgentCapabilityPolicy policyOf(String capabilityCode) {
        return policyMapper.selectOne(new LambdaQueryWrapper<AgentCapabilityPolicy>()
                .eq(AgentCapabilityPolicy::getCapabilityCode, capabilityCode));
    }

    private void notifyLeaders(String capabilityCode, String note) {
        try {
            // 通知部门领导（role_codes 含 ROLE_DEPT_LEADER 的用户）——经通知中心，失败不阻塞评估
            com.dept.mgmt.platform.notify.dto.NotificationSendDTO dto =
                    new com.dept.mgmt.platform.notify.dto.NotificationSendDTO();
            dto.setTemplateCode("GENERIC_REMIND");
            dto.setParams(Map.of("title", "能力自主级别变更",
                    "content", "能力 " + capabilityCode + "：" + note));
            // 简化：通知 1 号（部门领导账号，Phase 2 权限域完善后改为按角色查询）
            dto.setReceiverIds(List.of(1L));
            notificationService.send(dto);
        } catch (Exception e) {
            log.warn("自主级别变更通知发送失败（不阻塞）: {}", e.getMessage());
        }
    }
}
