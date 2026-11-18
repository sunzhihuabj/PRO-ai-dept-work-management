package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 文档草稿实体（V2.0 M6 文档工作台）：Hermes/Agent 起草落库 → 工作台查看/编辑/合规审核/送审。
 *
 * @author dept-mgmt
 */
@Data
@TableName("ai_doc_draft")
public class AiDocDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档编号 DOC-YYYYMMDD-SEQ */
    private String docNo;

    /** 文档标题 */
    private String title;

    /** 正文（Markdown） */
    private String contentMd;

    /** 版本号（每次编辑 +1） */
    private Integer version;

    /** DRAFT 草稿 / REVIEW 待审 / APPROVED 已审 */
    private String status;

    /** 来源：HERMES / AGENT / MANUAL */
    private String source;

    /** 关联 AI 会话 */
    private Long conversationId;

    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 逻辑删除（工具/接口均不暴露删除，字段兼容保留） */
    @JsonIgnore
    private Integer deleted;
}
