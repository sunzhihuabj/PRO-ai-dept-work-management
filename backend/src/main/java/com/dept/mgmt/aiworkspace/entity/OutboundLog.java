package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 出网留痕实体（V2.0 铁律四/五）：经脱敏 proxy 出网的每一次外部请求记录。
 * 原始检索词不落库（仅 SHA-256 摘要），脱敏后内容与命中类型可追溯。
 *
 * @author dept-mgmt
 */
@Data
@TableName("outbound_log")
public class OutboundLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起用户 */
    private Long userId;

    /** 出网目标主机 */
    private String targetHost;

    /** 用途（WEB_SEARCH） */
    private String purpose;

    /** 原始检索词 SHA-256 前 16 位（不存原文） */
    private String queryDigest;

    /** 脱敏后实际出网检索词 */
    private String sanitizedQuery;

    /** 命中的敏感类型列表（逗号分隔：CUSTOMER/AMOUNT/MOBILE/ID_CARD/BANK_CARD/EMAIL） */
    private String hitTypes;

    /** 结果条数 */
    private Integer resultCount;

    /** SUCCESS/FAILED */
    private String status;

    /** 失败简述 */
    private String errorBrief;

    /** 耗时 ms */
    private Long costMs;

    private LocalDateTime createTime;
}
