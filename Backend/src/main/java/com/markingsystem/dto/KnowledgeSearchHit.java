package com.markingsystem.dto;

import lombok.Data;

/**
 * 知识库检索命中结果：条目摘要 + 相似度分数。
 */
@Data
public class KnowledgeSearchHit {

    private Long id;

    private String question;

    private String category;

    /** 答案正文（注入 LLM 上下文用） */
    private String content;

    /** 知识来源标注（如「依据《实训手册》P32」） */
    private String source;

    /** 余弦相似度（0~1） */
    private Double score;
}
