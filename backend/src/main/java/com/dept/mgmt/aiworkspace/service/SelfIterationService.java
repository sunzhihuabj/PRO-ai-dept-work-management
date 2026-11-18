package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.AgentFeedback;
import com.dept.mgmt.aiworkspace.entity.SoulPatchProposal;
import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.agent.entity.AgentTask;
import com.dept.mgmt.mapper.AgentFeedbackMapper;
import com.dept.mgmt.mapper.AgentTaskMapper;
import com.dept.mgmt.mapper.SoulPatchProposalMapper;
import com.dept.mgmt.security.LoginUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自迭代服务（V2.0 M8）：反馈采集 → 每周低分归因 → 补丁建议 → 人工采纳（SoulManageService）。
 * 归因为本地规则（任务书不清/工具缺失/知识不足），不调模型；建议落库后进决策建议中心提示。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfIterationService {

    private final AgentFeedbackMapper feedbackMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final SoulPatchProposalMapper proposalMapper;
    private final DecisionAdviceService adviceService;
    private final com.dept.mgmt.aiworkspace.service.SoulManageService soulManageService;

    public static final String SCENE_SELF_ITERATION = "SELF_ITERATION";

    /** 反馈提交（RUN/MESSAGE/TASK 三源；评分人取当前登录用户） */
    public AgentFeedback submitFeedback(String sourceType, Long sourceId, String runId,
                                        int rating, String dimension, String comment) {
        if (!"RUN".equals(sourceType) && !"MESSAGE".equals(sourceType) && !"TASK".equals(sourceType)) {
            throw new com.dept.mgmt.common.exception.BusinessException(400, "sourceType 仅支持 RUN/MESSAGE/TASK");
        }
        if (rating != 1 && rating != -1) {
            throw new com.dept.mgmt.common.exception.BusinessException(400, "rating 仅支持 ±1");
        }
        if (sourceId == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(400, "sourceId 不能为空");
        }
        LoginUser user = LoginUser.current();
        AgentFeedback fb = new AgentFeedback();
        fb.setSourceType(sourceType);
        fb.setSourceId(sourceId);
        fb.setRunId(StringUtils.hasText(runId) ? runId : null);
        fb.setUserId(user == null ? null : user.getUserId());
        fb.setRating(rating);
        fb.setDimension(StringUtils.hasText(dimension) ? dimension : null);
        fb.setComment(StringUtils.hasText(comment) ? comment.substring(0, Math.min(comment.length(), 500)) : null);
        feedbackMapper.insert(fb);
        // 任务级差评直接触发轻量归因建议（不用等周 Job），保持闭环及时性
        if (rating == -1 && "TASK".equals(sourceType)) {
            proposeFromTask(fb, sourceId);
        }
        return fb;
    }

    /** 反馈统计：总量/好评率/维度分布 */
    public Map<String, Object> feedbackStats() {
        Long total = feedbackMapper.selectCount(new QueryWrapper<>());
        Long positive = feedbackMapper.selectCount(new LambdaQueryWrapper<AgentFeedback>()
                .eq(AgentFeedback::getRating, 1));
        Long negative = feedbackMapper.selectCount(new LambdaQueryWrapper<AgentFeedback>()
                .eq(AgentFeedback::getRating, -1));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("positive", positive);
        stats.put("negative", negative);
        stats.put("approvalRate", total == 0 ? 1.0 : Math.round(positive * 1000.0 / total) / 1000.0);
        return stats;
    }

    /**
     * 每周迭代 Job（周一 08:00，可手动触发）：本周差评（rating<0）与失败/升级任务归因 → 补丁建议。
     * 幂等：同周期同归因类别已有 PROPOSED/ADOPTED 建议时跳过。
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void weeklyIterate() {
        weeklyIterate(LocalDate.now());
    }

    /** weeklyIterate 可注入日期（测试/手动补跑用） */
    public Map<String, Object> weeklyIterate(LocalDate date) {
        String period = periodOf(date);
        LocalDateTime weekStart = date.with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        // 1. 本周差评（RUN/TASK 源；MESSAGE 差评属对话体验，不进任务归因）
        List<AgentFeedback> negatives = feedbackMapper.selectList(new LambdaQueryWrapper<AgentFeedback>()
                .eq(AgentFeedback::getRating, -1)
                .in(AgentFeedback::getSourceType, "RUN", "TASK")
                .between(AgentFeedback::getCreateTime, weekStart, weekEnd));
        // 2. 本周失败/升级任务
        List<AgentTask> failedTasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .in(AgentTask::getStatus, "failed", "escalated")
                .between(AgentTask::getCreateTime, weekStart, weekEnd)
                .last("LIMIT 50"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("negativeCount", negatives.size());
        result.put("failedCount", failedTasks.size());
        int created = 0;
        // 3. 归因：有差评样本 → 按 dimension 分类；无差评但有失败任务 → 失败任务归因
        if (!negatives.isEmpty()) {
            created += proposeOnce(period, categorizeByDimension(negatives),
                    "本周差评 " + negatives.size() + " 条", negatives.size(),
                    evidence(negatives, failedTasks));
        }
        if (negatives.size() < 3 && !failedTasks.isEmpty()) {
            created += proposeOnce(period, "TASK_UNCLEAR",
                    "本周失败/升级任务 " + failedTasks.size() + " 个", failedTasks.size(),
                    evidence(negatives, failedTasks));
        }
        result.put("proposalsCreated", created);
        if (created > 0) {
            adviceService.createGovernanceAdvice(SCENE_SELF_ITERATION, "GOVERNANCE", 0L,
                    "自迭代周报（" + period + "）：生成 " + created + " 条补丁建议待采纳",
                    "本周差评 " + negatives.size() + " 条、失败/升级任务 " + failedTasks.size()
                            + " 个。请进入「AI Agent 员工 → 自迭代」审阅补丁建议并采纳。",
                    null, Map.of("period", period, "created", created));
        }
        log.info("自迭代周Job完成: period={}, negatives={}, failed={}, proposals={}",
                period, negatives.size(), failedTasks.size(), created);
        return result;
    }

    /** 差评维度 → 归因类别（无维度默认 OTHER→任务书不清样本看 comment 关键词） */
    private String categorizeByDimension(List<AgentFeedback> negatives) {
        boolean toolGap = negatives.stream().anyMatch(f -> "TOOL".equals(f.getDimension()));
        if (toolGap) {
            return "TOOL_MISSING";
        }
        boolean knowledge = negatives.stream().anyMatch(f -> {
            String c = f.getComment();
            return c != null && (c.contains("不知道") || c.contains("知识") || c.contains("资料") || c.contains("标准"));
        });
        if (knowledge) {
            return "KNOWLEDGE_GAP";
        }
        return "TASK_UNCLEAR";
    }

    /** 幂等落库：同周期同归因已有未决建议则跳过 */
    private int proposeOnce(String period, String rootCause, String summary, int count, String evidenceJson) {
        Long exists = proposalMapper.selectCount(new LambdaQueryWrapper<SoulPatchProposal>()
                .eq(SoulPatchProposal::getPeriod, period)
                .eq(SoulPatchProposal::getRootCause, rootCause)
                .in(SoulPatchProposal::getStatus, "PROPOSED", "ADOPTED"));
        if (exists > 0) {
            return 0;
        }
        SoulPatchProposal p = new SoulPatchProposal();
        p.setPeriod(period);
        p.setCategory("TOOL_MISSING".equals(rootCause) ? "TOOL_REQUEST" : "SOUL_PATCH");
        p.setRootCause(rootCause);
        p.setSummary(summary + "（样本 " + count + " 条）");
        p.setSuggestion(suggestFor(rootCause));
        p.setEvidenceJson(evidenceJson);
        p.setStatus("PROPOSED");
        proposalMapper.insert(p);
        return 1;
    }

    private String suggestFor(String rootCause) {
        return switch (rootCause) {
            case "TOOL_MISSING" -> "为对应业务场景补充 MCP 工具注册（OpsToolRegistry/McpToolRegistry），并在技能卡 triggers 中加入触发词。";
            case "KNOWLEDGE_GAP" -> "将对应行业规范/操作口径文档导入知识库（kb），并在 SOUL.md 补丁段中声明该类问题的检索策略。";
            default -> "在 SOUL.md 追加任务书口径补丁：明确该类指令的执行边界、必填参数与汇报格式，降低任务书歧义。";
        };
    }

    private void proposeFromTask(AgentFeedback fb, Long taskId) {
        try {
            AgentTask task = agentTaskMapper.selectById(taskId);
            String summary = task == null ? "任务 #" + taskId + " 差评" : "任务 " + task.getTaskNo() + " 差评";
            proposeOnce(periodOf(LocalDate.now()), categorizeByDimension(List.of(fb)),
                    summary, 1, evidence(List.of(fb), List.of()));
        } catch (Exception e) {
            log.warn("差评即时归因失败（不阻塞反馈）: {}", e.getMessage());
        }
    }

    private String evidence(List<AgentFeedback> negatives, List<AgentTask> failedTasks) {
        List<Map<String, Object>> ev = new ArrayList<>();
        for (AgentFeedback f : negatives) {
            ev.add(Map.of("type", "FEEDBACK", "sourceType", f.getSourceType(),
                    "sourceId", f.getSourceId(), "dimension", String.valueOf(f.getDimension()),
                    "comment", String.valueOf(f.getComment())));
        }
        for (AgentTask t : failedTasks) {
            ev.add(Map.of("type", "TASK", "taskNo", t.getTaskNo(), "capability",
                    t.getCapabilityCode(), "status", t.getStatus()));
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    Map.of("items", ev.stream().limit(20).toList()));
        } catch (Exception e) {
            return "[]";
        }
    }

    /** ISO 周号（如 2026-W47） */
    public static String periodOf(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        int week = date.get(wf.weekOfWeekBasedYear());
        int year = date.get(wf.weekBasedYear());
        return year + "-W" + (week < 10 ? "0" + week : week);
    }

    // ==================== 建议管理 ====================

    public List<SoulPatchProposal> listProposals(String status) {
        LambdaQueryWrapper<SoulPatchProposal> qw = new LambdaQueryWrapper<SoulPatchProposal>()
                .orderByDesc(SoulPatchProposal::getCreateTime);
        if (StringUtils.hasText(status)) {
            qw.eq(SoulPatchProposal::getStatus, status);
        }
        return proposalMapper.selectList(qw.last("LIMIT 50"));
    }

    /** 驳回 */
    public void reject(Long id, String note) {
        SoulPatchProposal p = proposalMapper.selectById(id);
        if (p == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(404, "建议不存在");
        }
        p.setStatus("REJECTED");
        p.setAdoptNote(note);
        proposalMapper.updateById(p);
    }

    /** 采纳（委托 SoulManageService 写 SOUL.md 版本链，回归门槛内建） */
    public Map<String, Object> adopt(Long id, String note) {
        SoulPatchProposal p = proposalMapper.selectById(id);
        if (p == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(404, "建议不存在");
        }
        if (!"PROPOSED".equals(p.getStatus())) {
            throw new com.dept.mgmt.common.exception.BusinessException(400, "建议已处理（" + p.getStatus() + "）");
        }
        return soulManageService.adoptPatch(p, note, LoginUser.current() == null
                ? "system" : LoginUser.current().getUsername());
    }
}
