package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M2 LLM 意图判别器单测（fail-open 契约）
 */
class IntentClassifierTest {

    private AiGatewayClient gatewayClient;
    private IntentClassifier classifier;

    @BeforeEach
    void setUp() throws Exception {
        gatewayClient = mock(AiGatewayClient.class);
        classifier = new IntentClassifier(gatewayClient);
        // @Value 字段在单测中不生效，反射置 true
        Field f = IntentClassifier.class.getDeclaredField("llmEnabled");
        f.setAccessible(true);
        f.setBoolean(classifier, true);
    }

    private void stubChat(String out) {
        when(gatewayClient.chat(anyString(), anyString(), anyLong(), any())).thenReturn(out);
    }

    @Test
    void shouldClassifyDelegateWithConfidence() {
        stubChat("{\"intent\":\"DELEGATE\",\"confidence\":0.9}");
        Optional<IntentClassifier.Intent> r = classifier.classify("把客户对接情况整理成材料发给领导", 9L);
        assertTrue(r.isPresent());
        assertEquals("DELEGATE", r.get().label());
        assertEquals(0.9, r.get().confidence(), 1e-6);
    }

    @Test
    void shouldReturnEmptyOnInvalidLabel() {
        stubChat("{\"intent\":\"FOO\",\"confidence\":0.9}");
        assertTrue(classifier.classify("任意消息", 1L).isEmpty());
    }

    @Test
    void shouldReturnEmptyOnGarbageOutput() {
        stubChat("抱歉我无法回答");
        assertTrue(classifier.classify("任意消息", 1L).isEmpty());
    }

    @Test
    void shouldFailOpenOnGatewayError() {
        when(gatewayClient.chat(anyString(), anyString(), anyLong(), any()))
                .thenThrow(new BusinessException(503, "AI 服务暂不可用"));
        assertTrue(classifier.classify("任意消息", 1L).isEmpty());
    }

    @Test
    void shouldFailOpenWhenDisabled() throws Exception {
        stubChat("{\"intent\":\"DELEGATE\",\"confidence\":0.99}");
        Field f = IntentClassifier.class.getDeclaredField("llmEnabled");
        f.setAccessible(true);
        f.setBoolean(classifier, false);
        assertTrue(classifier.classify("任意消息", 1L).isEmpty());
    }

    @Test
    void shouldPassJsonSchemaAndTruncateLongContent() {
        String longContent = "x".repeat(600);
        stubChat("{\"intent\":\"QUERY\",\"confidence\":0.5}");
        classifier.classify(longContent, 2L);
        // 校验能力点编码 + 内容截断至 500 字
        org.mockito.ArgumentCaptor<String> cap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(gatewayClient).chat(eq("CAP_INTENT_CLASSIFY"), cap.capture(), eq(2L), any());
        long xCount = cap.getValue().chars().filter(c -> c == 'x').count();
        assertTrue(xCount <= 500);
    }
}
