package com.dept.mgmt.kb.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.kb.entity.KbChunk;
import com.dept.mgmt.mapper.KbChunkMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MySQL 向量存储实现（默认）：kb_chunk.embedding JSON 列 + 内存余弦相似度
 *
 * <p>适用边界：知识库分块总量 ≤ 1 万块（部门级 SOP/制度语料规模）。
 * 超出该规模或需要 ANN 索引时，切换 pgvector 实现（rag.vector-store=pgvector）。
 *
 * @author dept-mgmt
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "mysql", matchIfMissing = true)
@RequiredArgsConstructor
public class MysqlVectorStore implements VectorStore {

    private final KbChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void upsert(List<KbChunk> chunks) {
        // embedding 已由索引服务写好，此处仅保证落库一致性（分块行由 KbService 写入）
        for (KbChunk chunk : chunks) {
            if (chunk.getId() == null) {
                chunkMapper.insert(chunk);
            } else {
                chunkMapper.updateById(chunk);
            }
        }
    }

    @Override
    public List<SearchHit> search(List<Double> queryEmbedding, int topK) {
        // 全量加载已向量化分块（规模边界内内存余弦），带文档维度过滤时由上层调用重载
        List<KbChunk> all = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .isNotNull(KbChunk::getEmbedding));
        List<SearchHit> hits = new ArrayList<>();
        for (KbChunk chunk : all) {
            List<Double> vec = parse(chunk.getEmbedding());
            if (vec == null || vec.size() != queryEmbedding.size()) {
                continue;
            }
            hits.add(new SearchHit(chunk, cosine(queryEmbedding, vec)));
        }
        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    @Override
    public void deleteByDocument(Long documentId) {
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, documentId));
    }

    private List<Double> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** 余弦相似度（向量已归一化时等价于点积，此处做通用实现） */
    static double cosine(List<Double> a, List<Double> b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
