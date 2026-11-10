package com.dept.gateway.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * JSON 契约校验器
 * 能力点声明了输出契约（请求带 jsonSchema）时强制校验模型输出：
 * 解析失败 → 携带错误信息重生成一次 → 再失败 CONTRACT_FAILED 转人工
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class ContractValidator {

    private final ObjectMapper objectMapper;

    /**
     * 校验输出是否符合契约
     *
     * @return null=通过（或无契约）；否则返回失败原因描述
     */
    public String validate(String content, Map<String, Object> jsonSchema) {
        if (jsonSchema == null || jsonSchema.isEmpty()) {
            return null;
        }
        if (!StringUtils.hasText(content)) {
            return "模型输出为空";
        }
        try {
            // 契约最小校验：必须是合法 JSON 对象且包含 schema 声明的必填字段
            Map<?, ?> parsed = objectMapper.readValue(content, Map.class);
            Object required = jsonSchema.get("required");
            if (required instanceof List<?> requiredFields) {
                for (Object field : requiredFields) {
                    if (!parsed.containsKey(String.valueOf(field))) {
                        return "输出缺少必填字段: " + field;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return "输出不是合法 JSON 对象: " + e.getMessage();
        }
    }

    /**
     * 将契约要求拼进重生成提示词
     */
    public String buildRetryPrompt(String originalPrompt, Map<String, Object> jsonSchema, String lastError) {
        return originalPrompt + "\n\n【输出要求】你的上一次输出未通过校验（" + lastError
                + "）。请严格按以下 JSON 结构重新输出，只输出 JSON 本身，不要任何解释文字："
                + safeSchemaText(jsonSchema);
    }

    private String safeSchemaText(Map<String, Object> jsonSchema) {
        try {
            return objectMapper.writeValueAsString(jsonSchema);
        } catch (Exception e) {
            return "{}";
        }
    }
}
