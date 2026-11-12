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
 * 合同要素提取处理器（AGT-06，T-112）
 * 文本（知识库文档或台账合成）→ 网关 LLM + JSON 契约 → 结构化要素；
 * 结果回填 contract.extracted_json（存在 contractId 时）供建档确认页引用；
 * 建档本身必须人工确认（is_never 建档红线不变），本能力只产出"建议值"
 * OCR 预留：扫描件 PDF 解析为空时由上层报错提示，PaddleOCR 就绪后接入 DocumentParser
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExtractHandler implements CapabilityHandler {

    private final AiGatewayClient aiGatewayClient;
    private final ContractTaskSupport support;
    private final ContractMapper contractMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-06";
    }

    /** 只读分析 + 建议字段回填，不改合同状态不建档，可自动执行 */
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

        // T-407：required 声明全部提取字段（网关校验为 containsKey 语义，未提及字段输出 null 仍通过）
        Map<String, Object> schema = Map.of(
                "required", List.of(
                        "contract_name", "contract_no", "customer_name", "party_b", "amount", "sign_date",
                        "warranty_months", "pay_ratios", "payment_terms", "payment_method",
                        "delivery_time", "delivery_place", "contact_info", "acceptance_condition", "summary"),
                "contract_name", "string", "customer_name", "string",
                "amount", "number", "pay_ratios", "object");
        String prompt = "你是合同要素提取助手。请从以下合同文本中提取结构化要素，只输出 JSON，"
                + "不要任何解释文字。字段定义：contract_name（合同名称）、"
                + "contract_no（合同编号，找不到为 null）、customer_name（甲方/客户名称）、"
                + "party_b（乙方主体，即合同承接方，找不到为 null）、"
                + "amount（合同金额，数字，单位元）、sign_date（签订日期 yyyy-MM-dd，找不到为 null）、"
                + "warranty_months（质保期月数，数字）、pay_ratios（对象：prepay/delivery/accept/warranty 四个整数比例，"
                + "找不到按 0 处理）、payment_terms（付款条件描述）、payment_method（付款方式，如银行转账/承兑汇票）、"
                + "delivery_time（交货时间）、delivery_place（交货地点）、"
                + "contact_info（联系人信息：姓名/电话/邮箱）、acceptance_condition（验收条件描述）、"
                + "summary（合同摘要，100 字以内概括合同标的与主要义务）。"
                + "文本中未提及的字段填 null，禁止编造；置信度不足的字段也填 null 由人工补全。\n\n合同文本：\n" + text;
        String content = support.stripCodeFence(aiGatewayClient.chat(capabilityCode(), prompt, userId, schema));

        Map<?, ?> extracted;
        try {
            extracted = objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new BusinessException(500, "要素提取结果解析失败（模型输出异常），请重试或转人工: " + e.getMessage());
        }
        // 结果回填台账（有 contractId 时）：extracted_json 存证，供建档确认页比对
        if (contractId != null) {
            Contract contract = contractMapper.selectById(contractId);
            if (contract != null) {
                contract.setExtractedJson(objectMapper.writeValueAsString(extracted));
                contractMapper.updateById(contract);
            }
        }
        log.info("AGT-06 要素提取完成: contractId={}, documentId={}", contractId, documentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", "AGT-06 合同要素提取");
        result.put("contractId", contractId);
        result.put("extracted", extracted);
        return objectMapper.writeValueAsString(result);
    }

    private Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }
}
