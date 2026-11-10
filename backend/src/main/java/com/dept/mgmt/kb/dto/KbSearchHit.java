package com.dept.mgmt.kb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识检索命中项（含溯源引用）
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class KbSearchHit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档ID（溯源） */
    private Long documentId;

    /** 文档标题（溯源） */
    private String documentTitle;

    /** 块序号（溯源：文档内位置） */
    private Integer chunkIndex;

    /** 块正文 */
    private String content;

    /** 融合得分（RRF，越大越相关） */
    private double score;
}
