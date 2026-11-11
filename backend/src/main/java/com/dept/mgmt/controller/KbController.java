package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.kb.dto.KbSearchHit;
import com.dept.mgmt.kb.entity.KbDocument;
import com.dept.mgmt.kb.service.KbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口（T-106 RAG 基础）
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/kb")
@RequiredArgsConstructor
@Tag(name = "知识库", description = "文档索引、混合检索（溯源引用）")
public class KbController {

    private final KbService kbService;

    @PostMapping("/documents")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "上传并索引知识文档（TXT/MD/DOCX/PDF）")
    public R<KbDocument> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String title) {
        return R.ok("索引完成", kbService.index(file, title));
    }

    @GetMapping("/documents")
    @Operation(summary = "文档列表")
    public R<List<KbDocument>> list() {
        return R.ok(kbService.list());
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('ROLE_DEPT_LEADER')")
    @Operation(summary = "删除文档及分块")
    public R<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return R.ok("删除成功", null);
    }

    @GetMapping("/search")
    @Operation(summary = "混合检索（向量+关键词，RRF 融合，带溯源；敏感未审核自动过滤）")
    public R<List<KbSearchHit>> search(@RequestParam String query,
                                       @RequestParam(defaultValue = "5") Integer topK) {
        return R.ok(kbService.search(query, topK == null || topK < 1 ? 5 : Math.min(topK, 20)));
    }

    // ===== T-306 知识库投产 =====

    @PostMapping("/text")
    @Operation(summary = "文本语料入库（SOP/制度/规范/案例；敏感知识须人工审核后可检索）")
    public R<KbDocument> indexText(@RequestBody Map<String, Object> body) {
        return R.ok("知识已入库", kbService.indexText(
                (String) body.get("title"),
                (String) body.get("content"),
                (String) body.getOrDefault("category", "SOP"),
                Boolean.parseBoolean(String.valueOf(body.getOrDefault("sensitive", "false")))));
    }

    @PostMapping("/{id}/feedback")
    @Operation(summary = "知识评分反馈（upvote=true 点赞+3 / false 点踩-2）")
    public R<Void> feedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        kbService.feedback(id, Boolean.parseBoolean(String.valueOf(body.getOrDefault("upvote", "true"))));
        return R.ok("反馈已记录", null);
    }

    @GetMapping("/eliminate-candidates")
    @Operation(summary = "知识淘汰候选（未命中且均分低于阈值）")
    public R<List<Map<String, Object>>> eliminateCandidates(@RequestParam(defaultValue = "1") Integer scoreThreshold) {
        return R.ok(kbService.eliminateCandidates(scoreThreshold == null ? 1 : scoreThreshold));
    }

    @PostMapping("/{id}/audit")
    @Operation(summary = "敏感知识人工审核（approved=true 通过可检索 / false 驳回）")
    public R<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        kbService.auditSensitive(id, Boolean.parseBoolean(String.valueOf(body.getOrDefault("approved", "true"))));
        return R.ok("审核完成", null);
    }
}
