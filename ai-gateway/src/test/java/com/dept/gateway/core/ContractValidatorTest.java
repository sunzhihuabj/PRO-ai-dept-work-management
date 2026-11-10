package com.dept.gateway.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 契约校验测试
 *
 * @author dept-mgmt
 */
class ContractValidatorTest {

    private final ContractValidator validator = new ContractValidator(new ObjectMapper());

    private final Map<String, Object> schema = Map.of("required", List.of("riskLevel", "summary"));

    @Test
    void validJson_passes() {
        String content = "{\"riskLevel\": \"中\", \"summary\": \"合同存在付款节点风险\"}";
        assertNull(validator.validate(content, schema), "符合契约应通过");
    }

    @Test
    void missingField_fails() {
        String content = "{\"riskLevel\": \"中\"}";
        assertNotNull(validator.validate(content, schema), "缺少必填字段应失败");
    }

    @Test
    void nonJson_fails() {
        assertNotNull(validator.validate("这不是JSON", schema), "非 JSON 应失败");
    }

    @Test
    void noSchema_skipsValidation() {
        assertNull(validator.validate("任意文本", null), "无契约不校验");
    }

    @Test
    void retryPrompt_containsSchema() {
        String retry = validator.buildRetryPrompt("原提示词", schema, "缺少字段");
        assertTrue(retry.contains("riskLevel"), "重生成提示词应包含契约结构");
        assertTrue(retry.contains("原提示词"));
    }
}
