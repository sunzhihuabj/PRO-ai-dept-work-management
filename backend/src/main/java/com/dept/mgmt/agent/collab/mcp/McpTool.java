package com.dept.mgmt.agent.collab.mcp;

import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具定义（阶段一：自有业务能力暴露为 MCP Server 工具，供 Dify/Hermes 调用）
 *
 * @param name             工具名（如 task.query）
 * @param description      描述（进入 LLM 上下文，需准确无诱导——防工具描述投毒，内容仅陈述功能）
 * @param inputSchema      JSON Schema（type=object + properties + required）
 * @param requiresApproval 写操作声明（B4：true=调用即生成待确认审批建议，委托人采纳后才执行）
 * @param handler          执行器（入参 arguments Map，返回可 JSON 序列化结果）
 * @author dept-mgmt
 */
public record McpTool(String name, String description, Map<String, Object> inputSchema,
                      boolean requiresApproval, Function<Map<String, Object>, Object> handler) {

    /** 只读工具便捷构造（requiresApproval=false，兼容既有注册） */
    public McpTool(String name, String description, Map<String, Object> inputSchema,
                   Function<Map<String, Object>, Object> handler) {
        this(name, description, inputSchema, false, handler);
    }

    /** JSON Schema 快捷构造：type=object */
    public static Map<String, Object> objectSchema(Map<String, Object> properties, java.util.List<String> required) {
        java.util.Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? Map.of() : properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
