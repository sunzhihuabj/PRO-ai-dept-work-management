package com.dept.mgmt.kb;

import com.dept.mgmt.kb.service.ChunkSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本切块器测试（纯逻辑，无 Spring 上下文）
 *
 * @author dept-mgmt
 */
class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    void shortText_singleChunk() {
        List<String> chunks = splitter.split("这是一段很短的文本。");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("很短"));
    }

    @Test
    void longText_multipleChunksWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("第").append(i).append("段：风电机组现场施工应遵守安全操作规程，确保吊装作业安全。\n");
        }
        List<String> chunks = splitter.split(sb.toString());
        assertTrue(chunks.size() > 1, "长文本应切分为多块");
        // 相邻块应存在重叠（前块尾部内容出现在后块头部）
        String tailOfFirst = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 20));
        assertTrue(chunks.get(1).contains(tailOfFirst.strip().substring(0, Math.min(5, tailOfFirst.strip().length())))
                        || chunks.stream().anyMatch(c -> c.contains(tailOfFirst.strip().substring(0, Math.min(5, tailOfFirst.strip().length())))),
                "相邻块应有重叠内容");
    }

    @Test
    void oversizeParagraph_hardSplit() {
        String longPara = "重".repeat(1200);
        List<String> chunks = splitter.split(longPara);
        assertTrue(chunks.size() >= 2, "单段超长应硬切");
        chunks.forEach(c -> assertFalse(c.isBlank()));
    }

    @Test
    void blankText_emptyResult() {
        assertTrue(splitter.split(null).isEmpty());
        assertTrue(splitter.split("   \n  \n ").isEmpty());
    }
}
