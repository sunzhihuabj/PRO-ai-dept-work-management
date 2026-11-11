package com.dept.mgmt.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.CompanyQualificationMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.TenderBidMapper;
import com.dept.mgmt.mapper.TenderCompetitorMapper;
import com.dept.mgmt.mapper.TenderNoticeMapper;
import com.dept.mgmt.mapper.TenderQualificationCheckMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.tender.entity.CompanyQualification;
import com.dept.mgmt.tender.entity.TenderBid;
import com.dept.mgmt.tender.entity.TenderCompetitor;
import com.dept.mgmt.tender.entity.TenderNotice;
import com.dept.mgmt.tender.entity.TenderQualificationCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * W1 投标域服务（T-301）
 * 标讯全生命周期（NEW→FOLLOWED→APPLIED→PREPARED→DECIDED→WON/LOST）；
 * AGT-02 资格比对：确定性关键词+等级比对引擎（不经 LLM，可复核）；
 * AGT-04 报价建议：历史成交价匹配区间 + 毛利预警（确定性）；
 * AGT-05 竞品分析：开标价统计 + 策略规则；
 * AGT-03 招标文件分析结果由 handler 经网关 LLM 产出后回填 analysis_json；
 * 中标公示 → 发布 tender.won 事件（联动 W2 谈判启动/看板中标指标）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderService {

    /** 资质等级序（数值越大越高） */
    private static final Map<String, Integer> GRADE_RANK = Map.of(
            "特级", 4, "一级", 3, "壹级", 3, "二级", 2, "贰级", 2, "三级", 1, "叁级", 1);
    private static final Pattern GRADE_PATTERN = Pattern.compile("(特级|一级|壹级|二级|贰级|三级|叁级)");
    private static final List<String> STATUS_FLOW = List.of(
            "NEW", "FOLLOWED", "APPLIED", "PREPARED", "DECIDED", "WON", "LOST");

    private final TenderNoticeMapper noticeMapper;
    private final CompanyQualificationMapper qualificationMapper;
    private final TenderQualificationCheckMapper checkMapper;
    private final TenderBidMapper bidMapper;
    private final TenderCompetitorMapper competitorMapper;
    private final ProjectMapper projectMapper;
    private final CodeEngineService codeEngineService;
    private final EventBusService eventBusService;

    // ===== 标讯生命周期 =====

    @Transactional(rollbackFor = Exception.class)
    public Long createTender(String title, String platform, BigDecimal budget, BigDecimal deposit,
                             String qualificationReq, LocalDate deadline, LocalDate publishDate,
                             String importSource, String rawText) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "标讯标题不能为空");
        }
        TenderNotice notice = new TenderNotice();
        notice.setTenderNo(codeEngineService.nextCode("TN", null));
        notice.setTitle(title);
        notice.setPlatform(platform);
        notice.setBudget(budget);
        notice.setDeposit(deposit);
        notice.setQualificationReq(qualificationReq);
        notice.setDeadline(deadline);
        notice.setPublishDate(publishDate == null ? LocalDate.now() : publishDate);
        notice.setStatus("NEW");
        notice.setImportSource(importSource == null || importSource.isBlank() ? "MANUAL" : importSource);
        notice.setRawText(rawText);
        noticeMapper.insert(notice);
        log.info("标讯建档: {} {} 预算 {} 截止 {}", notice.getTenderNo(), title, budget, deadline);
        return notice.getId();
    }

    /** 标讯状态推进（DECIDED→WON 时发布 tender.won 事件） */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long tenderId, String status, String remark) {
        TenderNotice notice = mustExistTender(tenderId);
        if (!STATUS_FLOW.contains(status)) {
            throw new BusinessException(400, "标讯状态仅允许 " + STATUS_FLOW);
        }
        String old = notice.getStatus();
        notice.setStatus(status);
        if (remark != null && !remark.isBlank()) {
            notice.setRemark(remark);
        }
        noticeMapper.updateById(notice);
        if ("WON".equals(status) && !"WON".equals(old)) {
            // 中标公示 → W2 启动谈判/看板中标指标（跨域事件）
            eventBusService.publish(DomainEventType.TENDER_WON, Map.of(
                    "tenderId", notice.getId(),
                    "tenderNo", notice.getTenderNo(),
                    "title", notice.getTitle(),
                    "budget", String.valueOf(notice.getBudget())));
            log.info("tender.won 事件已发布: {}", notice.getTenderNo());
        }
        log.info("标讯状态推进: {} {} → {}", notice.getTenderNo(), old, status);
    }

    public PageResult<TenderNotice> listTenders(int pageNum, int pageSize, String status, String keyword) {
        LambdaQueryWrapper<TenderNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), TenderNotice::getStatus, status);
        wrapper.like(keyword != null && !keyword.isBlank(), TenderNotice::getTitle, keyword);
        wrapper.orderByDesc(TenderNotice::getId);
        return PageResult.of(noticeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public TenderNotice detail(Long tenderId) {
        return mustExistTender(tenderId);
    }

    /** AGT-03 分析结果回填（analysis_json 存证） */
    @Transactional(rollbackFor = Exception.class)
    public void saveAnalysis(Long tenderId, String analysisJson) {
        TenderNotice notice = mustExistTender(tenderId);
        notice.setAnalysisJson(analysisJson);
        noticeMapper.updateById(notice);
    }

    // ===== AGT-02 资格比对（确定性引擎）=====

    /**
     * 资格比对：资质要求原文拆项 → 逐项与公司资质清单关键词+等级比对 → 结果落库
     * 等级规则：要求等级 > 公司等级 → 不满足；无等级要求关键词命中即满足；无法判断 → 待核实
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkQualification(Long tenderId, String checkedBy) {
        TenderNotice notice = mustExistTender(tenderId);
        if (notice.getQualificationReq() == null || notice.getQualificationReq().isBlank()) {
            throw new BusinessException(400, "标讯未含资质要求，无法比对（" + notice.getTenderNo() + "）");
        }
        List<CompanyQualification> quals = qualificationMapper.selectList(null);
        List<String> requirements = splitRequirements(notice.getQualificationReq());
        List<TenderQualificationCheck> results = new ArrayList<>();
        for (String requirement : requirements) {
            TenderQualificationCheck check = new TenderQualificationCheck();
            check.setTenderId(tenderId);
            check.setRequirement(requirement);
            check.setCheckedBy(checkedBy == null || checkedBy.isBlank() ? "AGT-02" : checkedBy);
            resolveRequirement(requirement, quals, check);
            checkMapper.insert(check);
            results.add(check);
        }
        long satisfied = results.stream().filter(c -> "SATISFIED".equals(c.getCompanyStatus())).count();
        long notSatisfied = results.stream().filter(c -> "NOT_SATISFIED".equals(c.getCompanyStatus())).count();
        long uncertain = results.stream().filter(c -> "UNCERTAIN".equals(c.getCompanyStatus())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenderId", tenderId);
        out.put("tenderNo", notice.getTenderNo());
        out.put("total", results.size());
        out.put("satisfied", satisfied);
        out.put("notSatisfied", notSatisfied);
        out.put("uncertain", uncertain);
        out.put("verdict", notSatisfied > 0 ? "NOT_QUALIFIED" : (uncertain > 0 ? "NEED_REVIEW" : "QUALIFIED"));
        out.put("checks", results);
        log.info("AGT-02 资格比对: tenderId={} 满足 {} 不满足 {} 待核实 {}", tenderId, satisfied, notSatisfied, uncertain);
        return out;
    }

    /** 资质要求拆项（分号/换行/中文分号分隔） */
    private List<String> splitRequirements(String text) {
        return Arrays.stream(text.split("[;；\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void resolveRequirement(String requirement, List<CompanyQualification> quals,
                                    TenderQualificationCheck check) {
        // 1. 关键词命中：取要求项中的资质领域词，在公司资质名中匹配
        CompanyQualification hit = quals.stream()
                .filter(q -> keywordMatch(requirement, q.getQualName()))
                .max(Comparator.comparingInt(q -> gradeRank(q.getQualName())))
                .orElse(null);
        Integer requiredRank = gradeRank(requirement);
        if (hit == null) {
            check.setCompanyStatus("UNCERTAIN");
            check.setEvidence("资质清单未命中关键词，需人工核实: " + requirement);
            return;
        }
        if (requiredRank != null && requiredRank > gradeRank(hit.getQualName())) {
            check.setCompanyStatus("NOT_SATISFIED");
            check.setEvidence("等级不足：要求" + gradeText(requirement) + "，公司持有「" + hit.getQualName() + "」");
            return;
        }
        // 有效期校验
        if (hit.getValidUntil() != null && hit.getValidUntil().isBefore(LocalDate.now())) {
            check.setCompanyStatus("NOT_SATISFIED");
            check.setEvidence("资质已过期：" + hit.getQualName() + " 有效期至 " + hit.getValidUntil());
            return;
        }
        check.setCompanyStatus("SATISFIED");
        check.setEvidence("命中公司资质「" + hit.getQualName() + "」"
                + (hit.getValidUntil() == null ? "" : "，有效期至 " + hit.getValidUntil()));
    }

    /** 关键词匹配：要求项与资质名的领域词交集（电力工程/钢结构/ISO9001/安全生产/起重/建筑/机电 等） */
    private boolean keywordMatch(String requirement, String qualName) {
        List<String> domains = List.of("电力工程", "钢结构", "ISO9001", "质量管理体系", "安全生产", "起重",
                "建筑工程", "机电", "防腐", "保温", "环保", "施工总承包", "专业承包");
        for (String domain : domains) {
            if (requirement.contains(domain) && qualName.contains(domain)) {
                return true;
            }
        }
        // 兜底：要求项含资质名的主要片段（≥4 字连续）
        if (qualName.length() >= 4) {
            String head = qualName.substring(0, Math.min(qualName.length(), 6));
            return requirement.contains(head);
        }
        return false;
    }

    private Integer gradeRank(String text) {
        Matcher m = GRADE_PATTERN.matcher(text);
        return m.find() ? GRADE_RANK.get(m.group(1)) : null;
    }

    private String gradeText(String text) {
        Matcher m = GRADE_PATTERN.matcher(text);
        return m.find() ? m.group(1) : "未标明等级";
    }

    // ===== AGT-04 报价建议（确定性）=====

    /**
     * 报价建议：历史成交价（已完成项目合同额均值）匹配报价区间；
     * 无历史数据时按预算折价区间；低毛利预警按行业口径 15% 阈值
     */
    public Map<String, Object> suggestPrice(Long tenderId) {
        TenderNotice notice = mustExistTender(tenderId);
        List<Project> history = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, 2)
                .isNotNull(Project::getContractAmount)
                .gt(Project::getContractAmount, BigDecimal.ZERO));
        if (history.isEmpty()) {
            history = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .isNotNull(Project::getContractAmount)
                    .gt(Project::getContractAmount, BigDecimal.ZERO));
        }
        BigDecimal histAvg = BigDecimal.ZERO;
        if (!history.isEmpty()) {
            histAvg = history.stream().map(Project::getContractAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
        }
        BigDecimal low, high;
        if (histAvg.compareTo(BigDecimal.ZERO) > 0) {
            low = histAvg.multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP);
            high = histAvg.multiply(new BigDecimal("1.05")).setScale(2, RoundingMode.HALF_UP);
        } else if (notice.getBudget() != null && notice.getBudget().compareTo(BigDecimal.ZERO) > 0) {
            low = notice.getBudget().multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);
            high = notice.getBudget().multiply(new BigDecimal("0.95")).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new BusinessException(400, "无历史成交价且标讯无预算，无法生成报价区间");
        }
        BigDecimal suggested = low.add(high).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        // 低毛利预警：suggested 相对 budget 折让超 15% 时提示
        List<String> warnings = new ArrayList<>();
        if (notice.getBudget() != null && notice.getBudget().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountRate = BigDecimal.ONE.subtract(suggested.divide(notice.getBudget(), 4, RoundingMode.HALF_UP));
            if (discountRate.compareTo(new BigDecimal("0.15")) > 0) {
                warnings.add("建议价较预算折让 " + discountRate.multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP) + "%，存在低毛利风险，需成本归集（W7）复核后再决策");
            }
        }
        if (notice.getDeadline() != null && notice.getDeadline().isBefore(LocalDate.now().plusDays(7))) {
            warnings.add("投标截止临近（" + notice.getDeadline() + "），请预留三级内审时间");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenderId", tenderId);
        out.put("tenderNo", notice.getTenderNo());
        out.put("budget", notice.getBudget());
        out.put("historyAvg", histAvg);
        out.put("historyCount", history.size());
        out.put("range", Map.of("low", low, "suggested", suggested, "high", high));
        out.put("warnings", warnings);
        log.info("AGT-04 报价建议: tenderId={} 区间 [{}, {}]", tenderId, low, high);
        return out;
    }

    // ===== AGT-05 竞品分析（确定性统计+策略规则）=====

    public Map<String, Object> analyzeCompetitors(Long tenderId) {
        TenderNotice notice = mustExistTender(tenderId);
        List<TenderCompetitor> competitors = competitorMapper.selectList(new LambdaQueryWrapper<TenderCompetitor>()
                .eq(TenderCompetitor::getTenderId, tenderId));
        if (competitors.isEmpty()) {
            throw new BusinessException(400, "未录入竞品开标记录，无法分析（" + notice.getTenderNo() + "）");
        }
        List<BigDecimal> prices = competitors.stream()
                .filter(c -> c.getPrice() != null)
                .map(TenderCompetitor::getPrice)
                .sorted()
                .collect(Collectors.toList());
        BigDecimal avg = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
        BigDecimal min = prices.get(0);
        BigDecimal max = prices.get(prices.size() - 1);
        TenderBid ourBid = bidMapper.selectOne(new LambdaQueryWrapper<TenderBid>()
                .eq(TenderBid::getTenderId, tenderId).orderByDesc(TenderBid::getId).last("LIMIT 1"));
        // 策略规则（确定性）
        List<String> strategies = new ArrayList<>();
        BigDecimal budget = notice.getBudget() == null ? BigDecimal.ZERO : notice.getBudget();
        if (min.compareTo(avg.multiply(new BigDecimal("0.85"))) < 0) {
            strategies.add("存在明显低价竞标者（最低价低于均值 15%），避免单纯比价，转向差异化方案与履约能力背书");
        }
        if (ourBid != null && ourBid.getPrice() != null) {
            int cheaper = (int) prices.stream().filter(p -> p.compareTo(ourBid.getPrice()) < 0).count();
            strategies.add("我方报价 " + ourBid.getPrice() + " 位于 " + (cheaper + 1) + "/" + (prices.size() + 1)
                    + " 位；" + (cheaper == 0 ? "价格占优，重点保障技术分" : "价格不占优，需强化技术分与业绩分"));
            if (budget.compareTo(BigDecimal.ZERO) > 0
                    && ourBid.getPrice().compareTo(budget.multiply(new BigDecimal("0.9"))) < 0) {
                strategies.add("我方报价低于预算 90%，核实成本口径防止亏本中标");
            }
        } else {
            strategies.add("尚未录入我方报价，建议围绕均价 " + avg + " 制定报价策略");
        }
        strategies.add("重点竞品：" + competitors.stream()
                .max(Comparator.comparing(c -> c.getPrice() == null ? BigDecimal.ZERO : c.getPrice()))
                .map(TenderCompetitor::getCompetitorName).orElse(""));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenderId", tenderId);
        out.put("tenderNo", notice.getTenderNo());
        out.put("competitorCount", competitors.size());
        out.put("avgPrice", avg);
        out.put("minPrice", min);
        out.put("maxPrice", max);
        out.put("ourPrice", ourBid == null ? null : ourBid.getPrice());
        out.put("strategies", strategies);
        out.put("competitors", competitors);
        log.info("AGT-05 竞品分析: tenderId={} 竞品 {} 家 均价 {}", tenderId, competitors.size(), avg);
        return out;
    }

    // ===== 报价/竞品记录 =====

    @Transactional(rollbackFor = Exception.class)
    public Long saveBid(Long tenderId, BigDecimal price, BigDecimal costEstimate, String strategyText,
                        LocalDate openDate, String openResult) {
        mustExistTender(tenderId);
        TenderBid bid = new TenderBid();
        bid.setTenderId(tenderId);
        bid.setPrice(price);
        bid.setCostEstimate(costEstimate);
        if (price != null && costEstimate != null && price.compareTo(BigDecimal.ZERO) > 0) {
            bid.setGrossMargin(price.subtract(costEstimate)
                    .divide(price, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        }
        bid.setStrategyText(strategyText);
        bid.setOpenDate(openDate);
        bid.setOpenResult(openResult);
        bidMapper.insert(bid);
        return bid.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addCompetitor(Long tenderId, String competitorName, BigDecimal price, String note) {
        mustExistTender(tenderId);
        TenderCompetitor competitor = new TenderCompetitor();
        competitor.setTenderId(tenderId);
        competitor.setCompetitorName(competitorName);
        competitor.setPrice(price);
        competitor.setNote(note);
        competitorMapper.insert(competitor);
        return competitor.getId();
    }

    public List<TenderBid> listBids(Long tenderId) {
        return bidMapper.selectList(new LambdaQueryWrapper<TenderBid>()
                .eq(tenderId != null, TenderBid::getTenderId, tenderId)
                .orderByDesc(TenderBid::getId));
    }

    public List<TenderCompetitor> listCompetitors(Long tenderId) {
        return competitorMapper.selectList(new LambdaQueryWrapper<TenderCompetitor>()
                .eq(tenderId != null, TenderCompetitor::getTenderId, tenderId)
                .orderByDesc(TenderCompetitor::getId));
    }

    public List<TenderQualificationCheck> listChecks(Long tenderId) {
        return checkMapper.selectList(new LambdaQueryWrapper<TenderQualificationCheck>()
                .eq(TenderQualificationCheck::getTenderId, tenderId)
                .orderByAsc(TenderQualificationCheck::getId));
    }

    private TenderNotice mustExistTender(Long id) {
        TenderNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "标讯不存在: " + id);
        }
        return notice;
    }
}
