package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dto.KpiIndicatorDTO;
import com.dept.mgmt.dto.KpiScoreAdjustDTO;
import com.dept.mgmt.entity.KpiIndicator;
import com.dept.mgmt.entity.KpiScore;
import com.dept.mgmt.entity.KpiWeight;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.mapper.KpiIndicatorMapper;
import com.dept.mgmt.mapper.KpiScoreMapper;
import com.dept.mgmt.mapper.KpiWeightMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 绩效管理服务
 * 等级规则（依据需求文档7.3）：A≥90，B 80-89，C 70-79，D<70
 * 评分状态：0草稿 1已确认 2已归档
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiService {

    private final KpiIndicatorMapper kpiIndicatorMapper;
    private final KpiScoreMapper kpiScoreMapper;
    private final KpiWeightMapper kpiWeightMapper;
    private final SysUserMapper sysUserMapper;

    // ==================== 指标管理 ====================

    public List<KpiIndicator> listIndicators(Integer projectType, Integer status, String keyword) {
        LambdaQueryWrapper<KpiIndicator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectType != null, KpiIndicator::getProjectType, projectType);
        wrapper.eq(status != null, KpiIndicator::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(KpiIndicator::getIndicatorName, keyword)
                    .or().like(KpiIndicator::getIndicatorCode, keyword));
        }
        wrapper.orderByAsc(KpiIndicator::getId);
        return kpiIndicatorMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createIndicator(KpiIndicatorDTO dto) {
        LambdaQueryWrapper<KpiIndicator> exists = new LambdaQueryWrapper<>();
        exists.eq(KpiIndicator::getIndicatorCode, dto.getIndicatorCode());
        if (kpiIndicatorMapper.selectCount(exists) > 0) {
            throw new BusinessException(400, "指标编码已存在: " + dto.getIndicatorCode());
        }
        KpiIndicator entity = new KpiIndicator();
        applyIndicator(entity, dto);
        kpiIndicatorMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateIndicator(Long id, KpiIndicatorDTO dto) {
        KpiIndicator exists = kpiIndicatorMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "指标不存在");
        }
        // 编码唯一性（排除自身）
        LambdaQueryWrapper<KpiIndicator> dup = new LambdaQueryWrapper<>();
        dup.eq(KpiIndicator::getIndicatorCode, dto.getIndicatorCode()).ne(KpiIndicator::getId, id);
        if (kpiIndicatorMapper.selectCount(dup) > 0) {
            throw new BusinessException(400, "指标编码已被其他指标占用");
        }
        applyIndicator(exists, dto);
        kpiIndicatorMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteIndicator(Long id) {
        KpiIndicator exists = kpiIndicatorMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "指标不存在");
        }
        // 已有评分的指标不允许物理删除，建议改为停用
        LambdaQueryWrapper<KpiScore> used = new LambdaQueryWrapper<>();
        used.eq(KpiScore::getIndicatorId, id);
        if (kpiScoreMapper.selectCount(used) > 0) {
            throw new BusinessException(409, "该指标已有评分记录，不允许删除，请停用");
        }
        kpiIndicatorMapper.deleteById(id);
    }

    private void applyIndicator(KpiIndicator entity, KpiIndicatorDTO dto) {
        entity.setIndicatorName(dto.getIndicatorName());
        entity.setIndicatorCode(dto.getIndicatorCode());
        entity.setProjectType(dto.getProjectType());
        entity.setIndicatorType(dto.getIndicatorType());
        entity.setBaseScore(dto.getBaseScore());
        entity.setUnit(dto.getUnit());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    // ==================== 权重配置 ====================

    public List<KpiWeight> listWeights(Integer projectType, String period) {
        LambdaQueryWrapper<KpiWeight> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectType != null, KpiWeight::getProjectType, projectType);
        if (StringUtils.hasText(period)) {
            wrapper.and(w -> w.eq(KpiWeight::getPeriod, period).or().isNull(KpiWeight::getPeriod));
        }
        return kpiWeightMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveWeights(Integer projectType, String period, Map<Long, BigDecimal> weightMap) {
        if (weightMap == null || weightMap.isEmpty()) {
            throw new BusinessException(400, "权重配置不能为空");
        }
        BigDecimal sum = weightMap.values().stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(new BigDecimal("100")) != 0) {
            throw new BusinessException(400, "权重之和必须等于100%，当前为 " + sum + "%");
        }
        weightMap.forEach((indicatorId, weight) -> {
            KpiWeight entity = new KpiWeight();
            entity.setProjectType(projectType);
            entity.setIndicatorId(indicatorId);
            entity.setWeight(weight);
            entity.setPeriod(period);
            kpiWeightMapper.insert(entity);
        });
    }

    // ==================== 评分管理 ====================

    /**
     * 计算某期绩效：为所有启用用户 + 启用指标生成基础评分记录（已存在的跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public int calculate(String period, Integer projectType) {
        if (!StringUtils.hasText(period)) {
            throw new BusinessException(400, "period 不能为空（格式 YYYY-MM）");
        }
        // 若该期已有确认数据，禁止重新计算
        LambdaQueryWrapper<KpiScore> check = new LambdaQueryWrapper<>();
        check.eq(KpiScore::getPeriod, period).eq(KpiScore::getStatus, 1);
        if (kpiScoreMapper.selectCount(check) > 0) {
            throw new BusinessException(409, "该期间绩效已发布，不允许重新计算");
        }

        // 获取全部启用指标（可按项目类型过滤）
        LambdaQueryWrapper<KpiIndicator> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.eq(KpiIndicator::getStatus, 1);
        indWrapper.eq(projectType != null, KpiIndicator::getProjectType, projectType);
        List<KpiIndicator> indicators = kpiIndicatorMapper.selectList(indWrapper);
        if (indicators.isEmpty()) {
            return 0;
        }

        // 获取全部启用用户
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getStatus, 1);
        List<SysUser> users = sysUserMapper.selectList(userWrapper);

        int created = 0;
        for (SysUser user : users) {
            for (KpiIndicator indicator : indicators) {
                // 唯一键 user+period+indicator
                LambdaQueryWrapper<KpiScore> checkOne = new LambdaQueryWrapper<>();
                checkOne.eq(KpiScore::getUserId, user.getId())
                        .eq(KpiScore::getPeriod, period)
                        .eq(KpiScore::getIndicatorId, indicator.getId());
                if (kpiScoreMapper.selectCount(checkOne) > 0) {
                    continue;
                }
                BigDecimal base = indicator.getBaseScore() == null ? BigDecimal.ZERO : indicator.getBaseScore();
                KpiScore score = new KpiScore();
                score.setUserId(user.getId());
                score.setPeriod(period);
                score.setIndicatorId(indicator.getId());
                score.setBaseScore(base);
                score.setAdjustScore(BigDecimal.ZERO);
                score.setFinalScore(base);
                score.setLevel(computeLevel(base));
                score.setStatus(0);
                kpiScoreMapper.insert(score);
                created++;
            }
        }
        log.info("绩效计算完成: period={}, projectType={}, 新记录数={}", period, projectType, created);
        return created;
    }

    public List<KpiScore> listScores(String period, Long userId, Long indicatorId, Integer status) {
        LambdaQueryWrapper<KpiScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(period), KpiScore::getPeriod, period);
        wrapper.eq(userId != null, KpiScore::getUserId, userId);
        wrapper.eq(indicatorId != null, KpiScore::getIndicatorId, indicatorId);
        wrapper.eq(status != null, KpiScore::getStatus, status);
        wrapper.orderByAsc(KpiScore::getUserId).orderByAsc(KpiScore::getIndicatorId);
        return kpiScoreMapper.selectList(wrapper);
    }

    /**
     * 调整单个评分
     */
    @Transactional(rollbackFor = Exception.class)
    public KpiScore adjustScore(Long scoreId, KpiScoreAdjustDTO dto) {
        KpiScore score = kpiScoreMapper.selectById(scoreId);
        if (score == null) {
            throw new BusinessException(404, "评分记录不存在");
        }
        if (score.getStatus() != null && score.getStatus() != 0) {
            throw new BusinessException(409, "该评分已确认/归档，不可调整");
        }
        score.setAdjustScore(dto.getAdjustScore());
        score.setAdjustReason(dto.getAdjustReason());
        BigDecimal base = score.getBaseScore() == null ? BigDecimal.ZERO : score.getBaseScore();
        BigDecimal finalScore = base.add(dto.getAdjustScore() == null ? BigDecimal.ZERO : dto.getAdjustScore());
        if (finalScore.compareTo(BigDecimal.ZERO) < 0) {
            finalScore = BigDecimal.ZERO;
        }
        score.setFinalScore(finalScore);
        score.setLevel(computeLevel(finalScore));
        kpiScoreMapper.updateById(score);
        LoginUser loginUser = LoginUser.current();
        log.info("绩效调整: scoreId={}, adjust={}, reason={}, operator={}", scoreId, dto.getAdjustScore(),
                dto.getAdjustReason(), loginUser != null ? loginUser.getUsername() : "system");
        return score;
    }

    /**
     * 确认发布某期绩效
     */
    @Transactional(rollbackFor = Exception.class)
    public int confirmPeriod(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BusinessException(400, "period 不能为空");
        }
        LambdaQueryWrapper<KpiScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KpiScore::getPeriod, period);
        List<KpiScore> scores = kpiScoreMapper.selectList(wrapper);
        int count = 0;
        for (KpiScore score : scores) {
            if (score.getStatus() == null || score.getStatus() == 0) {
                score.setStatus(1);
                kpiScoreMapper.updateById(score);
                count++;
            }
        }
        log.info("绩效发布: period={}, count={}", period, count);
        return count;
    }

    /**
     * 绩效报表：按用户汇总（加权总分=Σ finalScore * weight%），含等级分布
     */
    public Map<String, Object> report(String period, Integer projectType) {
        if (!StringUtils.hasText(period)) {
            throw new BusinessException(400, "period 不能为空");
        }
        List<KpiScore> scores = listScores(period, null, null, null);

        // 权重映射 indicatorId -> weight（全局 period 或指定 period）
        List<KpiWeight> weights = listWeights(projectType, period);
        Map<Long, BigDecimal> weightMap = weights.stream()
                .collect(Collectors.toMap(KpiWeight::getIndicatorId, KpiWeight::getWeight, (a, b) -> b));

        // 按用户聚合
        Map<Long, List<KpiScore>> byUser = scores.stream().collect(Collectors.groupingBy(KpiScore::getUserId));
        Map<Long, SysUser> userMap = sysUserMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> levelCount = new HashMap<>();
        for (Map.Entry<Long, List<KpiScore>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal weightSum = BigDecimal.ZERO;
            for (KpiScore s : entry.getValue()) {
                BigDecimal w = weightMap.getOrDefault(s.getIndicatorId(), BigDecimal.ZERO);
                BigDecimal finalScore = s.getFinalScore() == null ? BigDecimal.ZERO : s.getFinalScore();
                total = total.add(finalScore.multiply(w));
                weightSum = weightSum.add(w);
            }
            BigDecimal normalized = weightSum.compareTo(BigDecimal.ZERO) > 0
                    ? total.divide(weightSum, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String level = computeLevel(normalized);
            levelCount.merge(level, 1, Integer::sum);
            SysUser user = userMap.get(userId);

            Map<String, Object> row = new HashMap<>();
            row.put("userId", userId);
            row.put("userName", user != null ? user.getRealName() : "#" + userId);
            row.put("totalScore", normalized);
            row.put("level", level);
            row.put("detailCount", entry.getValue().size());
            rows.add(row);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("totalScore")).compareTo((BigDecimal) a.get("totalScore")));

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("userCount", rows.size());
        result.put("levelCount", levelCount);
        BigDecimal avg = rows.isEmpty() ? BigDecimal.ZERO
                : rows.stream().map(r -> (BigDecimal) r.get("totalScore"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
        result.put("avgScore", avg);
        result.put("rows", rows);
        return result;
    }

    private String computeLevel(BigDecimal score) {
        if (score == null) return "D";
        int v = score.intValue();
        if (v >= 90) return "A";
        if (v >= 80) return "B";
        if (v >= 70) return "C";
        return "D";
    }

    /**
     * 导出绩效报表为 CSV（UTF-8 带 BOM，便于 Excel 直接打开）
     */
    public String exportReportCsv(String period, Integer projectType) {
        Map<String, Object> report = report(period, projectType);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff'); // UTF-8 BOM 便于 Excel 识别中文
        sb.append("用户ID,用户名,加权总分,等级,指标数\n");
        for (Map<String, Object> row : rows) {
            sb.append(escape(String.valueOf(row.get("userId")))).append(',')
              .append(escape(String.valueOf(row.get("userName")))).append(',')
              .append(escape(String.valueOf(row.get("totalScore")))).append(',')
              .append(escape(String.valueOf(row.get("level")))).append(',')
              .append(escape(String.valueOf(row.get("detailCount")))).append('\n');
        }
        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
