package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分段传输对象（不含向量大字段）。
 */
@Data
public class KnowledgeSegmentDTO {

    private Long id;

    private Long kbId;

    /** 所属文档（手动新增分段可为 null） */
    private Long documentId;

    /** 分段标题 */
    private String title;

    /** 分段正文（Markdown） */
    private String content;

    /** 分类 */
    private String category;

    /** 辅助检索关键词（逗号分隔） */
    private String keywords;

    /** 知识来源标注 */
    private String source;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否已向量化（前端展示"待向量化"状态） */
    private Boolean embedded;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}