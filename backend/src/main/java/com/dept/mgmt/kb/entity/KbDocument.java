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

    /** 知识分类（SOP/REGULATION制度/SPEC规范/CASE案例，T-306） */
    private String docCategory;

    /** 敏感知识（0/1，敏感须人工审核后可检索，T-306；sensitive 为 MySQL 保留字需反引号） */
    @com.baomidou.mybatisplus.annotation.TableField("`sensitive`")
    private Integer sensitive;

    /** 审核状态（非敏感默认APPROVED；敏感PENDING待审核/APPROVED/REJECTED） */
    private String auditStatus;

    /** 检索命中计数（评分来源之一，T-306） */
    private Integer hitCount;

    /** 解析失败原因（收敛后） */
    private String failReason;

    /** 提取后的正文全文（检索展示用） */
    private String content;
}
