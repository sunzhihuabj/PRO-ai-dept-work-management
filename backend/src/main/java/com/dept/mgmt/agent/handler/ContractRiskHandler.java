package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.mapper.ContractMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同风险分析处理器（AGT-07，T-112）
 * 文本 → 网关 LLM 风险条款分析（JSON 契约）→ 风险等级/摘要回填台账 + 结构化风险项清单；
 * 结论为 AI 建议，合同审批决策仍由人类领导完成（红线：审批类永久禁止 Agent 自动化）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractRiskHandler implements CapabilityHandler {

    private final AiGatewayClient aiGatewayClient;
    private final ContractTaskSupport support;
    private final ContractMapper contractMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-07";
    }

    /** 只读分析 + 风险字段回填，不参与审批决策，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        return execute(instruction, userId, Map.of());
    }

    @Override
    public String execute(String instruction, Long userId, Map<String, Object> params) throws Exception {
        Long contractId = firstNonNull(support.fromParams(params, "contractId"), support.parseContractId(instruction));
        Long documentId = firstNonNull(support.fromParams(params, "documentId"), support.parseDocumentId(instruction));
        String text = support.resolveText(documentId, contractId);

        Map<String, Object> schema = Map.of(
                "required", List.of("risk_level", "risk_summary", "risks"),
                "risk_level", "string(高|中|低)", "risk_summary", "string", "risks", "array");
        String prompt = "你是合同风险分析助手。请分析以下合同文本中的风险条款，只输出 JSON，不要任何解释文字。"
                + "字段定义：risk_level（整体风险等级，只能取 高/中/低 之一）、risk_summary（100字内总体结论）、"
                + "risks（数组，每项含 item 风险点描述、severity 严重程度(高/中/低)、suggestion 应对建议；"
                + "重点识别：付款条件失衡、验收标准模糊、违约责任缺失、知识产权归属、单方解除权、质保范围不清。"
                + "未发现的风险不要编造，risks 可为空数组）。\n\n合同文本：\n" + text;
        String content = support.stripCodeFence(aiGatewayClient.chat(capabilityCode(), prompt, userId, schema));

        Map<?, ?> analysis;
        try {
            analysis = objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "风险分析结果解析失败（模型输出异常），请重试或转人工: " + e.getMessage());
        }
        // 风险等级/摘要回填台账；风险项明细保留在任务结果中供工作台展示
        if (contractId != null) {
            Contract contract = contractMapper.selectById(contractId);
            if (contract != null) {
                Object level = analysis.get("risk_level");
                if (level != null && List.of("高", "中", "低").contains(String.valueOf(level))) {
                    contract.setRiskLevel(String.valueOf(level));
                }
                Object summary = analysis.get("risk_summary");
                if (summary != null) {
                    contract.setRiskSummary(abbreviate(String.valueOf(summary), 1000));
                }
                contractMapper.updateById(contract);
            }
        }
        log.info("AGT-07 风险分析完成: contractId={}, documentId={}", contractId, documentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", "AGT-07 合同风险分析");
        result.put("contractId", contractId);
        result.put("analysis", analysis);
        return objectMapper.writeValueAsString(result);
    }

    private Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    private String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
