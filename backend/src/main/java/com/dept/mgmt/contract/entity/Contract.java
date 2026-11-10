package com.dept.mgmt.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同台账实体（T-111 W2 合同域）
 * 金额统一 DECIMAL(18,2)，禁用 float；客户名称暂为文本，主数据 T-103 建成后切换主体引用
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract")
public class Contract extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同编号（T-101 引擎 HT 规则） */
    private String contractNo;

    /** 合同名称 */
    private String name;

    /** 客户名称 */
    private String customerName;

    /** 合同金额（元） */
    private BigDecimal amount;

    /** 签订日期 */
    private LocalDate signDate;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 质保期（月） */
    private Integer warrantyMonths;

    /** 预付款比例% */
    private Integer payRatioPrepay;

    /** 到货款比例% */
    private Integer payRatioDelivery;

    /** 验收款比例% */
    private Integer payRatioAccept;

    /** 质保金比例% */
    private Integer payRatioWarranty;

    /** 状态（DRAFT/COUNTERSIGN/APPROVING/SEALING/EFFECTIVE/CLOSED/TERMINATED） */
    private String status;

    /** AGT-06 要素提取结果 JSON */
    private String extractedJson;

    /** AGT-07 风险等级（高/中/低） */
    private String riskLevel;

    /** AGT-07 风险摘要 */
    private String riskSummary;

    /** 备注 */
    private String remark;
}
