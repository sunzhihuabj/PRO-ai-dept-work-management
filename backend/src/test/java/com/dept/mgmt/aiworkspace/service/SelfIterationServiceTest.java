package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.aiworkspace.entity.AgentFeedback;
import com.dept.mgmt.aiworkspace.entity.SoulPatchProposal;
import com.dept.mgmt.agent.collab.service.DecisionAdviceService;
import com.dept.mgmt.mapper.AgentFeedbackMapper;
import com.dept.mgmt.mapper.AgentTaskMapper;
import com.dept.mgmt.mapper.SoulPatchProposalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2.0 M8 自迭代服务单测（差评归因/周 Job 幂等/统计）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfIterationServiceTest {

    @Mock
    private AgentFeedbackMapper feedbackMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private SoulPatchProposalMapper proposalMapper;
    @Mock
    private DecisionAdviceService adviceService;
    @Mock
    private SoulManageService soulManageService;

    private SelfIterationService service;

    @BeforeEach
    void setUp() {
        service = new SelfIterationService(feedbackMapper, agentTaskMapper, proposalMapper,
                adviceService, soulManageService);
    }

    private AgentFeedback neg(String dimension, String comment) {
        AgentFeedback f = new AgentFeedback();
        f.setSourceType("RUN");
        f.setSourceId(1L);
        f.setRating(-1);
        f.setDimension(dimension);
        f.setComment(comment);
        return f;
    }

    @Test
    void shouldRejectInvalidRating() {
        assertThrows(com.dept.mgmt.common.exception.BusinessException.class,
                () -> service.submitFeedback("TASK", 1L, null, 0, null, null));
    }

    @Test
    void shouldProposeFromToolNegativeFeedback() {
        when(feedbackMapper.selectCount(any())).thenReturn(0L);
        when(proposalMapper.selectCount(any())).thenReturn(0L);

        service.submitFeedback("TASK", 9L, null, -1, "TOOL", "缺少对账工具");

        ArgumentCaptor<SoulPatchProposal> cap = ArgumentCaptor.forClass(SoulPatchProposal.class);
        verify(proposalMapper).insert(cap.capture());
        assertEquals("TOOL_MISSING", cap.getValue().getRootCause());
        assertEquals("TOOL_REQUEST", cap.getValue().getCategory());
    }

    @Test
    void weeklyIterateShouldBeIdempotentPerPeriodAndCause() {
        String period = SelfIterationService.periodOf(LocalDate.now());
        when(feedbackMapper.selectList(any())).thenReturn(List.of(neg("TOOL", null)));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        // 同周期同归因已有建议 → 跳过
        when(proposalMapper.selectCount(any())).thenReturn(1L);

        Map<String, Object> result = service.weeklyIterate(LocalDate.now());

        verify(proposalMapper, never()).insert(any(SoulPatchProposal.class));
        verify(adviceService, never()).createGovernanceAdvice(any(), any(), any(),
                any(), any(), any(), any());
        assertEquals(0, ((Number) result.get("proposalsCreated")).intValue());
    }

    @Test
    void weeklyIterateShouldCreateProposalAndAdvice() {
        when(feedbackMapper.selectList(any())).thenReturn(List.of(neg("TOOL", null), neg("SPEED", null)));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(proposalMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = service.weeklyIterate(LocalDate.now());

        ArgumentCaptor<SoulPatchProposal> cap = ArgumentCaptor.forClass(SoulPatchProposal.class);
        verify(proposalMapper).insert(cap.capture());
        assertEquals("TOOL_REQUEST", cap.getValue().getCategory());
        verify(adviceService).createGovernanceAdvice(eq(SelfIterationService.SCENE_SELF_ITERATION),
                eq("GOVERNANCE"), eq(0L), any(), any(), eq(null), any());
        assertEquals(1, ((Number) result.get("proposalsCreated")).intValue());
    }

    @Test
    void statsShouldComputeApprovalRate() {
        when(feedbackMapper.selectCount(any())).thenReturn(4L, 3L, 1L);

        Map<String, Object> stats = service.feedbackStats();

        assertEquals(4L, ((Number) stats.get("total")).longValue());
        assertEquals(0.75, (Double) stats.get("approvalRate"), 0.001);
    }

    @Test
    void rejectShouldMarkRejected() {
        SoulPatchProposal p = new SoulPatchProposal();
        p.setId(5L);
        p.setStatus("PROPOSED");
        when(proposalMapper.selectById(5L)).thenReturn(p);

        service.reject(5L, "理由不充分");

        ArgumentCaptor<SoulPatchProposal> cap = ArgumentCaptor.forClass(SoulPatchProposal.class);
        verify(proposalMapper).updateById(cap.capture());
        assertEquals("REJECTED", cap.getValue().getStatus());
    }
}
