package com.dept.mgmt.kb.vector;

import java.util.List;

/**
 * 向量存储抽象层（T-106 RAG）
 *
 * <p>当前默认实现：MysqlVectorStore（向量 JSON 列 + 内存余弦相似度）。
 * 选型说明（无人值守模式自主决策，透明披露）：本机无 Docker/PostgreSQL 环境，
 * 部门知识库首批语料规模（≤1 万块）下内存余弦完全可行且零运维；
 * 规划的 pgvector 实现保留接口位（rag.vector-store=pgvector），环境就绪后无需改动业务代码即可切换。
 *
 * @author dept-mgmt
 */
public interface VectorStore {

    /**
     * 批量写入/更新向量
     *
     * @param chunks 已含 embedding JSON 的分块
     */
    void upsert(List<com.dept.mgmt.kb.entity.KbChunk> chunks);

    /**
     * 相似度检索
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回条数
     * @return 按相似度降序的检索结果（含分块与相似度得分）
     */
    List<SearchHit> search(List<Double> queryEmbedding, int topK);

    /**
     * 删除指定文档的全部分块向量
     */
    void deleteByDocument(Long documentId);

    /**
     * 检索命中项：分块 + 余弦相似度得分（0~1，越大越相似）
     */
    record SearchHit(com.dept.mgmt.kb.entity.KbChunk chunk, double score) {
    }
}
