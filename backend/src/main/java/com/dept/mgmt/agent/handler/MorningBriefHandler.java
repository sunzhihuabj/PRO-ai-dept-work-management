package com.dept.mgmt.agent.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.board.entity.Redcard;
import com.dept.mgmt.board.service.BoardsService;
import com.dept.mgmt.mapper.RedcardMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板晨会解读处理器（AGT-26，T-210，只读 Auto）
 * 自动解读九板指标异常 + 生成晨会材料（早会红灯过堂场景）：
 * 红灯按级别/板块排序，附确定性建议行动；材料为模板化文本，不经 LLM（数据零编造）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MorningBriefHandler implements CapabilityHandler {

    private final BoardsService boardsService;
    private final RedcardMapper redcardMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-26";
    }

    /** 纯只读盘点与材料生成，不改任何业务状态，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Map<String, BigDecimal> metrics = boardsService.computeMetrics();
        List<Redcard> active = redcardMapper.selectList(new LambdaQueryWrapper<Redcard>()
                .in(Redcard::getStatus, List.of("OPEN", "ASSIGNED", "ESCALATED"))
                .orderByAsc(Redcard::getId));
        // 红灯排序：RED 优先，ESCALATED 优先
        List<Map<String, Object>> redcards = new ArrayList<>();
        active.stream().sorted((a, b) -> {
            int byLevel = Integer.compare(weight(b), weight(a));
            return byLevel != 0 ? byLevel : Long.compare(b.getId(), a.getId());
        }).forEach(card -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", card.getId());
            row.put("ruleCode", card.getRuleCode());
            row.put("ruleName", card.getRuleName());
            row.put("board", card.getBoard());
            row.put("level", card.getLevel());
            row.put("status", card.getStatus());
            row.put("metricValue", card.getMetricValue());
            row.put("owner", card.getOwner());
            row.put("dueDate", String.valueOf(card.getDueDate()));
            row.put("suggestedAction", suggestedAction(card));
            redcards.add(row);
        });

        // 重点解读（确定性规则）
        List<String> highlights = new ArrayList<>();
        if (d(metrics, "po.overdue_count") > 0) {
            highlights.add("齐套采购板：逾期 PO " + d(metrics, "po.overdue_count") + " 张，影响生产齐套，需当天催货");
        }
        if (d(metrics, "kitting.block_count") > 0) {
            highlights.add("齐套采购板：" + d(metrics, "kitting.block_count") + " 个项目齐套率低于 95%（BR-08 开工卡点）");
        }
        if (d(metrics, "disclosure.unsigned_count") > 0) {
            highlights.add("生产质检板：" + d(metrics, "disclosure.unsigned_count") + " 份技术交底未签字（BR-09 开工卡点）");
        }
        if (d(metrics, "acceptance.pending_sign") > 0) {
            highlights.add("验收收款板：" + d(metrics, "acceptance.pending_sign") + " 张验收单待三方签字（BR-05）");
        }
        if (d(metrics, "rectification.overdue_count") > 0) {
            highlights.add("整改风险板：" + d(metrics, "rectification.overdue_count") + " 张整改单逾期未闭环");
        }
        if (d(metrics, "recon.diff_count") > 0) {
            highlights.add("扫码过账板：最近对账存在 " + d(metrics, "recon.diff_count") + " 单差异，需财务复核");
        }
        if (d(metrics, "inventory.push_failed") > 0) {
            highlights.add("扫码过账板：" + d(metrics, "inventory.push_failed") + " 单 T+ 推送失败，需重推");
        }
        if (highlights.isEmpty()) {
            highlights.add("各板指标正常，无红灯风险，保持节奏");
        }

        // 晨会材料（模板化文本）
        StringBuilder md = new StringBuilder();
        md.append("# 晨会材料 · ").append(LocalDate.now()).append("\n\n");
        md.append("## 一、全公司概览\n");
        md.append("- 在库项目：").append(d(metrics, "project.total_count"))
                .append("；生效合同：").append(d(metrics, "contract.effective_count"))
                .append("；在产工单：").append(d(metrics, "wo.started_count"))
                .append("；已发运批次：").append(d(metrics, "shipment.submitted_count")).append("\n");
        md.append("## 二、红灯项（").append(redcards.size()).append(" 项活跃）\n");
        if (redcards.isEmpty()) {
            md.append("- 无活跃红灯\n");
        } else {
            for (Map<String, Object> card : redcards) {
                md.append("- [").append(card.get("level")).append("/").append(card.get("status")).append("] ")
                        .append(card.get("ruleName")).append("（").append(card.get("board")).append("板）当前值 ")
                        .append(card.get("metricValue")).append(" → ").append(card.get("suggestedAction")).append("\n");
            }
        }
        md.append("## 三、重点关注\n");
        for (String h : highlights) {
            md.append("- ").append(h).append("\n");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-26 看板晨会解读");
        out.put("generatedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        out.put("meetingMode", boardsService.isMeetingMode());
        out.put("metrics", metrics);
        out.put("activeRedcards", redcards);
        out.put("highlights", highlights);
        out.put("materialMarkdown", md.toString());
        log.info("AGT-26 晨会材料生成: 活跃红灯 {} 项 重点关注 {} 条", redcards.size(), highlights.size());
        return objectMapper.writeValueAsString(out);
    }

    /** 红灯建议行动（确定性映射） */
    private String suggestedAction(Redcard card) {
        return switch (card.getRuleCode()) {
            case "RC-KITTING-BLOCK" -> "生成缺料请购草稿（AGT-14），当天补齐缺口物料";
            case "RC-PO-OVERDUE" -> "逾期 PO 当天催货，更新期望到货日期";
            case "RC-DISCLOSURE-UNSIGN" -> "通知交底人当天完成签字（否则工单无法开工）";
            case "RC-ACCEPT-PENDING" -> "催办三方签字（业主+监理+我方），早会现场指派跟进人";
            case "RC-PUSH-FAILED" -> "检查 T+ 推送失败原因并重推队列";
            case "RC-RECON-DIFF" -> "财务当天核对差异单，明确差错方";
            case "RC-RECT-OVERDUE" -> "整改单当天闭环或重新约期并升级责任人";
            case "RC-CONTRACT-PENDING" -> "商务部逐单过堂，明确会签/审批卡点";
            case "RC-DESIGN-DRAFT" -> "设计部当天评审并受控发布";
            default -> "早会过堂指定责任人，限期闭环";
        };
    }

    private int weight(Redcard card) {
        int w = "RED".equals(card.getLevel()) ? 10 : 0;
        w += "ESCALATED".equals(card.getStatus()) ? 5 : 0;
        return w;
    }

    private int d(Map<String, BigDecimal> metrics, String key) {
        return metrics.getOrDefault(key, BigDecimal.ZERO).intValue();
    }
}
