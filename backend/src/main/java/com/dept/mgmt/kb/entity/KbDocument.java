package com.dept.mgmt.kb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识文档实体（T-106 RAG 基础）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocument extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 文档标题 */
    private String title;

    /** 来源类型（PDF/WORD/TXT/MD/TEXT） */
    private String sourceType;

    /** 原始文件名（上传时） */
    private String originName;

    /** 提取后的正文字符数 */
    private Integer charCount;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态（DRAFT解析中/INDEXED已索引/FAILED失败） */
    private String status;

    /** 解析失败原因（收敛后） */
    private String failReason;

    /** 提取后的正文全文（检索展示用） */
    private String content;
}
