package com.dept.mgmt.aiworkspace;

import com.dept.mgmt.aiworkspace.entity.AiConversation;
import com.dept.mgmt.aiworkspace.entity.AiMessage;
import com.dept.mgmt.aiworkspace.entity.HermesRunEvent;
import com.dept.mgmt.aiworkspace.service.HermesDelegateService;
import com.dept.mgmt.common.client.HermesService;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AiConversationMapper;
import com.dept.mgmt.mapper.AiMessageMapper;
import com.dept.mgmt.mapper.HermesRunEventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hermes 委托服务单元测试（B2：第四级路由判定 + 异步回写）
 */
@ExtendWith(MockitoExtension.class)
class HermesDelegateServiceTest {

    @Mock
    private HermesService hermesService;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private HermesRunEventMapper runEventMapper;

    private HermesDelegateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new HermesDelegateService(hermesService, messageMapper, conversationMapper, runEventMapper);
        // 消息插入回填 ID（模拟自增）
        lenient().when(messageMapper.insert(any(AiMessage.class))).thenAnswer(inv -> {
            inv.getArgument(0, AiMessage.class).setId(100L);
            return 1;
        });
        lenient().when(conversationMapper.selectById(any())).thenReturn(new AiConversation());
        lenient().when(conversationMapper.updateById(any(AiConversation.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        // 后台轮询线程清场（避免用例间 log 噪音）
    }

    private AiConversation conv() {
        AiConversation c = new AiConversation();
        c.setId(5L);
        return c;
    }

    // ==================== 委托判定 ====================

    @Test
    void shouldDelegateOnExplicitTrigger() {
        assertTrue(service.shouldDelegate("委托AI Agent员工帮我整理本季度所有项目的进度汇总"));
        assertTrue(service.shouldDelegate("帮我处理售后报修单的闭环跟进，然后生成处理周报"));
    }

    @Test
    void shouldDelegateOnMultiStepCombination() {
        assertTrue(service.shouldDelegate("先查一下项目A的进度，然后把滞后任务列出来，之后生成一份周报给领导"));
    }

    @Test
    void shouldNotDelegatePureQuery() {
        // 纯查询（短指令）走 Ops 单轮
        assertTrue(!service.shouldDelegate("查一下项目A的进度"));
        assertTrue(!service.shouldDelegate("看下我今天的待办"));
    }

    @Test
    void shouldNotDelegateShortContent() {
        assertTrue(!service.shouldDelegate("委托"));
        assertTrue(!service.shouldDelegate(""));
        assertTrue(!service.shouldDelegate(null));
    }

    // ==================== 委托执行 ====================

    @Test
    void tryDelegateShouldReturnCardWhenAvailable() {
        when(hermesService.available()).thenReturn(true);
        when(hermesService.submitRun(any())).thenReturn("run_abc123");

        AiMessage card = service.tryDelegate(conv(), "委托AI Agent员工帮我整理本季度项目进度汇总",
                7L, "sunzhihua");

        assertNotNull(card);
        assertEquals("hermes", card.getMsgType());
        assertTrue(card.getContent().contains("hermes-run:run_abc123"));
        // 任务书含系统约束 + 委托人身份 + 用户指令
        ArgumentCaptor<String> bookCaptor = ArgumentCaptor.forClass(String.class);
        verify(hermesService).submitRun(bookCaptor.capture());
        assertTrue(bookCaptor.getValue().contains("AI Agent 员工"));
        assertTrue(bookCaptor.getValue().contains("sunzhihua"));
        assertTrue(bookCaptor.getValue().contains("整理本季度项目进度汇总"));
    }

    @Test
    void tryDelegateShouldReturnNullWhenUnavailable() {
        when(hermesService.available()).thenReturn(false);

        assertNull(service.tryDelegate(conv(), "委托AI Agent员工处理复杂任务", 7L, "u"));
        verify(hermesService, never()).submitRun(any());
        verify(messageMapper, never()).insert(any(AiMessage.class));
    }

    @Test
    void tryDelegateShouldReturnNullWhenNotMatched() {
        // 判定短路：shouldDelegate=false 时不会探测 available（严格模式下无多余桩）
        assertNull(service.tryDelegate(conv(), "今天天气怎么样", 7L, "u"));
        verify(hermesService, never()).submitRun(any());
    }

    @Test
    void tryDelegateShouldReturnNullWhenSubmitFails() {
        when(hermesService.available()).thenReturn(true);
        when(hermesService.submitRun(any())).thenReturn(null);

        assertNull(service.tryDelegate(conv(), "委托AI Agent员工处理复杂任务", 7L, "u"));
        verify(hermesService).invalidateAvailability();
    }

    // ==================== 任务书组装 ====================

    @Test
    void taskBookShouldCarryUserInstruction() {
        String book = service.buildTaskBook("查询项目A并汇总风险", 9L, "lihailong");
        assertTrue(book.contains("userId=9"));
        assertTrue(book.contains("lihailong"));
        assertTrue(book.contains("查询项目A并汇总风险"));
        assertTrue(book.contains("MCP 工具"));
    }

    // ==================== 终态回写 ====================

    @Test
    void pollBackShouldAppendResultMessageOnSuccess() throws Exception {
        when(hermesService.available()).thenReturn(true);
        when(hermesService.submitRun(any())).thenReturn("run_ok1");
        // awaitRun 立即返回终态（isTerminal/isSuccessful 为 HermesService 静态语义，无需 stub）
        when(hermesService.awaitRun("run_ok1")).thenReturn(
                Map.of("status", "completed", "output", "汇总完成：3 个项目正常，1 个滞后"));

        AiMessage card = service.tryDelegate(conv(), "委托AI Agent员工整理项目进度", 7L, "u");
        assertNotNull(card);
        Thread.sleep(300); // 后台线程完成事件消费、轮询与回写
        ArgumentCaptor<AiMessage> msgCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, org.mockito.Mockito.atLeastOnce()).insert(msgCaptor.capture());
        assertTrue(msgCaptor.getAllValues().stream()
                .anyMatch(m -> "text".equals(m.getMsgType())
                        && m.getContent().contains("执行完成")
                        && m.getContent().contains("hermes-run:run_ok1")
                        && m.getContent().contains("汇总完成")));
    }

    // ==================== 过程事件时间线（B3） ====================

    @Test
    @SuppressWarnings("unchecked")
    void eventsForUserShouldReturnTimelineWhenOwner() {
        AiMessage card = new AiMessage();
        card.setConversationId(5L);
        when(messageMapper.selectOne(any())).thenReturn(card);
        AiConversation conv = new AiConversation();
        conv.setUserId(7L);
        when(conversationMapper.selectById(5L)).thenReturn(conv);
        HermesRunEvent ev = new HermesRunEvent();
        ev.setRunId("run_t1");
        ev.setSeq(0L);
        ev.setEventType("tool.started");
        when(runEventMapper.selectList(any())).thenReturn(java.util.List.of(ev));

        java.util.List<HermesRunEvent> list = service.eventsForUser(7L, "run_t1");
        assertEquals(1, list.size());
        assertEquals("tool.started", list.get(0).getEventType());
    }

    @Test
    void eventsForUserShouldRejectWhenNotOwner() {
        AiMessage card = new AiMessage();
        card.setConversationId(5L);
        when(messageMapper.selectOne(any())).thenReturn(card);
        AiConversation conv = new AiConversation();
        conv.setUserId(9L);
        when(conversationMapper.selectById(5L)).thenReturn(conv);

        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.eventsForUser(7L, "run_t2"));
        assertEquals(404, ex.getCode());
        verify(runEventMapper, never()).selectList(any());
    }

    @Test
    void eventsForUserShouldRejectUnknownRun() {
        when(messageMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.eventsForUser(7L, "run_ghost"));
        assertEquals(404, ex.getCode());
    }
}
