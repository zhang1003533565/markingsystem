package com.markingsystem.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentTextExtractor 契约测试：PDF / DOCX / TXT 内存构造后抽取，格式不支持时报错。
 */
class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsTextFromPdf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Knowledge document import works");
                content.endText();
            }
            document.save(out);
        }
        MultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", out.toByteArray());

        String text = extractor.extract(file);
        assertTrue(text.contains("Knowledge document import works"));
    }

    @Test
    void extractsTextFromDocx() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("第一段：Java 基础知识点");
            document.createParagraph().createRun().setText("第二段：面向对象三大特性");
            document.write(out);
        }
        MultipartFile file = new MockMultipartFile(
                "file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                out.toByteArray());

        String text = extractor.extract(file);
        assertTrue(text.contains("Java 基础知识点"));
        assertTrue(text.contains("面向对象三大特性"));
    }

    @Test
    void extractsTxtAndNormalizesNewlines() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "note.md", "text/markdown",
                "# 标题\r\n正文内容".getBytes(StandardCharsets.UTF_8));
        String text = extractor.extract(file);
        assertEquals("# 标题\n正文内容", text);
    }

    @Test
    void rejectsUnsupportedAndLegacyFormats() throws Exception {
        MultipartFile doc = new MockMultipartFile(
                "file", "old.doc", "application/msword", new byte[]{1});
        IllegalArgumentException legacy = assertThrows(
                IllegalArgumentException.class, () -> extractor.extract(doc));
        assertTrue(legacy.getMessage().contains(".docx"));

        MultipartFile exe = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(exe));
    }

    @Test
    void rejectsBlankContent() throws Exception {
        MultipartFile blank = new MockMultipartFile(
                "file", "blank.txt", "text/plain", "   ".getBytes());
        assertThrows(IllegalStateException.class, () -> extractor.extract(blank));
    }

    @Test
    void mergesPdfStyleBrokenLinesIntoCompleteSentence() {
        String text = "目\n标、也是把每门课程、每次项目和每次沟通都变成能力增长的证明。\n我会保持抗压、主动。";
        assertEquals("目标、也是把每门课程、每次项目和每次沟通都变成能力增长的证明。\n我会保持抗压、主动。",
                DocumentTextExtractor.mergeContinuationLines(text));
    }

    @Test
    void mergesBrokenLinesThroughExtract() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "broken.txt", "text/plain",
                "目\n标、也是把每门课程变成能力增长的证明。".getBytes(StandardCharsets.UTF_8));
        assertEquals("目标、也是把每门课程变成能力增长的证明。", extractor.extract(file));
    }

    @Test
    void keepsParagraphHeadingAndListBoundaries() {
        String text = "第一段完成。\n第二段开始。\n# 标题\n正文内容\n一、背景\n这是正文。\n1. 第一点\n2. 第二点";
        assertEquals(text, DocumentTextExtractor.mergeContinuationLines(text));
    }

    @Test
    void keepsBlankLineAsParagraphBoundary() {
        assertEquals("第一段完成。\n第二段开始。",
                DocumentTextExtractor.mergeContinuationLines("第一段完成。\n\n第二段开始。"));
    }
}
