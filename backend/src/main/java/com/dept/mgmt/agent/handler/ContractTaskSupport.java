package com.dept.mgmt.agent.handler;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.contract.entity.Contract;
import com.dept.mgmt.kb.entity.KbDocument;
import com.dept.mgmt.mapper.ContractMapper;
import com.dept.mgmt.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同类能力点（AGT-06/07）共享支撑：指令参数解析 + 分析文本解析
 * 指令格式约定：自然语言中嵌入 "contractId=1"、"documentId=3" 令牌（均为可选）；
 * 文本来源优先级：知识库文档 > 合同台账字段合成（无文档时兜底）
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class ContractTaskSupport {

    /** 送入模型的最大文本长度（保护 token 预算，超长截断） */
    private static final int MAX_TEXT_CHARS = 6000;

    private static final Pattern CONTRACT_ID = Pattern.compile("contractId\\s*=\\s*(\\d+)");
    private static final Pattern DOCUMENT_ID = Pattern.compile("documentId\\s*=\\s*(\\d+)");

    private final KbDocumentMapper kbDocumentMapper;
    private final ContractMapper contractMapper;

    /** 从指令中解析合同ID（无则 null） */
    public Long parseContractId(String instruction) {
        return parseId(instruction, CONTRACT_ID);
    }

    /** 从指令中解析知识库文档ID（无则 null） */
    public Long parseDocumentId(String instruction) {
        return parseId(instruction, DOCUMENT_ID);
    }

    /**
     * 解析待分析文本：documentId 指向知识库文档时取其解析文本；
     * 否则用合同台账自身字段合成结构化文本（无文档场景兜底）
     */
    public String resolveText(Long documentId, Long contractId) {
        if (documentId != null) {
            KbDocument doc = kbDocumentMapper.selectById(documentId);
            if (doc == null || !StringUtils.hasText(doc.getContent())) {
                throw new BusinessException(404, "知识库文档不存在或无可分析文本: " + documentId);
            }
            return truncate(doc.getContent());
        }
        if (contractId != null) {
            Contract contract = contractMapper.selectById(contractId);
            if (contract == null) {
                throw new BusinessException(404, "合同不存在: " + contractId);
            }
            return truncate("合同名称: " + contract.getName() + "\n客户名称: " + contract.getCustomerName()
                    + "\n合同金额: " + contract.getAmount() + " 元\n质保期: " + contract.getWarrantyMonths()
                    + " 个月\n预付款比例: " + contract.getPayRatioPrepay() + "%\n到货款比例: "
                    + contract.getPayRatioDelivery() + "%\n验收款比例: " + contract.getPayRatioAccept()
                    + "%\n质保金比例: " + contract.getPayRatioWarranty() + "%\n备注: "
                    + (contract.getRemark() == null ? "无" : contract.getRemark()));
        }
        throw new BusinessException(400, "指令中未找到 documentId= 或 contractId=，无法定位分析对象");
    }

    /**
     * 剥离模型输出可能携带的 markdown 代码围栏，返回纯 JSON 文本
     */
    public String stripCodeFence(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
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

    private Long parseId(String instruction, Pattern pattern) {
        if (!StringUtils.hasText(instruction)) {
            return null;
        }
        Matcher matcher = pattern.matcher(instruction);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String truncate(String text) {
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS) + "\n（文本过长已截断）";
    }

    /** 参数 Map 取值（params 与指令双通道兜底） */
    public Long fromParams(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
