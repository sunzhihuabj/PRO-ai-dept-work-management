package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.HermesRegressionCase;
import com.dept.mgmt.mapper.HermesRegressionCaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄金指令回归集服务（V2.0 M8/验收）：25 条路由断言（dryRoute 纯判定，零 LLM 成本）+
 * 5 条越权断言（由验收脚本 HTTP 层执行，服务层汇总）。
 * SOUL/工具变更后自动回归，通过率 ≥90% 才允许补丁发布（SoulManageService 门槛）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegressionSuiteService {

    private final HermesRegressionCaseMapper caseMapper;
    private final AiChatService aiChatService;

    /** 执行 ROUTE 类回归（PERM 类由验收脚本执行后回写结果） */
    public Map<String, Object> runAll() {
        List<HermesRegressionCase> cases = caseMapper.selectList(new LambdaQueryWrapper<HermesRegressionCase>()
                .eq(HermesRegressionCase::getEnabled, 1)
                .orderByAsc(HermesRegressionCase::getCaseNo));
        long start = System.currentTimeMillis();
        int pass = 0;
        int fail = 0;
        int skip = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (HermesRegressionCase c : cases) {
            String result;
            if ("ROUTE".equals(c.getCaseKind())) {
                String actual = aiChatService.dryRoute(c.getInstruction());
                result = c.getExpectedRoute() != null && c.getExpectedRoute().equals(actual) ? "PASS" : "FAIL";
                Map<String, Object> d = baseRow(c, result);
                d.put("actualRoute", actual);
                d.put("expectedRoute", c.getExpectedRoute());
                details.add(d);
            } else {
                // PERM 类：结果由验收脚本通过 permResult 回写，此处跳过执行（历史结果仅展示，不参与本次计分）
                String historic = StringUtils.hasText(c.getLastResult()) ? c.getLastResult() : null;
                result = historic == null ? "SKIP" : historic;
                Map<String, Object> d = baseRow(c, result);
                d.put("permEndpoint", c.getInstruction());
                details.add(d);
                skip++;
                c.setLastResult(result);
                c.setLastRunAt(LocalDateTime.now());
                caseMapper.updateById(c);
                continue;
            }
            if ("PASS".equals(result)) {
                pass++;
            } else if ("FAIL".equals(result)) {
                fail++;
            }
            c.setLastResult(result);
            c.setLastRunAt(LocalDateTime.now());
            caseMapper.updateById(c);
        }
        long cost = System.currentTimeMillis() - start;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", cases.size());
        report.put("pass", pass);
        report.put("fail", fail);
        report.put("skip", skip);
        // 通过率分母只含实际断言的用例（PASS+FAIL），PERM 越权由验收脚本回写后经 lastPassRate() 汇总
        report.put("passRate", (pass + fail) == 0 ? 1.0 : Math.round(pass * 1000.0 / (pass + fail)) / 1000.0);
        report.put("costMs", cost);
        report.put("runAt", LocalDateTime.now().toString());
        report.put("cases", details);
        log.info("黄金指令回归完成: total={}, pass={}, fail={}, cost={}ms", cases.size(), pass, fail, cost);
        return report;
    }

    /** 最近一次全量通过率（SOUL 采纳门槛用；无数据返回 null 表示未建基线） */
    public Double lastPassRate() {
        Long total = caseMapper.selectCount(new LambdaQueryWrapper<HermesRegressionCase>()
                .eq(HermesRegressionCase::getEnabled, 1)
                .isNotNull(HermesRegressionCase::getLastResult));
        if (total == null || total == 0) {
            return null;
        }
        Long passCount = caseMapper.selectCount(new LambdaQueryWrapper<HermesRegressionCase>()
                .eq(HermesRegressionCase::getEnabled, 1)
                .eq(HermesRegressionCase::getLastResult, "PASS"));
        return Math.round(passCount * 1000.0 / total) / 1000.0;
    }
    /** 用例清单（前端展示/校准用） */
    public List<HermesRegressionCase> listCases() {
        return caseMapper.selectList(new LambdaQueryWrapper<HermesRegressionCase>()
                .orderByAsc(HermesRegressionCase::getCaseNo));
    }

    /** 越权用例结果回写（验收脚本执行 HTTP 403 断言后调用） */
    public void writePermResult(String caseNo, boolean pass) {
        HermesRegressionCase c = caseMapper.selectOne(new LambdaQueryWrapper<HermesRegressionCase>()
                .eq(HermesRegressionCase::getCaseNo, caseNo));
        if (c != null) {
            c.setLastResult(pass ? "PASS" : "FAIL");
            c.setLastRunAt(LocalDateTime.now());
            caseMapper.updateById(c);
        }
    }

    private Map<String, Object> baseRow(HermesRegressionCase c, String result) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("caseNo", c.getCaseNo());
        d.put("caseKind", c.getCaseKind());
        d.put("scene", c.getScene());
        d.put("instruction", c.getInstruction());
        d.put("result", result);
        return d;
    }
}
