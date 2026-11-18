package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.HermesRegressionCase;
import com.dept.mgmt.mapper.HermesRegressionCaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2.0 D7 黄金指令回归集单测（路由断言计分/通过率门槛）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegressionSuiteServiceTest {

    @Mock
    private HermesRegressionCaseMapper caseMapper;
    @Mock
    private AiChatService aiChatService;

    private RegressionSuiteService service;

    @BeforeEach
    void setUp() {
        service = new RegressionSuiteService(caseMapper, aiChatService);
    }

    private HermesRegressionCase routeCase(String no, String expected, String instruction) {
        HermesRegressionCase c = new HermesRegressionCase();
        c.setCaseNo(no);
        c.setCaseKind("ROUTE");
        c.setInstruction(instruction);
        c.setExpectedRoute(expected);
        c.setEnabled(1);
        return c;
    }

    @Test
    void runAllShouldScorePassFailAndSkipPerm() {
        HermesRegressionCase g1 = routeCase("G01", "WEBSEARCH", "联网检索招标动态");
        HermesRegressionCase g2 = routeCase("G02", "ADVISOR", "专业意见：消防验收");
        HermesRegressionCase p1 = new HermesRegressionCase();
        p1.setCaseNo("P01");
        p1.setCaseKind("PERM");
        p1.setInstruction("/v1/system/perm-config/assignments");
        p1.setEnabled(1);
        p1.setLastResult("PASS");

        when(caseMapper.selectList(any())).thenReturn(List.of(g1, g2, p1));
        when(aiChatService.dryRoute("联网检索招标动态")).thenReturn("WEBSEARCH");
        when(aiChatService.dryRoute("专业意见：消防验收")).thenReturn("OPS");

        Map<String, Object> report = service.runAll();

        assertEquals(3, ((Number) report.get("total")).intValue());
        assertEquals(1, ((Number) report.get("pass")).intValue());
        assertEquals(1, ((Number) report.get("fail")).intValue());
        assertEquals(1, ((Number) report.get("skip")).intValue());
        // 通过率分母 = pass + fail（PERM 跳过用例不参与），1/2 = 0.5
        assertEquals(0.5, (Double) report.get("passRate"), 0.001);
        // 3 条用例都应回写最近结果
        verify(caseMapper, times(3)).updateById(any(HermesRegressionCase.class));
    }

    @Test
    void lastPassRateShouldReturnNullWhenNoBaseline() {
        when(caseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertEquals(null, service.lastPassRate());
    }

    @Test
    void lastPassRateShouldComputeRatio() {
        when(caseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(4L, 3L);
        assertEquals(0.75, service.lastPassRate(), 0.001);
    }

    @Test
    void writePermResultShouldUpdateCase() {
        HermesRegressionCase p1 = new HermesRegressionCase();
        p1.setCaseNo("P01");
        p1.setCaseKind("PERM");
        p1.setEnabled(1);
        when(caseMapper.selectOne(any())).thenReturn(p1);

        service.writePermResult("P01", false);

        verify(caseMapper).updateById(p1);
        assertEquals("FAIL", p1.getLastResult());
    }
}
