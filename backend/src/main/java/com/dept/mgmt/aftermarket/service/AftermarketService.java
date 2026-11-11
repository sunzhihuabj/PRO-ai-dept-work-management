package com.dept.mgmt.aftermarket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.aftermarket.entity.InspectionRecord;
import com.dept.mgmt.aftermarket.entity.RepairTicket;
import com.dept.mgmt.aftermarket.entity.WarrantyInfo;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.mapper.InspectionRecordMapper;
import com.dept.mgmt.mapper.RepairTicketMapper;
import com.dept.mgmt.mapper.WarrantyInfoMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W8 售后域服务（T-304）
 * AGT-23 报修受理分级（Auto，确定性规则分级——透明披露：比 LLM 分级更稳定可复核）：
 *   紧急=描述含 安全/事故/停机/着火/漏电/坍塌/伤亡；咨询=含 咨询/询问/了解 且无紧急词；其余=一般
 *   响应 SLA：紧急 2 小时 / 一般 24 小时 / 咨询 48 小时
 * AGT-24 质保金提醒（Auto）：质保期满前 30 天提醒 B-32 质保金申请（幂等 reminded 标记）
 * 远程诊断建议（B-04 定位）：历史已闭环工单根因/方案 关键词检索 TOP3（确定性，知识库 RAG 就绪后切换）
 * 故障统计月报（A-09）：按月统计报修量/分级分布/闭环率/平均闭环时长
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AftermarketService {

    private static final List<String> EMERGENCY_WORDS = List.of("安全", "事故", "停机", "着火", "漏电", "坍塌", "伤亡");
    private static final List<String> INQUIRY_WORDS = List.of("咨询", "询问", "了解", "请教");
    private static final Map<String, String> SLA = Map.of(
            "EMERGENCY", "紧急：2 小时内响应",
            "GENERAL", "一般：24 小时内响应",
            "INQUIRY", "咨询：48 小时内答复");
    private static final List<String> INSPECTION_TYPES = List.of("HALF_YEAR", "ANNUAL");

    private final RepairTicketMapper repairMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final WarrantyInfoMapper warrantyMapper;
    private final CodeEngineService codeEngineService;

    // ===== 报修工单 =====

    /**
     * 报修受理 + 自动分级（AGT-23 规则引擎）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRepair(Long projectId, String faultDesc, String reporter) {
        if (faultDesc == null || faultDesc.isBlank()) {
            throw new BusinessException(400, "故障描述不能为空");
        }
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(400, "必须关联项目 projectId");
        }
        RepairTicket ticket = new RepairTicket();
        ticket.setTicketNo(codeEngineService.nextCode("RP", null));
        ticket.setProjectId(projectId);
        ticket.setFaultDesc(faultDesc);
        ticket.setFaultTime(LocalDateTime.now());
        String level = classify(faultDesc);
        ticket.setLevel(level);
        ticket.setResponseSla(SLA.get(level));
        ticket.setStatus("OPEN");
        ticket.setReporter(reporter);
        repairMapper.insert(ticket);
        log.info("报修受理: {} 级别 {} SLA {}", ticket.getTicketNo(), level, ticket.getResponseSla());
        return ticket.getId();
    }

    /** AGT-23 分级规则（确定性，可复核） */
    public String classify(String faultDesc) {
        String text = faultDesc == null ? "" : faultDesc;
        boolean emergency = EMERGENCY_WORDS.stream().anyMatch(text::contains);
        if (emergency) {
            return "EMERGENCY";
        }
        boolean inquiry = INQUIRY_WORDS.stream().anyMatch(text::contains);
        return inquiry ? "INQUIRY" : "GENERAL";
    }

    /** 开始处理 */
    @Transactional(rollbackFor = Exception.class)
    public void startProcessing(Long ticketId, String handler) {
        RepairTicket ticket = mustExistTicket(ticketId);
        if (!"OPEN".equals(ticket.getStatus())) {
            throw new BusinessException(400, "仅待响应工单可开始处理，当前: " + ticket.getStatus());
        }
        ticket.setStatus("PROCESSING");
        ticket.setHandler(handler);
        repairMapper.updateById(ticket);
    }

    /** 闭环（根因/方案必填，A-06 故障闭环） */
    @Transactional(rollbackFor = Exception.class)
    public void closeRepair(Long ticketId, String rootCause, String solution) {
        RepairTicket ticket = mustExistTicket(ticketId);
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new BusinessException(400, "工单已闭环，不允许重复闭环");
        }
        if (rootCause == null || rootCause.isBlank() || solution == null || solution.isBlank()) {
            throw new BusinessException(400, "闭环必须填写根因与解决方案（A-06 故障闭环）");
        }
        ticket.setStatus("CLOSED");
        ticket.setRootCause(rootCause);
        ticket.setSolution(solution);
        ticket.setClosedAt(LocalDateTime.now());
        repairMapper.updateById(ticket);
        log.info("报修闭环: {} 根因 {}", ticket.getTicketNo(), rootCause);
    }

    /**
     * 远程诊断建议（B-04）：历史已闭环工单按故障描述关键词检索根因/方案 TOP3
     */
    public List<Map<String, Object>> diagnosisSuggest(Long ticketId) {
        RepairTicket ticket = mustExistTicket(ticketId);
        String keyword = extractKeyword(ticket.getFaultDesc());
        List<RepairTicket> closed = repairMapper.selectList(new LambdaQueryWrapper<RepairTicket>()
                .eq(RepairTicket::getStatus, "CLOSED")
                .ne(RepairTicket::getId, ticketId)
                .orderByDesc(RepairTicket::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (RepairTicket history : closed) {
            boolean match = (history.getRootCause() != null && history.getRootCause().contains(keyword))
                    || (history.getSolution() != null && history.getSolution().contains(keyword))
                    || keywordMatchDesc(history.getFaultDesc(), ticket.getFaultDesc());
            if (match) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ticketNo", history.getTicketNo());
                row.put("faultDesc", history.getFaultDesc());
                row.put("rootCause", history.getRootCause());
                row.put("solution", history.getSolution());
                result.add(row);
            }
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    /** 取故障描述匹配关键词：优先分级领域词（安全/停机等），否则取前 4 字符（保守不编造） */
    private String extractKeyword(String faultDesc) {
        String text = faultDesc == null ? "" : faultDesc;
        for (String word : EMERGENCY_WORDS) {
            if (text.contains(word)) {
                return word;
            }
        }
        for (String word : INQUIRY_WORDS) {
            if (text.contains(word)) {
                return word;
            }
        }
        return text.substring(0, Math.min(4, text.length()));
    }

    private boolean keywordMatchDesc(String historyDesc, String desc) {
        return historyDesc != null && desc != null
                && (historyDesc.contains(extractKeyword(desc)));
    }

    public PageResult<RepairTicket> listRepairs(int pageNum, int pageSize, Long projectId, String status, String level) {
        LambdaQueryWrapper<RepairTicket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, RepairTicket::getProjectId, projectId);
        wrapper.eq(status != null && !status.isBlank(), RepairTicket::getStatus, status);
        wrapper.eq(level != null && !level.isBlank(), RepairTicket::getLevel, level);
        wrapper.orderByDesc(RepairTicket::getId);
        return PageResult.of(repairMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    // ===== 巡检 =====

    @Transactional(rollbackFor = Exception.class)
    public Long addInspection(Long projectId, String inspectionType, LocalDate inspectionDate,
                              String inspector, String content, String issues) {
        if (!INSPECTION_TYPES.contains(inspectionType)) {
            throw new BusinessException(400, "巡检类型仅允许 HALF_YEAR/ANNUAL");
        }
        InspectionRecord record = new InspectionRecord();
        record.setProjectId(projectId);
        record.setInspectionType(inspectionType);
        record.setInspectionDate(inspectionDate == null ? LocalDate.now() : inspectionDate);
        record.setInspector(inspector);
        record.setContent(content);
        record.setIssues(issues);
        inspectionMapper.insert(record);
        return record.getId();
    }

    public List<InspectionRecord> listInspections(Long projectId) {
        return inspectionMapper.selectList(new LambdaQueryWrapper<InspectionRecord>()
                .eq(projectId != null, InspectionRecord::getProjectId, projectId)
                .orderByDesc(InspectionRecord::getId));
    }

    // ===== 质保信息 =====

    @Transactional(rollbackFor = Exception.class)
    public Long saveWarranty(Long projectId, LocalDate warrantyEnd, BigDecimal depositAmount) {
        WarrantyInfo existing = warrantyMapper.selectOne(new LambdaQueryWrapper<WarrantyInfo>()
                .eq(WarrantyInfo::getProjectId, projectId).last("LIMIT 1"));
        WarrantyInfo info = existing == null ? new WarrantyInfo() : existing;
        info.setProjectId(projectId);
        info.setWarrantyEnd(warrantyEnd);
        info.setDepositAmount(depositAmount);
        info.setReminded(existing == null ? 0 : existing.getReminded());
        info.setStatus(existing == null ? "ACTIVE" : existing.getStatus());
        if (existing == null) {
            warrantyMapper.insert(info);
        } else {
            warrantyMapper.updateById(info);
        }
        return info.getId();
    }

    public List<WarrantyInfo> listWarranties() {
        return warrantyMapper.selectList(new LambdaQueryWrapper<WarrantyInfo>()
                .orderByAsc(WarrantyInfo::getWarrantyEnd));
    }

    // ===== AGT-24 质保金提醒 =====

    /**
     * 质保金到期提醒：质保期满前 30 天内且未提醒 → 输出提醒清单（handler 负责推送+标记）
     */
    public List<WarrantyInfo> listExpiringWarranties(int withinDays) {
        LocalDate today = LocalDate.now();
        return warrantyMapper.selectList(new LambdaQueryWrapper<WarrantyInfo>()
                .eq(WarrantyInfo::getReminded, 0)
                .eq(WarrantyInfo::getStatus, "ACTIVE")
                .isNotNull(WarrantyInfo::getWarrantyEnd)
                .le(WarrantyInfo::getWarrantyEnd, today.plusDays(withinDays))
                .ge(WarrantyInfo::getWarrantyEnd, today.minusDays(7)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markWarrantyReminded(Long warrantyId) {
        WarrantyInfo info = warrantyMapper.selectById(warrantyId);
        if (info != null) {
            info.setReminded(1);
            warrantyMapper.updateById(info);
        }
    }

    // ===== A-09 故障统计月报 =====

    public Map<String, Object> monthlyReport(String month) {
        YearMonth ym = month == null || month.isBlank()
                ? YearMonth.now() : YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        List<RepairTicket> tickets = repairMapper.selectList(new LambdaQueryWrapper<RepairTicket>()
                .ge(RepairTicket::getFaultTime, start)
                .lt(RepairTicket::getFaultTime, end));
        long emergency = tickets.stream().filter(t -> "EMERGENCY".equals(t.getLevel())).count();
        long general = tickets.stream().filter(t -> "GENERAL".equals(t.getLevel())).count();
        long inquiry = tickets.stream().filter(t -> "INQUIRY".equals(t.getLevel())).count();
        List<RepairTicket> closed = tickets.stream().filter(t -> "CLOSED".equals(t.getStatus())).toList();
        double closedRate = tickets.isEmpty() ? 0 : closed.size() * 100.0 / tickets.size();
        double avgHours = closed.stream()
                .filter(t -> t.getClosedAt() != null && t.getFaultTime() != null)
                .mapToLong(t -> ChronoUnit.MINUTES.between(t.getFaultTime(), t.getClosedAt()))
                .average().orElse(0) / 60.0;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        out.put("total", tickets.size());
        out.put("emergency", emergency);
        out.put("general", general);
        out.put("inquiry", inquiry);
        out.put("closedCount", closed.size());
        out.put("closedRate", BigDecimal.valueOf(closedRate).setScale(1, RoundingMode.HALF_UP) + "%");
        out.put("avgClosedHours", BigDecimal.valueOf(avgHours).setScale(1, RoundingMode.HALF_UP));
        out.put("tickets", tickets);
        log.info("故障月报: {} 报修 {} 紧急 {} 闭环率 {}", out.get("month"), tickets.size(), emergency, out.get("closedRate"));
        return out;
    }

    private RepairTicket mustExistTicket(Long id) {
        RepairTicket ticket = repairMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "报修工单不存在: " + id);
        }
        return ticket;
    }
}
