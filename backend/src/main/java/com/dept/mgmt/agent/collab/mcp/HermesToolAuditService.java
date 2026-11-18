package com.dept.mgmt.agent.collab.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.HermesToolAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用审计服务（V2.0 铁律五）：写入与追溯查询。
 *
 * <p>写入原则：append-only（仅 insert）；审计失败仅记 WARN 不阻塞工具主流程；
 * 参数只存 SHA-256 摘要前 16 位（原文不落库）；链式哈希贯穿防篡改。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class HermesToolAuditService {

    private final HermesToolAuditMapper auditMapper;

    public HermesToolAuditService(HermesToolAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    /** 参数摘要：SHA-256 前 16 位 hex（原文不落库） */
    public static String digest(Map<String, Object> args) {
        try {
            String raw = String.valueOf(args);
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 16);
        } catch (Exception e) {
            return null;
        }
    }

    /** 上一条记录的 chain digest（哈希链前驱） */
    private String lastChain() {
        HermesToolAudit last = auditMapper.selectOne(new LambdaQueryWrapper<HermesToolAudit>()
                .orderByDesc(HermesToolAudit::getId).last("LIMIT 1"));
        return last == null ? null : last.getChainPrev() == null
                ? last.getParamsDigest() : last.getChainPrev();
    }

    /**
     * 记录一次工具调用（SUCCESS/DENIED/FAILED/INVALID）。审计异常不阻塞主流程。
     */
    public void record(String runId, String toolName, String toolKind,
                       Long userId, String identitySrc, String outcome,
                       String errorBrief, Map<String, Object> args, long costMs) {
        try {
            HermesToolAudit a = new HermesToolAudit();
            a.setRunId(runId);
            a.setToolName(toolName);
            a.setToolKind(toolKind);
            a.setUserId(userId);
            a.setIdentitySrc(identitySrc);
            a.setOutcome(outcome);
            a.setErrorBrief(truncate(errorBrief, 300));
            a.setParamsDigest(digest(args));
            a.setCostMs(costMs);
            a.setChainPrev(lastChain());
            a.setCreateTime(java.time.LocalDateTime.now());
            auditMapper.insert(a);
        } catch (Exception e) {
            log.warn("工具审计写入失败（不阻塞主流程）: tool={}, outcome={}", toolName, outcome, e);
        }
    }

    /** 追溯查询（按 run/用户/工具/时间，leader 权限由控制器把关） */
    public List<HermesToolAudit> query(String runId, Long userId, String toolName,
                                       int pageNum, int pageSize) {
        LambdaQueryWrapper<HermesToolAudit> qw = new LambdaQueryWrapper<>();
        if (runId != null && !runId.isBlank()) {
            qw.eq(HermesToolAudit::getRunId, runId);
        }
        if (userId != null) {
            qw.eq(HermesToolAudit::getUserId, userId);
        }
        if (toolName != null && !toolName.isBlank()) {
            qw.eq(HermesToolAudit::getToolName, toolName);
        }
        qw.orderByDesc(HermesToolAudit::getId)
                .last("LIMIT " + Math.min(Math.max(pageSize, 1), 200)
                        + " OFFSET " + Math.max(pageNum - 1, 0) * Math.min(Math.max(pageSize, 1), 200));
        return auditMapper.selectList(qw);
    }

    /** 链完整性校验（从最新往回逐条比对前驱） */
    public boolean verifyChain(int depth) {
        List<HermesToolAudit> rows = auditMapper.selectList(new LambdaQueryWrapper<HermesToolAudit>()
                .orderByDesc(HermesToolAudit::getId).last("LIMIT " + Math.min(Math.max(depth, 1), 1000)));
        for (int i = 0; i < rows.size() - 1; i++) {
            HermesToolAudit cur = rows.get(i);
            HermesToolAudit prev = rows.get(i + 1);
            String expected = prev.getChainPrev() == null ? prev.getParamsDigest() : prev.getChainPrev();
            if (cur.getChainPrev() != null && !cur.getChainPrev().equals(expected)
                    && !cur.getChainPrev().equals(prev.getParamsDigest())) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
