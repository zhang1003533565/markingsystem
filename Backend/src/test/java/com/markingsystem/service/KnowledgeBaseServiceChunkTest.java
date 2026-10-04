package com.markingsystem.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KnowledgeBaseService.chunkDocument 契约测试：
 * 单块长度上限、段落聚合、碎块丢弃、超长段硬切。
 */
class KnowledgeBaseServiceChunkTest {

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    @Test
    void mergesShortParagraphsIntoOneChunk() {
        List<String> chunks = KnowledgeBaseService.chunkDocument("第一段内容\n第二段内容\n第三段内容");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("第一段内容"));
        assertTrue(chunks.get(0).contains("第三段内容"));
    }

    @Test
    void splitsWhenAddingParagraphWouldExceedLimit() {
        String big = repeat('a', 300);
        String small = repeat('b', 200);
        // 300 + 1(\n) + 200 > 450 → 必须分成两块
        List<String> chunks = KnowledgeBaseService.chunkDocument(big + "\n" + small);
        assertEquals(2, chunks.size());
        assertEquals(big, chunks.get(0));
        assertEquals(small, chunks.get(1));
    }

    @Test
    void hardSplitsOversizedParagraph() {
        String oversized = repeat('x', 1000);
        List<String> chunks = KnowledgeBaseService.chunkDocument(oversized);
        // 1000 / 450 → 3 块（450/450/100）
        assertEquals(3, chunks.size());
        chunks.forEach(chunk -> assertTrue(chunk.length() <= 450));
        assertEquals(oversized, String.join("", chunks));
    }

    @Test
    void dropsTinyFragments() {
        List<String> chunks = KnowledgeBaseService.chunkDocument("ab\ncd\n这是一条足够长的有效内容，不会被过滤掉。");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("足够长"));
    }

    @Test
    void hardSplitsOversizedParagraphAtSentenceBoundary() {
        // 每 100 字一个句号：切块应落在句号之后，块首不再出现残句，且不丢字
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(repeat('x', 99)).append('。');
        }
        String oversized = sb.toString();
        List<String> chunks = KnowledgeBaseService.chunkDocument(oversized);
        assertEquals(oversized, String.join("", chunks));
        chunks.forEach(chunk -> assertTrue(chunk.length() <= 450));
        for (int i = 1; i < chunks.size(); i++) {
            assertNotEquals('。', chunks.get(i).charAt(0));
            assertEquals('。', chunks.get(i - 1).charAt(chunks.get(i - 1).length() - 1));
        }
    }

    @Test
    void deriveQuestionKeepsCompleteFirstSentence() {
        String chunk = "标、也是把每门课程、每次项目和每次沟通都变成能力增长的证明。\n我会保持抗压、主动沟通。";
        // 首句在「。」结束，整段 23 字 < 30，原样保留
        assertEquals("标、也是把每门课程、每次项目和每次沟通都变成能力增长的证明。",
                KnowledgeBaseService.deriveQuestion(chunk));
    }

    @Test
    void deriveQuestionExtendsUntilSentenceEnd() {
        String chunk = "通过本次职业生涯规划，我更加明确了自己的发展路线\n：以数据科学与大数据专业为根基。";
        // 拼接后 36 字 > 30，按 maxChars-1 范围内最后一个强句末「：」截断 → 30 字
        assertEquals("通过本次职业生涯规划，我更加明确了自己的发展路线：",
                KnowledgeBaseService.deriveQuestion(chunk));
    }

    @Test
    void deriveQuestionStopsAtChapterHeading() {
        assertEquals("第五章 结束语", KnowledgeBaseService.deriveQuestion("第五章 结束语\n后续正文内容……"));
    }

    @Test
    void deriveQuestionTruncatesLongLineAtSentenceBoundary() {
        // 60 字 + 「。」 + 200 字：句号在第 61 位，超出 maxChars-1 范围，回退为硬切 30 字
        String line = repeat('字', 60) + "。" + repeat('字', 200);
        String title = KnowledgeBaseService.deriveQuestion(line);
        assertEquals(30, title.length());
        assertFalse(title.endsWith("…"));
    }

    @Test
    void deriveQuestionFallsBackToHardTruncationWithoutSentenceEnd() {
        // 200 字无标点：硬切 30 字，不带省略号
        String text = repeat('字', 200);
        String title = KnowledgeBaseService.deriveQuestion(text);
        assertEquals(30, title.length());
        assertFalse(title.endsWith("…"));
    }

    @Test
    void deriveQuestionFallsBackToSoftBoundaryWhenNoStrongPunctuation() {
        // 20 字 + 「，」 + 50 字：弱边界在 maxChars-1 范围内命中 → 在逗号截断 → 21 字
        String text = repeat('字', 20) + "，" + repeat('字', 50);
        assertEquals(repeat('字', 20) + "，", KnowledgeBaseService.deriveQuestion(text));
    }

    @Test
    void deriveQuestionHandlesEmptyChunk() {
        assertEquals("未命名片段", KnowledgeBaseService.deriveQuestion(""));
        assertEquals("未命名片段", KnowledgeBaseService.deriveQuestion("   \n  \n"));
    }
}
