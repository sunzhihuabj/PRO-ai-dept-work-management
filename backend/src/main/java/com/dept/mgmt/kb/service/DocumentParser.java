package com.dept.mgmt.kb.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 知识文档解析器（T-106）：从上传文件提取纯文本正文
 * 支持：TXT / MD（直接读取）、DOCX（POI）、PDF（PDFBox）；扫描件 OCR 预留（后续 PaddleOCR 适配器）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
public class DocumentParser {

    /** 解析大小上限 20MB */
    private static final long MAX_SIZE = 20 * 1024 * 1024L;

    /**
     * 解析文件为纯文本
     *
     * @return [0]=提取文本，[1]=来源类型
     */
    public String[] parse(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("文件超过 20MB 上限");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            try (InputStream in = file.getInputStream()) {
                return new String[]{new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        name.endsWith(".md") ? "MD" : "TXT"};
            }
        }
        if (name.endsWith(".docx")) {
            try (InputStream in = file.getInputStream(); XWPFDocument doc = new XWPFDocument(in);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                return new String[]{extractor.getText(), "WORD"};
            }
        }
        if (name.endsWith(".pdf")) {
            try (InputStream in = file.getInputStream();
                 org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(in.readAllBytes())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
                return new String[]{text, "PDF"};
            }
        }
        throw new IllegalArgumentException("暂不支持该文件类型，仅支持 TXT/MD/DOCX/PDF（扫描件 OCR 后续版本支持）");
    }
}
