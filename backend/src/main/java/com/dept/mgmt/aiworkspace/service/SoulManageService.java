package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.SoulChangeLog;
import com.dept.mgmt.aiworkspace.entity.SoulPatchProposal;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.SoulChangeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOUL.md 管理服务（V2.0 M8）：补丁建议采纳 → 追加部门口径补丁段 → 版本链留痕（可回滚）。
 * 回归门槛：黄金指令回归集通过率 ≥90% 才允许写入；无回归数据时跳过门槛（首次建链）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulManageService {

    private final SoulChangeLogMapper changeLogMapper;
    private final RegressionSuiteService regressionSuiteService;

    /** Hermes SOUL.md 路径（单机部署默认用户目录；可配置覆盖） */
    @Value("${hermes.soul-path:C:\\Users\\sunzh\\AppData\\Local\\hermes\\SOUL.md}")
    private String soulPath;

    /** 回归通过率门槛 */
    @Value("${hermes.soul-adoption-regression-threshold:0.9}")
    private double regressionThreshold;

    /** 补丁建议采纳：追加补丁段 + 快照落版本链 + 回写建议状态 */
    public Map<String, Object> adoptPatch(SoulPatchProposal proposal, String note, String operator) {
        // 回归门槛：SOUL/工具变更后回归不降级（≥基线 90%）
        Double lastRate = regressionSuiteService.lastPassRate();
        if (lastRate != null && lastRate < regressionThreshold) {
            throw new BusinessException(400, "回归集通过率 " + lastRate + " 低于门槛 " + regressionThreshold
                    + "，请先修复回归用例再采纳补丁");
        }
        String current;
        try {
            current = Files.readString(Path.of(soulPath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(500, "SOUL.md 读取失败: " + soulPath);
        }
        String patched = current + "\n\n## 部门口径补丁 " + proposal.getPeriod()
                + "（" + proposal.getRootCause() + "）\n- " + proposal.getSuggestion()
                + (StringUtils.hasText(note) ? "\n- 采纳备注：" + note : "");

        String nextVersion = nextVersion();
        try {
            Files.writeString(Path.of(soulPath), patched, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(500, "SOUL.md 写入失败（版本链未落库，无脏状态）: " + e.getMessage());
        }

        SoulChangeLog logRow = new SoulChangeLog();
        logRow.setVersion(nextVersion);
        logRow.setChangeType("PATCH");
        logRow.setDiffSummary("采纳建议 #" + proposal.getId() + "（" + proposal.getRootCause() + "）: "
                + abbreviate(proposal.getSummary(), 120));
        logRow.setContentSnapshot(patched);
        logRow.setProposalId(proposal.getId());
        logRow.setOperator(operator);
        logRow.setCreateTime(LocalDateTime.now());
        changeLogMapper.insert(logRow);

        proposal.setStatus("ADOPTED");
        proposal.setAdoptNote(note);
        proposal.setSoulVersion(nextVersion);
        proposal.setAdoptedAt(LocalDateTime.now());
        // proposal 状态回写由 SelfIterationService 持有 mapper，此处仅返回；调用方负责落库
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("soulVersion", nextVersion);
        result.put("changeLogId", logRow.getId());
        result.put("path", soulPath);
        return result;
    }

    /** 版本链列表（新→旧） */
    public List<SoulChangeLog> versions() {
        return changeLogMapper.selectList(new LambdaQueryWrapper<SoulChangeLog>()
                .orderByDesc(SoulChangeLog::getId));
    }

    /** 回滚到指定版本快照（回滚本身也是一次版本记录） */
    public Map<String, Object> rollback(String version, String operator) {
        SoulChangeLog target = changeLogMapper.selectOne(new LambdaQueryWrapper<SoulChangeLog>()
                .eq(SoulChangeLog::getVersion, version));
        if (target == null) {
            throw new BusinessException(404, "版本不存在: " + version);
        }
        String nextVersion = nextVersion();
        try {
            Files.writeString(Path.of(soulPath), target.getContentSnapshot(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(500, "SOUL.md 回滚失败: " + e.getMessage());
        }
        SoulChangeLog logRow = new SoulChangeLog();
        logRow.setVersion(nextVersion);
        logRow.setChangeType("ROLLBACK");
        logRow.setDiffSummary("回滚到 " + version);
        logRow.setContentSnapshot(target.getContentSnapshot());
        logRow.setOperator(operator);
        logRow.setCreateTime(LocalDateTime.now());
        changeLogMapper.insert(logRow);
        return Map.of("soulVersion", nextVersion, "rollbackFrom", version);
    }

    private String nextVersion() {
        Long count = changeLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
        return "v" + (count == null ? 1 : count + 1);
    }

    private static String abbreviate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
