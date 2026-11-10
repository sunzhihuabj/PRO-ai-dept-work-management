package com.dept.mgmt.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识分块实体（向量 JSON 存储；pgvector 实现待环境就绪切换，见 VectorStore 抽象层）
 *
 * @author dept-mgmt
 */
@Data
@TableName("kb_chunk")
public class KbChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档ID */
    private Long documentId;

    /** 块序号（文档内从0起） */
    private Integer chunkIndex;

    /** 块正文 */
    private String content;

    /** 块字符数 */
    private Integer charCount;

    /** 向量化模型标识 */
    private String embeddingModel;

    /** 向量 JSON 数组（双精度） */
    private String embedding;

    /** 创建时间 */
    private LocalDateTime createTime;
}
