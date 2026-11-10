package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.design.entity.DesignScheme;
import com.dept.mgmt.design.entity.DesignTemplate;
import com.dept.mgmt.design.service.DesignService;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.mapper.ProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方案文档起草处理器（AGT-09，T-116）
 * 模板匹配（场景键）→ 模板+项目上下文+勘测记录 → 网关 LLM 起草 → 存为 AI 草稿版本；
 * 受控发布必须人工执行（外发红线），起草只产草稿
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemeDraftHandler implements CapabilityHandler {

    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");
    private static final Pattern SCENE = Pattern.compile("scene\\s*=\\s*([\\u4e00-\\u9fa5A-Za-z0-9]+)");

    private final AiGatewayClient aiGatewayClient;
    private final DesignService designService;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-09";
    }

    /** 只产草稿不改受控状态，可自动执行（发布必须人工） */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        Long projectId = parseLong(instruction, PROJECT_ID);
        if (projectId == null) {
            throw new BusinessException(400, "指令中未找到 projectId=，无法定位项目");
        }
        String scene = parseString(instruction, SCENE);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在: " + projectId);
        }
        // 1. 模板匹配（场景精确 → 默认首个）
        DesignTemplate template = designService.matchTemplate(scene);
        if (template == null) {
            throw new BusinessException(404, "模板库为空，请先维护方案模板");
        }
        // 2. 组装上下文（项目+勘测记录）
        Map<String, Object> ctx = designService.projectContext(projectId);
        String prompt = "你是风电装备制造方案起草助手。请基于以下模板与项目信息起草一份方案文档，"
                + "输出 JSON：title（方案标题）与 content（Markdown 正文），正文须保留模板章节结构并结合项目信息具体化，"
                + "信息不足处用【待补充】标注，禁止编造数据。\n\n"
                + "【模板】" + template.getTemplateName() + "（引用规范：" + template.getSpecRefs() + "）\n"
                + template.getTemplateText() + "\n\n"
                + "【项目】名称：" + project.getProjectName() + "，客户：" + project.getCustomerName()
                + "，描述：" + (project.getDescription() == null ? "无" : project.getDescription()) + "\n"
                + "【勘测记录】\n" + ctx.get("surveyText");
        Map<String, Object> schema = Map.of("required", List.of("title", "content"));
        String json = stripFence(aiGatewayClient.chat(capabilityCode(), prompt, userId, schema));

        Map<String, Object> drafted;
        try {
            drafted = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(500, "方案起草结果解析失败，请重试或转人工: " + e.getMessage());
        }
        // 3. 存为 AI 草稿版本
        String title = String.valueOf(drafted.getOrDefault("title", project.getProjectName() + " 制造方案"));
        String content = String.valueOf(drafted.getOrDefault("content", ""));
        Long schemeId = designService.createScheme(projectId, null, title, content, "AI");
        log.info("AGT-09 方案起草完成: projectId={}, schemeId={}, template={}", projectId, schemeId, template.getTemplateName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", "AGT-09 方案文档起草");
        result.put("projectId", projectId);
        result.put("template", template.getTemplateName());
        result.put("schemeId", schemeId);
        result.put("title", title);
        result.put("contentPreview", content.length() > 200 ? content.substring(0, 200) + "…" : content);
        return objectMapper.writeValueAsString(result);
    }

    private String stripFence(String content) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    private Long parseLong(String instruction, Pattern pattern) {
        if (instruction == null) {
            return null;
        }
        Matcher m = pattern.matcher(instruction);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    private String parseString(String instruction, Pattern pattern) {
        if (instruction == null) {
            return null;
        }
        Matcher m = pattern.matcher(instruction);
        return m.find() ? m.group(1) : null;
    }
}
