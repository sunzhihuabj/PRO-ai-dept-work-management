package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.entity.A3Report;
import com.dept.mgmt.entity.ImprovementProposal;
import com.dept.mgmt.entity.LessonsLearned;
import com.dept.mgmt.entity.RootCauseAnalysis;
import com.dept.mgmt.entity.ValueStreamRecord;
import com.dept.mgmt.entity.WasteRecord;
import com.dept.mgmt.mapper.A3ReportMapper;
import com.dept.mgmt.mapper.ImprovementProposalMapper;
import com.dept.mgmt.mapper.LessonsLearnedMapper;
import com.dept.mgmt.mapper.RootCauseAnalysisMapper;
import com.dept.mgmt.mapper.ValueStreamRecordMapper;
import com.dept.mgmt.mapper.WasteRecordMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 精益改进服务
 * 提案状态机：0待审核 → 1审核通过 → 2实施中 → 3已完成；0 → 4已驳回
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeanService {

    public static final int P_PENDING = 0;
    public static final int P_APPROVED = 1;
    public static final int P_IMPLEMENTING = 2;
    public static final int P_DONE = 3;
    public static final int P_REJECTED = 4;

    private final ImprovementProposalMapper proposalMapper;
    private final RootCauseAnalysisMapper rcaMapper;
    private final LessonsLearnedMapper lessonMapper;
    private final A3ReportMapper a3Mapper;
    private final ValueStreamRecordMapper vsMapper;
    private final WasteRecordMapper wasteMapper;

    // ==================== 改进提案 ====================

    public PageResult<ImprovementProposal> listProposals(Integer pageNum, Integer pageSize,
                                                         Long projectId, Integer status, String proposalType, String keyword) {
        Page<ImprovementProposal> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<ImprovementProposal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, ImprovementProposal::getProjectId, projectId);
        wrapper.eq(status != null, ImprovementProposal::getStatus, status);
        wrapper.eq(StringUtils.hasText(proposalType), ImprovementProposal::getProposalType, proposalType);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ImprovementProposal::getTitle, keyword)
                    .or().like(ImprovementProposal::getProposalContent, keyword));
        }
        wrapper.orderByDesc(ImprovementProposal::getCreateTime);
        proposalMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    public ImprovementProposal proposalDetail(Long id) {
        ImprovementProposal p = proposalMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "提案不存在");
        }
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createProposal(ImprovementProposal dto) {
        LoginUser loginUser = LoginUser.current();
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(400, "提案标题不能为空");
        }
        dto.setId(null);
        dto.setStatus(P_PENDING);
        dto.setProposerId(loginUser != null ? loginUser.getUserId() : null);
        proposalMapper.insert(dto);
        log.info("创建提案: id={}, title={}, proposer={}", dto.getId(), dto.getTitle(),
                loginUser != null ? loginUser.getUsername() : "system");
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateProposal(Long id, ImprovementProposal dto) {
        ImprovementProposal exists = proposalDetail(id);
        if (exists.getStatus() != P_PENDING && exists.getStatus() != P_REJECTED) {
            throw new BusinessException(409, "仅待审核/已驳回的提案可修改");
        }
        exists.setTitle(dto.getTitle());
        exists.setProjectId(dto.getProjectId());
        exists.setProposalType(dto.getProposalType());
        exists.setCurrentState(dto.getCurrentState());
        exists.setTargetState(dto.getTargetState());
        exists.setProposalContent(dto.getProposalContent());
        // 驳回后修改可重新提交评审
        if (exists.getStatus() == P_REJECTED) {
            exists.setStatus(P_PENDING);
            exists.setReviewResult(null);
            exists.setReviewComment(null);
        }
        proposalMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProposal(Long id) {
        ImprovementProposal exists = proposalDetail(id);
        if (exists.getStatus() != P_PENDING && exists.getStatus() != P_REJECTED) {
            throw new BusinessException(409, "仅待审核/已驳回的提案可删除");
        }
        proposalMapper.deleteById(id);
    }

    /**
     * 评审提案：approved=true 通过（→审核通过），false 驳回（→已驳回+意见）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewProposal(Long id, boolean approved, String comment) {
        ImprovementProposal p = proposalDetail(id);
        if (p.getStatus() != P_PENDING) {
            throw new BusinessException(409, "仅待审核的提案可评审，当前: " + statusName(p.getStatus()));
        }
        p.setReviewResult(approved ? 1 : 2);
        p.setReviewComment(comment);
        p.setStatus(approved ? P_APPROVED : P_REJECTED);
        proposalMapper.updateById(p);
        LoginUser loginUser = LoginUser.current();
        log.info("提案评审: id={}, result={}, operator={}", id, approved ? "通过" : "驳回",
                loginUser != null ? loginUser.getUsername() : "system");
    }

    /**
     * 推进状态：审核通过 → 实施中 → 已完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void advanceProposal(Long id) {
        ImprovementProposal p = proposalDetail(id);
        if (p.getStatus() == P_APPROVED) {
            p.setStatus(P_IMPLEMENTING);
        } else if (p.getStatus() == P_IMPLEMENTING) {
            p.setStatus(P_DONE);
        } else {
            throw new BusinessException(409, "当前状态不可推进: " + statusName(p.getStatus()));
        }
        proposalMapper.updateById(p);
    }

    public static String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case P_PENDING -> "待审核";
            case P_APPROVED -> "审核通过";
            case P_IMPLEMENTING -> "实施中";
            case P_DONE -> "已完成";
            case P_REJECTED -> "已驳回";
            default -> "未知";
        };
    }

    // ==================== 根因分析 ====================

    public PageResult<RootCauseAnalysis> listRcas(Integer pageNum, Integer pageSize,
                                                  Long projectId, String analysisMethod, Integer status) {
        Page<RootCauseAnalysis> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<RootCauseAnalysis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, RootCauseAnalysis::getProjectId, projectId);
        wrapper.eq(StringUtils.hasText(analysisMethod), RootCauseAnalysis::getAnalysisMethod, analysisMethod);
        wrapper.eq(status != null, RootCauseAnalysis::getStatus, status);
        wrapper.orderByDesc(RootCauseAnalysis::getCreateTime);
        rcaMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    public RootCauseAnalysis rcaDetail(Long id) {
        RootCauseAnalysis r = rcaMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "根因分析不存在");
        }
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRca(RootCauseAnalysis dto) {
        LoginUser loginUser = LoginUser.current();
        if (dto.getProblemDesc() == null || dto.getProblemDesc().isBlank()) {
            throw new BusinessException(400, "问题描述不能为空");
        }
        dto.setId(null);
        dto.setStatus(0);
        dto.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");
        rcaMapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRca(Long id, RootCauseAnalysis dto) {
        RootCauseAnalysis exists = rcaDetail(id);
        if (exists.getStatus() == 2) {
            throw new BusinessException(409, "已关闭的分析不可修改");
        }
        exists.setProjectId(dto.getProjectId());
        exists.setProblemDesc(dto.getProblemDesc());
        exists.setAnalysisMethod(dto.getAnalysisMethod());
        exists.setFindings(dto.getFindings());
        exists.setRootCause(dto.getRootCause());
        exists.setCorrectiveAction(dto.getCorrectiveAction());
        exists.setPreventiveAction(dto.getPreventiveAction());
        rcaMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void advanceRca(Long id, Integer targetStatus) {
        RootCauseAnalysis r = rcaDetail(id);
        if (targetStatus == null || targetStatus < 0 || targetStatus > 2) {
            throw new BusinessException(400, "目标状态不合法");
        }
        // 只允许前进：0分析中 → 1已完成 → 2已关闭
        if (targetStatus <= r.getStatus()) {
            throw new BusinessException(409, "状态只能向前推进");
        }
        r.setStatus(targetStatus);
        rcaMapper.updateById(r);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRca(Long id) {
        rcaDetail(id);
        rcaMapper.deleteById(id);
    }

    // ==================== 经验教训 ====================

    public PageResult<LessonsLearned> listLessons(Integer pageNum, Integer pageSize,
                                                  Long projectId, String category, String keyword) {
        Page<LessonsLearned> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<LessonsLearned> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, LessonsLearned::getProjectId, projectId);
        wrapper.eq(StringUtils.hasText(category), LessonsLearned::getCategory, category);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(LessonsLearned::getTitle, keyword)
                    .or().like(LessonsLearned::getContent, keyword)
                    .or().like(LessonsLearned::getKeywords, keyword));
        }
        wrapper.orderByDesc(LessonsLearned::getCreateTime);
        lessonMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    public LessonsLearned lessonDetail(Long id) {
        LessonsLearned l = lessonMapper.selectById(id);
        if (l == null) {
            throw new BusinessException(404, "经验记录不存在");
        }
        return l;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createLesson(LessonsLearned dto) {
        LoginUser loginUser = LoginUser.current();
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(400, "经验标题不能为空");
        }
        if (dto.getProjectId() == null) {
            throw new BusinessException(400, "所属项目不能为空");
        }
        dto.setId(null);
        dto.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");
        lessonMapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateLesson(Long id, LessonsLearned dto) {
        LessonsLearned exists = lessonDetail(id);
        exists.setPhaseId(dto.getPhaseId());
        exists.setTitle(dto.getTitle());
        exists.setCategory(dto.getCategory());
        exists.setContent(dto.getContent());
        exists.setImpactLevel(dto.getImpactLevel());
        exists.setKeywords(dto.getKeywords());
        lessonMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteLesson(Long id) {
        lessonDetail(id);
        lessonMapper.deleteById(id);
    }

    // ==================== A3 报告 ====================

    public PageResult<A3Report> listA3s(Integer pageNum, Integer pageSize, Long projectId, Integer status) {
        Page<A3Report> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<A3Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, A3Report::getProjectId, projectId);
        wrapper.eq(status != null, A3Report::getStatus, status);
        wrapper.orderByDesc(A3Report::getCreateTime);
        a3Mapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    public A3Report a3Detail(Long id) {
        A3Report a = a3Mapper.selectById(id);
        if (a == null) {
            throw new BusinessException(404, "A3报告不存在");
        }
        return a;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createA3(A3Report dto) {
        LoginUser loginUser = LoginUser.current();
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(400, "报告标题不能为空");
        }
        dto.setId(null);
        dto.setStatus(0);
        dto.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");
        a3Mapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateA3(Long id, A3Report dto) {
        A3Report exists = a3Detail(id);
        if (exists.getStatus() == 3) {
            throw new BusinessException(409, "已关闭的A3报告不可修改");
        }
        exists.setProjectId(dto.getProjectId());
        exists.setProposalId(dto.getProposalId());
        exists.setTitle(dto.getTitle());
        exists.setBackground(dto.getBackground());
        exists.setCurrentCondition(dto.getCurrentCondition());
        exists.setGoal(dto.getGoal());
        exists.setRootCause(dto.getRootCause());
        exists.setCountermeasures(dto.getCountermeasures());
        exists.setCheckResult(dto.getCheckResult());
        exists.setFollowUp(dto.getFollowUp());
        a3Mapper.updateById(exists);
    }

    /**
     * A3 状态推进：0草稿 → 1进行中 → 2已完成 → 3已关闭（单向）
     */
    @Transactional(rollbackFor = Exception.class)
    public void advanceA3(Long id, Integer targetStatus) {
        A3Report a = a3Detail(id);
        if (targetStatus == null || targetStatus < 0 || targetStatus > 3) {
            throw new BusinessException(400, "目标状态不合法");
        }
        if (targetStatus <= a.getStatus()) {
            throw new BusinessException(409, "状态只能向前推进");
        }
        a.setStatus(targetStatus);
        a3Mapper.updateById(a);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteA3(Long id) {
        a3Detail(id);
        a3Mapper.deleteById(id);
    }

    // ==================== 价值流记录 ====================

    public PageResult<ValueStreamRecord> listValueStreams(Integer pageNum, Integer pageSize, Long projectId) {
        Page<ValueStreamRecord> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<ValueStreamRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, ValueStreamRecord::getProjectId, projectId);
        wrapper.orderByDesc(ValueStreamRecord::getRecordDate);
        vsMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /**
     * 保存价值流记录（流程效率 = 增值时间 / 周期时间 * 100，自动计算）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveValueStream(ValueStreamRecord dto) {
        if (dto.getProjectId() == null) {
            throw new BusinessException(400, "所属项目不能为空");
        }
        if (dto.getCycleTime() != null && dto.getCycleTime().compareTo(java.math.BigDecimal.ZERO) > 0
                && dto.getValueAddedTime() != null) {
            dto.setEfficiencyRate(dto.getValueAddedTime()
                    .divide(dto.getCycleTime(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new java.math.BigDecimal("100"))
                    .setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (dto.getId() == null) {
            vsMapper.insert(dto);
        } else {
            vsMapper.updateById(dto);
        }
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteValueStream(Long id) {
        if (vsMapper.selectById(id) == null) {
            throw new BusinessException(404, "记录不存在");
        }
        vsMapper.deleteById(id);
    }

    // ==================== 浪费记录 ====================

    public PageResult<WasteRecord> listWastes(Integer pageNum, Integer pageSize,
                                              Long projectId, String wasteType, Integer status) {
        Page<WasteRecord> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<WasteRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, WasteRecord::getProjectId, projectId);
        wrapper.eq(StringUtils.hasText(wasteType), WasteRecord::getWasteType, wasteType);
        wrapper.eq(status != null, WasteRecord::getStatus, status);
        wrapper.orderByAsc(WasteRecord::getSeverity).orderByDesc(WasteRecord::getCreateTime);
        wasteMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveWaste(WasteRecord dto) {
        if (dto.getProjectId() == null) {
            throw new BusinessException(400, "所属项目不能为空");
        }
        if (dto.getSeverity() == null) {
            dto.setSeverity(2);
        }
        if (dto.getStatus() == null) {
            dto.setStatus(0);
        }
        if (dto.getId() == null) {
            wasteMapper.insert(dto);
        } else {
            wasteMapper.updateById(dto);
        }
        return dto.getId();
    }

    /**
     * 浪费状态推进：0未处理 → 1处理中 → 2已消除 → 3已关闭（单向）
     */
    @Transactional(rollbackFor = Exception.class)
    public void advanceWaste(Long id, Integer targetStatus) {
        WasteRecord w = wasteMapper.selectById(id);
        if (w == null) {
            throw new BusinessException(404, "浪费记录不存在");
        }
        if (targetStatus == null || targetStatus < 0 || targetStatus > 3) {
            throw new BusinessException(400, "目标状态不合法");
        }
        if (targetStatus <= w.getStatus()) {
            throw new BusinessException(409, "状态只能向前推进");
        }
        w.setStatus(targetStatus);
        wasteMapper.updateById(w);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteWaste(Long id) {
        if (wasteMapper.selectById(id) == null) {
            throw new BusinessException(404, "记录不存在");
        }
        wasteMapper.deleteById(id);
    }
}
