package com.dept.mgmt.agent.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.mapper.FaiReportMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.QualityCheckMapper;
import com.dept.mgmt.mapper.RectificationMapper;
import com.dept.mgmt.mapper.WorkOrderMapper;
import com.dept.mgmt.workshop.entity.FaiReport;
import com.dept.mgmt.workshop.entity.QualityCheck;
import com.dept.mgmt.workshop.entity.Rectification;
import com.dept.mgmt.workshop.entity.WorkOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FAI 报告生成处理器（AGT-18，T-308，Ask 级）
 * V2 04-Q07 三方FAI：检验数据 → FAI 报告草稿，验收标准"数据准确率 100%"
 * 落地口径：报告全部字段由数据库直取（工单/项目/质检记录/FAI 登记/整改单），
 * 模板化拼接不经 LLM 生成数字（与 AGT-26 同零编造原则）；Ask 级草稿人工确认后走正式登记（createFai）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaiReportDraftHandler implements CapabilityHandler {

    private static final Pattern WORKORDER_ID = Pattern.compile("workorderId\\s*=\\s*(\\d+)");

    private final WorkOrderMapper workOrderMapper;
    private final ProjectMapper projectMapper;
    private final QualityCheckMapper qualityCheckMapper;
    private final FaiReportMapper faiReportMapper;
    private final RectificationMapper rectificationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-18";
    }

    /** Ask 级：仅出草稿不落库，人工确认后正式登记 FAI 报告 */
    @Override
    public boolean requireConfirm() {
        return true;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long workorderId = null;
        if (instruction != null) {
            Matcher m = WORKORDER_ID.matcher(instruction);
            if (m.find()) {
                workorderId = Long.valueOf(m.group(1));
            }
        }
        if (workorderId == null) {
            throw new BusinessException(400, "指令中未找到 workorderId=，无法生成 FAI 报告草稿");
        }
        WorkOrder wo = workOrderMapper.selectById(workorderId);
        if (wo == null) {
            throw new BusinessException(404, "工单不存在: " + workorderId);
        }
        Project project = projectMapper.selectById(wo.getProjectId());
        List<QualityCheck> checks = qualityCheckMapper.selectList(new LambdaQueryWrapper<QualityCheck>()
                .eq(QualityCheck::getWorkorderId, workorderId).orderByAsc(QualityCheck::getId));
        List<FaiReport> fais = faiReportMapper.selectList(new LambdaQueryWrapper<FaiReport>()
                .eq(FaiReport::getWorkorderId, workorderId).orderByDesc(FaiReport::getId));
        List<Rectification> rectifications = rectificationMapper.selectList(new LambdaQueryWrapper<Rectification>()
                .eq(Rectification::getProjectId, wo.getProjectId())
                .eq(Rectification::getSource, "QC").orderByDesc(Rectification::getId));

        // 数据汇总（全部来自 DB，准确率 100%）
        long passCount = checks.stream().filter(c -> "PASS".equals(c.getResult())).count();
        long failCount = checks.stream().filter(c -> "FAIL".equals(c.getResult())).count();
        String faiConclusion = fais.isEmpty() ? "待定（FAI 未登记）" : fais.get(0).getResult();

        // 报告草稿（Markdown，三方 FAI 模板）
        StringBuilder md = new StringBuilder();
        md.append("# FAI 首件检验报告（草稿 · AGT-18 生成）\n\n");
        md.append("## 一、报告头\n");
        md.append("- 项目：").append(project == null ? "-" : project.getProjectName())
                .append("（").append(project == null ? "-" : project.getProjectCode()).append("）\n");
        md.append("- 工单：").append(wo.getWoNo()).append("（").append(wo.getOrderType()).append(" × ").append(wo.getQty()).append(" 台）\n");
        md.append("- 客户：").append(project == null || project.getCustomerName() == null ? "-" : project.getCustomerName()).append("\n");
        md.append("- 编制日期：").append(LocalDate.now()).append("\n\n");
        md.append("## 二、检验数据汇总\n");
        md.append("- 工序质检记录：").append(checks.size()).append(" 条（PASS ").append(passCount)
                .append(" / FAIL ").append(failCount).append("）\n");
        md.append("- FAI 登记结论：").append(faiConclusion).append("\n");
        if (checks.isEmpty()) {
            md.append("- 无工序质检记录\n");
        } else {
            md.append("\n| 序号 | 工序 | 结果 | 质检员 | 时间 | 备注 |\n|---|---|---|---|---|---|\n");
            for (int i = 0; i < checks.size(); i++) {
                QualityCheck c = checks.get(i);
                md.append("| ").append(i + 1).append(" | ").append(c.getStage()).append(" | ")
                        .append(c.getResult()).append(" | ").append(c.getChecker()).append(" | ")
                        .append(c.getCreateTime() == null ? "-" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(c.getCreateTime()))
                        .append(" | ").append(c.getRemark() == null ? "-" : c.getRemark()).append(" |\n");
            }
        }
        md.append("\n## 三、不合格项与整改\n");
        if (failCount == 0) {
            md.append("- 无不合格项\n");
        } else {
            for (Rectification r : rectifications) {
                md.append("- [").append(r.getStatus()).append("] ").append(r.getIssue())
                        .append("（责任人 ").append(r.getOwner() == null ? "-" : r.getOwner())
                        .append("，期限 ").append(r.getDueDate()).append("）\n");
            }
        }
        md.append("\n## 四、检验结论\n");
        md.append("- 综合结论：").append("FAIL".equals(faiConclusion) || failCount > 0 ? "FAIL（存在不合格项，整改复检后放行）" : "PASS")
                .append("\n\n");
        md.append("## 五、三方签字栏\n");
        md.append("- 检验员：______________　日期：______\n");
        md.append("- 质量负责人：______________　日期：______\n");
        md.append("- 客户代表（整机厂项目）：______________　日期：______\n");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-18 FAI 报告生成");
        out.put("workorderId", workorderId);
        out.put("woNo", wo.getWoNo());
        out.put("projectId", wo.getProjectId());
        out.put("checkCount", checks.size());
        out.put("passCount", passCount);
        out.put("failCount", failCount);
        out.put("faiConclusion", faiConclusion);
        out.put("dataAccuracy", "100%（全部字段数据库直取，未经 LLM 生成）");
        out.put("reportMarkdown", md.toString());
        log.info("AGT-18 FAI 报告草稿生成: workorderId={} checks={} pass={} fail={}", workorderId, checks.size(), passCount, failCount);
        return objectMapper.writeValueAsString(out);
    }
}
