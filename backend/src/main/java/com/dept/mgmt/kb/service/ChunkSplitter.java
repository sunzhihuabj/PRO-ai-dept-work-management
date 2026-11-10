package com.dept.mgmt.kb.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块器（T-106）：固定长度 + 重叠窗口，段落边界优先
 *
 * @author dept-mgmt
 */
@Component
public class ChunkSplitter {

    /** 块目标长度（字符） */
    private static final int CHUNK_SIZE = 500;

    /** 相邻块重叠长度（字符） */
    private static final int OVERLAP = 50;

    /**
     * 切块：优先按段落聚合到目标长度，单段超长时硬切
     */
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        // 按段落切分（保留结构），段内再处理超长
        String[] paragraphs = text.replace("\r\n", "\n").split("\n\\s*\n|\n");
        StringBuilder buffer = new StringBuilder();
        for (String para : paragraphs) {
            String p = para.strip();
            if (p.isEmpty()) {
                continue;
            }
            // 单段超长：硬切并立即入列
            if (p.length() > CHUNK_SIZE) {
                if (!buffer.isEmpty()) {
                    chunks.add(buffer.toString());
                    buffer.setLength(0);
                }
                for (int i = 0; i < p.length(); i += CHUNK_SIZE - OVERLAP) {
                    chunks.add(p.substring(i, Math.min(p.length(), i + CHUNK_SIZE)));
                    if (i + CHUNK_SIZE >= p.length()) {
                        break;
                    }
                }
                continue;
            }
            // 聚合段落至目标长度
            if (buffer.length() + p.length() + 1 > CHUNK_SIZE && !buffer.isEmpty()) {
                chunks.add(buffer.toString());
                // 保留尾部重叠
                String tail = buffer.length() > OVERLAP ? buffer.substring(buffer.length() - OVERLAP) : buffer.toString();
                buffer.setLength(0);
                buffer.append(tail).append('\n');
            }
            buffer.append(p).append('\n');
        }
        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }
}
