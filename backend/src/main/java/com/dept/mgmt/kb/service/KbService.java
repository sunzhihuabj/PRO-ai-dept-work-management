package com.dept.mgmt.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.kb.dto.KbSearchHit;
import com.dept.mgmt.kb.entity.KbChunk;
import com.dept.mgmt.kb.entity.KbDocument;
import com.dept.mgmt.kb.vector.VectorStore;
import com.dept.mgmt.mapper.KbChunkMapper;
import com.dept.mgmt.mapper.KbDocumentMapper;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务（T-106 RAG 基础）
 * 索引：解析 → 切块 → 网关向量化 → 落库
 * 检索：混合检索（向量余弦 + 关键词）→ RRF 融合排序 → 溯源引用（文档标题+块序号）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbService {

    /** RRF 融合常数（行业标准 60） */
    private static final int RRF_K = 60;

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final DocumentParser parser;
    private final ChunkSplitter splitter;
    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    /**
     * 上传并索引知识文档（同步流程：解析→切块→向量化→落库）
     */
    @Transactional(rollbackFor = Exception.class)
    public KbDocument index(MultipartFile file, String title) {
        LoginUser user = LoginUser.current();
        KbDocument document = new KbDocument();
        document.setTitle(StringUtils.hasText(title) ? title : file.getOriginalFilename());
        document.setStatus("DRAFT");
        document.setOriginName(file.getOriginalFilename());
        document.setDocCategory("SOP");
        document.setSensitive(0);
        document.setAuditStatus("APPROVED");
        document.setHitCount(0);
        documentMapper.insert(document);
        try {
            // 1. 解析
            String[] parsed = parser.parse(file);
            String content = parsed[0];
            document.setSourceType(parsed[1]);
            if (!StringUtils.hasText(content) || content.isBlank()) {
                throw new BusinessException(400, "未能从文件中提取到文本内容（扫描件 PDF 需 OCR，后续版本支持）");
            }
            return indexContent(document, content);
        } catch (BusinessException e) {
            document.setStatus("FAILED");
            document.setFailReason(e.getMessage());
            documentMapper.updateById(document);
            throw e;
        } catch (Exception e) {
            log.error("文档索引失败: {}", document.getTitle(), e);
            document.setStatus("FAILED");
            document.setFailReason("索引失败：" + e.getClass().getSimpleName());
            documentMapper.updateById(document);
            throw new BusinessException(500, "文档索引失败，请检查 AI 通道配置");
        }
    }

    /**
     * 文本语料直接入库（T-306：SOP/制度/规范/案例批量投产入口，无需上传文件）
     */
    @Transactional(rollbackFor = Exception.class)
    public KbDocument indexText(String title, String content, String category, boolean sensitive) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(400, "知识标题不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "知识正文不能为空");
        }
        if (!List.of("SOP", "REGULATION", "SPEC", "CASE").contains(category)) {
            throw new BusinessException(400, "知识分类仅允许 SOP/REGULATION/SPEC/CASE");
        }
        LoginUser user = LoginUser.current();
        KbDocument document = new KbDocument();
        document.setTitle(title);
        document.setStatus("DRAFT");
        document.setSourceType("TEXT");
        document.setOriginName(title + ".txt");
        document.setDocCategory(category);
        document.setSensitive(sensitive ? 1 : 0);
        // 敏感知识必须人工审核后才可检索（T-306 规则）
        document.setAuditStatus(sensitive ? "PENDING" : "APPROVED");
        document.setHitCount(0);
        documentMapper.insert(document);
        try {
            return indexContent(document, content);
        } catch (BusinessException e) {
            document.setStatus("FAILED");
            document.setFailReason(e.getMessage());
            documentMapper.updateById(document);
            throw e;
        } catch (Exception e) {
            log.error("文本语料索引失败: {}", title, e);
            document.setStatus("FAILED");
            document.setFailReason("索引失败：" + e.getClass().getSimpleName());
            documentMapper.updateById(document);
            throw new BusinessException(500, "文本语料索引失败，请检查 AI 通道配置");
        }
    }

    /** 公共索引流程：切块 → 向量化 → 落库（文件与文本入库共用） */
    private KbDocument indexContent(KbDocument document, String content) throws Exception {
        document.setContent(content);
        document.setCharCount(content.length());
        List<String> chunks = splitter.split(content);
        if (chunks.isEmpty()) {
            throw new BusinessException(400, "切块结果为空");
        }
        LoginUser user = LoginUser.current();
        Map<String, Object> embed = aiGatewayClient.embed(chunks, user == null ? null : user.getUserId());
        @SuppressWarnings("unchecked")
        List<List<Double>> vectors = (List<List<Double>>) embed.get("embeddings");
        String model = String.valueOf(embed.get("model"));
        List<KbChunk> chunkRows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setCharCount(chunks.get(i).length());
            chunk.setEmbeddingModel(model);
            chunk.setEmbedding(objectMapper.writeValueAsString(vectors.get(i)));
            chunk.setScore(0);
            chunkRows.add(chunk);
        }
        vectorStore.upsert(chunkRows);
        document.setChunkCount(chunkRows.size());
        document.setStatus("INDEXED");
        documentMapper.updateById(document);
        log.info("知识文档索引完成: id={}, title={}, chunks={}, model={}",
                document.getId(), document.getTitle(), chunkRows.size(), model);
        return document;
    }

    /**
     * 混合检索：向量余弦 + 关键词，RRF 融合，溯源引用
     */
    public List<KbSearchHit> search(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(400, "检索问题不能为空");
        }
        LoginUser user = LoginUser.current();
        // 1. 查询向量化
        Map<String, Object> embed = aiGatewayClient.embed(List.of(query), user == null ? null : user.getUserId());
        @SuppressWarnings("unchecked")
        List<Double> queryVec = ((List<List<Double>>) embed.get("embeddings")).get(0);
        // 2. 向量通道
        List<VectorStore.SearchHit> vectorHits = vectorStore.search(queryVec, topK * 2);
        // 3. 关键词通道（块正文 LIKE 任一分词，取前 topK*2）
        List<KbChunk> keywordHits = new ArrayList<>();
        for (String kw : tokenize(query)) {
            keywordHits.addAll(chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                    .like(KbChunk::getContent, kw)
                    .last("LIMIT " + topK * 2)));
        }
        // 4. RRF 融合：score = Σ 1/(k + rank)
        Map<Long, Double> rrf = new HashMap<>();
        Map<Long, KbChunk> chunkById = new HashMap<>();
        double rank = 1;
        for (VectorStore.SearchHit hit : vectorHits) {
            rrf.merge(hit.chunk().getId(), 1.0 / (RRF_K + rank), Double::sum);
            chunkById.put(hit.chunk().getId(), hit.chunk());
            rank++;
        }
        rank = 1;
        for (KbChunk chunk : dedupe(keywordHits)) {
            rrf.merge(chunk.getId(), 1.0 / (RRF_K + rank), Double::sum);
            chunkById.put(chunk.getId(), chunk);
            rank++;
        }
        // 5. 文档标题补全（溯源）+ T-306 敏感知识过滤（敏感未审核通过不可检索）
        List<KbSearchHit> results = rrf.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK * 3L)
                .map(e -> {
                    KbChunk chunk = chunkById.get(e.getKey());
                    KbDocument doc = documentMapper.selectById(chunk.getDocumentId());
                    return new Object[] { chunk, doc, e.getValue() };
                })
                .filter(arr -> {
                    KbDocument doc = (KbDocument) arr[1];
                    return doc == null || doc.getSensitive() == null || doc.getSensitive() != 1
                            || "APPROVED".equals(doc.getAuditStatus());
                })
                .limit(topK)
                .map(arr -> {
                    KbChunk chunk = (KbChunk) arr[0];
                    KbDocument doc = (KbDocument) arr[1];
                    return new KbSearchHit(chunk.getDocumentId(),
                            doc == null ? "未知文档" : doc.getTitle(),
                            chunk.getChunkIndex(), chunk.getContent(), (Double) arr[2]);
                })
                .toList();
        // 6. 命中计数（评分来源之一，T-306）
        results.stream().map(KbSearchHit::getDocumentId).distinct().forEach(this::incrementHit);
        log.info("知识检索: query={} hits={}", query, results.size());
        return results;
    }

    /** 检索命中计数 +1（幂等性无要求，评分统计口径） */
    private void incrementHit(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc != null) {
            doc.setHitCount((doc.getHitCount() == null ? 0 : doc.getHitCount()) + 1);
            documentMapper.updateById(doc);
        }
    }

    // ===== T-306 知识评分与淘汰 =====

    /**
     * 知识评分反馈：点赞 +3 / 点踩 -2，作用于文档全部分块 score
     */
    @Transactional(rollbackFor = Exception.class)
    public void feedback(Long documentId, boolean upvote) {
        KbDocument doc = mustExistDoc(documentId);
        int delta = upvote ? 3 : -2;
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocumentId, documentId));
        for (KbChunk chunk : chunks) {
            chunk.setScore((chunk.getScore() == null ? 0 : chunk.getScore()) + delta);
            chunkMapper.updateById(chunk);
        }
        log.info("知识评分反馈: doc={} {}{} chunk={}", documentId, delta > 0 ? "+" : "", delta, chunks.size());
    }

    /**
     * 淘汰候选：从未被检索命中（hit_count=0）且平均块评分低于阈值的知识文档
     */
    public List<Map<String, Object>> eliminateCandidates(int scoreThreshold) {
        List<KbDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, "INDEXED")
                .eq(KbDocument::getHitCount, 0));
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (KbDocument doc : docs) {
            List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getDocumentId, doc.getId()));
            double avg = chunks.stream().mapToInt(c -> c.getScore() == null ? 0 : c.getScore())
                    .average().orElse(0);
            if (avg < scoreThreshold) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("documentId", doc.getId());
                row.put("title", doc.getTitle());
                row.put("category", doc.getDocCategory());
                row.put("avgScore", avg);
                row.put("hitCount", doc.getHitCount());
                candidates.add(row);
            }
        }
        return candidates;
    }

    /** 敏感知识人工审核（PENDING→APPROVED/REJECTED） */
    @Transactional(rollbackFor = Exception.class)
    public void auditSensitive(Long documentId, boolean approved) {
        KbDocument doc = mustExistDoc(documentId);
        if (doc.getSensitive() == null || doc.getSensitive() != 1) {
            throw new BusinessException(400, "非敏感知识无需人工审核");
        }
        if (!"PENDING".equals(doc.getAuditStatus())) {
            throw new BusinessException(400, "该知识已审核完成，当前: " + doc.getAuditStatus());
        }
        doc.setAuditStatus(approved ? "APPROVED" : "REJECTED");
        documentMapper.updateById(doc);
        log.info("敏感知识审核: doc={} → {}", documentId, doc.getAuditStatus());
    }

    private KbDocument mustExistDoc(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "知识文档不存在: " + id);
        }
        return doc;
    }

    /**
     * 文档列表
     */
    public List<KbDocument> list() {
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .orderByDesc(KbDocument::getCreateTime));
    }

    /**
     * 删除文档及其全部分块
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        vectorStore.deleteByDocument(documentId);
        documentMapper.deleteById(documentId);
        log.info("知识文档已删除: id={}, title={}", documentId, doc.getTitle());
    }

    // ===== 私有辅助 =====

    private List<KbChunk> dedupe(List<KbChunk> chunks) {
        Map<Long, KbChunk> seen = new HashMap<>();
        chunks.forEach(c -> seen.putIfAbsent(c.getId(), c));
        return new ArrayList<>(seen.values());
    }

    /** 极简中文/英文分词：连续非空白段 + 2 字滑窗（中文），过滤常见虚词 */
    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        for (String seg : query.trim().split("[\\s，。？！、；：,.?!;:]+")) {
            if (!seg.isEmpty()) {
                tokens.add(seg);
            }
            if (seg.length() >= 2 && seg.length() <= 8) {
                tokens.add(seg);
            } else if (seg.length() > 8) {
                for (int i = 0; i + 2 <= seg.length(); i++) {
                    tokens.add(seg.substring(i, i + 2));
                }
            }
        }
        return tokens.stream().distinct().limit(20).toList();
    }
}
