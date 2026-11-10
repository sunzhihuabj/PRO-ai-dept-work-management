package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.dto.BonusPayDTO;
import com.dept.mgmt.dto.BonusSaveDTO;
import com.dept.mgmt.entity.ProjectBonus;
import com.dept.mgmt.entity.ProjectBonusRecord;
import com.dept.mgmt.mapper.ProjectBonusMapper;
import com.dept.mgmt.mapper.ProjectBonusRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 项目奖金服务
 * 联动规则：按用户当期 KPI 汇总等级调整系数 A=1.2 B=1.0 C=0.8 D=0.6，
 * monthlyAmount = baseAmount * coefficient
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BonusService {

    private final ProjectBonusMapper projectBonusMapper;
    private final ProjectBonusRecordMapper projectBonusRecordMapper;
    private final KpiService kpiService;

    // ==================== 配置管理 ====================

    public PageResult<ProjectBonus> list(Integer pageNum, Integer pageSize, Long projectId, Long userId,
                                         Integer bonusType, Integer status) {
        Page<ProjectBonus> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<ProjectBonus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, ProjectBonus::getProjectId, projectId);
        wrapper.eq(userId != null, ProjectBonus::getUserId, userId);
        wrapper.eq(bonusType != null, ProjectBonus::getBonusType, bonusType);
        wrapper.eq(status != null, ProjectBonus::getStatus, status);
        wrapper.orderByDesc(ProjectBonus::getCreateTime);
        projectBonusMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(BonusSaveDTO dto) {
        checkUnique(dto.getProjectId(), dto.getUserId(), dto.getBonusType(), dto.getProjectPeriod(), null);
        ProjectBonus bonus = new ProjectBonus();
        apply(bonus, dto);
        projectBonusMapper.insert(bonus);
        return bonus.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BonusSaveDTO dto) {
        ProjectBonus exists = projectBonusMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "奖金配置不存在");
        }
        checkUnique(dto.getProjectId(), dto.getUserId(), dto.getBonusType(), dto.getProjectPeriod(), id);
        apply(exists, dto);
        projectBonusMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProjectBonus exists = projectBonusMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "奖金配置不存在");
        }
        long paid = countPaid(id);
        if (paid > 0) {
            throw new BusinessException(409, "该配置已有发放记录，不允许删除（可停用）");
        }
        projectBonusMapper.deleteById(id);
    }

    private long countPaid(Long bonusId) {
        LambdaQueryWrapper<ProjectBonusRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectBonusRecord::getBonusId, bonusId);
        return projectBonusRecordMapper.selectCount(wrapper);
    }

    private void checkUnique(Long projectId, Long userId, Integer bonusType, Integer period, Long excludeId) {
        LambdaQueryWrapper<ProjectBonus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectBonus::getProjectId, projectId)
                .eq(ProjectBonus::getUserId, userId)
                .eq(ProjectBonus::getBonusType, bonusType);
        if (period != null) {
            wrapper.eq(ProjectBonus::getProjectPeriod, period);
        } else {
            wrapper.isNull(ProjectBonus::getProjectPeriod);
        }
        if (excludeId != null) {
            wrapper.ne(ProjectBonus::getId, excludeId);
        }
        if (projectBonusMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "同项目/用户/类型/阶段的奖金配置已存在");
        }
    }

    private void apply(ProjectBonus entity, BonusSaveDTO dto) {
        entity.setProjectId(dto.getProjectId());
        entity.setUserId(dto.getUserId());
        entity.setBonusType(dto.getBonusType());
        entity.setProjectPeriod(dto.getProjectPeriod());
        entity.setBaseAmount(dto.getBaseAmount());
        entity.setCoefficient(dto.getCoefficient() == null ? BigDecimal.ONE : dto.getCoefficient());
        recalcAmounts(entity);
        entity.setPaidMonthly(BigDecimal.ZERO);
        entity.setPaidYearly(BigDecimal.ZERO);
        entity.setStatus(1);
    }

    private void recalcAmounts(ProjectBonus entity) {
        BigDecimal base = entity.getBaseAmount() == null ? BigDecimal.ZERO : entity.getBaseAmount();
        BigDecimal coef = entity.getCoefficient() == null ? BigDecimal.ONE : entity.getCoefficient();
        entity.setMonthlyAmount(base.multiply(coef).setScale(2, RoundingMode.HALF_UP));
        entity.setYearlyAmount(base.multiply(coef).multiply(new BigDecimal("3")).setScale(2, RoundingMode.HALF_UP));
    }

    // ==================== KPI 联动 ====================

    /**
     * 按用户当期 KPI 汇总等级调整系数并重算金额
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectBonus applyKpi(Long id, String period) {
        if (!StringUtils.hasText(period)) {
            throw new BusinessException(400, "period 不能为空（YYYY-MM）");
        }
        ProjectBonus bonus = projectBonusMapper.selectById(id);
        if (bonus == null) {
            throw new BusinessException(404, "奖金配置不存在");
        }
        Map<String, Object> report = kpiService.report(period, null);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows = (List<java.util.Map<String, Object>>) report.get("rows");
        String level = rows.stream()
                .filter(r -> bonus.getUserId().equals(((Number) r.get("userId")).longValue()))
                .map(r -> String.valueOf(r.get("level")))
                .findFirst()
                .orElse(null);
        if (level == null) {
            throw new BusinessException(404, "该用户在 " + period + " 期没有 KPI 评分记录");
        }
        BigDecimal coef = switch (level) {
            case "A" -> new BigDecimal("1.2");
            case "B" -> BigDecimal.ONE;
            case "C" -> new BigDecimal("0.8");
            default -> new BigDecimal("0.6");
        };
        bonus.setCoefficient(coef);
        recalcAmounts(bonus);
        projectBonusMapper.updateById(bonus);
        log.info("奖金KPI联动: bonusId={}, period={}, level={}, coefficient={}", id, period, level, coef);
        return bonus;
    }

    // ==================== 发放 ====================

    @Transactional(rollbackFor = Exception.class)
    public ProjectBonusRecord pay(Long id, BonusPayDTO dto) {
        ProjectBonus bonus = projectBonusMapper.selectById(id);
        if (bonus == null) {
            throw new BusinessException(404, "奖金配置不存在");
        }
        if (bonus.getStatus() == null || bonus.getStatus() != 1) {
            throw new BusinessException(409, "奖金配置已停用");
        }
        BigDecimal amount;
        if (dto.getPayType() == 1) {
            amount = bonus.getMonthlyAmount() == null ? BigDecimal.ZERO : bonus.getMonthlyAmount();
        } else {
            amount = bonus.getYearlyAmount() == null ? BigDecimal.ZERO : bonus.getYearlyAmount();
            BigDecimal paid = bonus.getPaidYearly() == null ? BigDecimal.ZERO : bonus.getPaidYearly();
            if (paid.add(amount).compareTo(bonus.getYearlyAmount()) > 0) {
                throw new BusinessException(409, "年度奖金已发放完毕，不允许重复发放");
            }
        }

        ProjectBonusRecord record = new ProjectBonusRecord();
        record.setBonusId(id);
        record.setProjectId(bonus.getProjectId());
        record.setUserId(bonus.getUserId());
        record.setPayType(dto.getPayType());
        record.setPayAmount(amount);
        record.setPayPeriod(dto.getPayPeriod());
        record.setPayTime(LocalDateTime.now());
        record.setRemark(dto.getRemark());
        projectBonusRecordMapper.insert(record);

        // 累计已发放
        ProjectBonus update = new ProjectBonus();
        update.setId(id);
        if (dto.getPayType() == 1) {
            update.setPaidMonthly((bonus.getPaidMonthly() == null ? BigDecimal.ZERO : bonus.getPaidMonthly()).add(amount));
        } else {
            update.setPaidYearly((bonus.getPaidYearly() == null ? BigDecimal.ZERO : bonus.getPaidYearly()).add(amount));
        }
        projectBonusMapper.updateById(update);

        log.info("奖金发放: bonusId={}, type={}, amount={}, period={}", id, dto.getPayType(), amount, dto.getPayPeriod());
        return record;
    }

    public PageResult<ProjectBonusRecord> records(Integer pageNum, Integer pageSize, Long projectId,
                                                  Long userId, Integer payType, String payPeriod) {
        Page<ProjectBonusRecord> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<ProjectBonusRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, ProjectBonusRecord::getProjectId, projectId);
        wrapper.eq(userId != null, ProjectBonusRecord::getUserId, userId);
        wrapper.eq(payType != null, ProjectBonusRecord::getPayType, payType);
        wrapper.eq(StringUtils.hasText(payPeriod), ProjectBonusRecord::getPayPeriod, payPeriod);
        wrapper.orderByDesc(ProjectBonusRecord::getPayTime);
        projectBonusRecordMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }
}
