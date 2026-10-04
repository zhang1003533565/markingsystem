package com.markingsystem.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 知识库文档文本抽取工具。
 *
 * <p>支持 PDF(.pdf)、Word(.docx)、纯文本(.txt/.md)；旧版 .doc 不支持，明确提示另存为 .docx。
 * 抽取结果统一做换行归一化；解析后无文本（如扫描件）抛出异常由调用方提示。
 */
@Component
public class DocumentTextExtractor {

    /** 句末标点（含成对闭合符），用于判断一行是否已是完整句子 */
    private static final String SENTENCE_END = "。！？!?；;…\"”』）】》」";

    /** 段落/标题起始特征：Markdown 标题、无序/有序列表、中文编号、章节号 */
    private static final Pattern PARAGRAPH_START = Pattern.compile(
            "^(#{1,6}\\s*|[-*•]\\s+|\\d+[.、)）]|[一二三四五六七八九十]+[、．.]"
                    + "|（[一二三四五六七八九十\\d]+）|\\([一二三四五六七八九十\\d]+\\)"
                    + "|第[一二三四五六七八九十百千\\d]+[章节篇部卷段])");

    /**
     * 按扩展名抽取文档纯文本。
     *
     * @throws IllegalArgumentException 格式不支持
     * @throws IllegalStateException    解析后无有效文本
     */
    public String extract(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".pdf")) {
            text = extractPdf(file);
        } else if (name.endsWith(".docx")) {
            text = extractDocx(file);
        } else if (name.endsWith(".txt") || name.endsWith(".md")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else if (name.endsWith(".doc")) {
            throw new IllegalArgumentException("暂不支持旧版 .doc 格式，请另存为 .docx 后上传");
        } else {
            throw new IllegalArgumentException("仅支持 PDF、Word(.docx)、TXT/Markdown 文件");
        }
        String normalized = text == null ? "" : text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0000', ' ')
                .trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("文档解析后无文本内容（可能是扫描件或空文档）");
        }
        return mergeContinuationLines(normalized);
    }

    /**
     * 合并被换行打断的半句话（PDF/Word 抽取常见的视觉换行，如"目\n标、"）。
     * 上一行不以句末标点结尾、且两行都不是标题/编号行时，把下一行拼回上一行，
     * 避免文档切分后分段标题与正文出现残句。
     */
    static String mergeContinuationLines(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        String prev = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                if (prev != null) {
                    sb.append(prev).append('\n');
                    prev = null;
                }
                continue;
            }
            if (prev == null) {
                prev = line;
            } else if (isContinuation(prev, line)) {
                prev = prev + joinSpace(prev, line) + line;
            } else {
                sb.append(prev).append('\n');
                prev = line;
            }
        }
        if (prev != null) {
            sb.append(prev);
        }
        return sb.toString().trim();
    }

    /** line 是否为 prev 的续行：prev 未成句、且两行都不是段落/标题起始 */
    private static boolean isContinuation(String prev, String line) {
        if (endsSentence(prev)) {
            return false;
        }
        return !PARAGRAPH_START.matcher(prev).find()
                && !PARAGRAPH_START.matcher(line).find();
    }

    private static boolean endsSentence(String s) {
        return !s.isEmpty() && SENTENCE_END.indexOf(s.charAt(s.length() - 1)) >= 0;
    }

    /** 拼接续行：任一侧含 CJK 时不加空格，否则补一个空格（"text\nbase" → "text base"） */
    private static String joinSpace(String a, String b) {
        char last = a.charAt(a.length() - 1);
        char first = b.charAt(0);
        return (isCjk(last) || isCjk(first)) ? "" : " ";
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
