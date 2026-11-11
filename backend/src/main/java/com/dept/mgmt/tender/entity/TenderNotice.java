package com.dept.mgmt.tender.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 标讯实体（T-301 W1 投标域，AGT-01 解析产出）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tender_notice")
public class TenderNotice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 标讯编号（TN 编码引擎） */
    private String tenderNo;

    /** 标讯标题 */
    private String title;

    /** 来源平台 */
    private String platform;

    /** 预算金额 */
    private BigDecimal budget;

    /** 投标保证金 */
    private BigDecimal deposit;

    /** 资质要求（原文，分号/换行分隔） */
    private String qualificationReq;

    /** 投标截止日期 */
    private LocalDate deadline;

    /** 发布日期 */
    private LocalDate publishDate;

    /** 状态（NEW/FOLLOWED/APPLIED/PREPARED/DECIDED/WON/LOST） */
    private String status;

    /** AGT-03 招标文件分析结果 JSON */
    private String analysisJson;

    /** 来源（MANUAL人工导入/AGENT采集） */
    private String importSource;

    /** 标讯原文 */
    private String rawText;

    /** 备注 */
    private String remark;
}
