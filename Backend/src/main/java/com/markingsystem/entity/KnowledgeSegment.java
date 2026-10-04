package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分段（MaxKB 风格：内容 + 向量化状态）。
 *
 * <p>title 为分段标题（检索主文本），content 为分段正文（Markdown），
 * embeddingJson 保存文本向量（OpenAI 兼容 embedding API 生成），供余弦相似度检索。
 * 手动新增的分段可以不挂文档（documentId 为 null）。
 */
@Data
@Entity
@Table(name = "knowledge_segment")
public class KnowledgeSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属知识库 */
    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    /** 所属文档（手动新增分段可为 null） */
    @Column(name = "document_id")
    private Long documentId;

    /** 分段标题（检索主文本） */
    @Column(nullable = false, length = 512)
    private String title;

    /** 分段正文（Markdown） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 分类（迁移自旧版知识条目，新分段可留空） */
    @Column(length = 64)
    private String category;

    /** 辅助检索关键词（逗号分隔） */
    @Column(length = 512)
    private String keywords;

    /** 知识来源标注，如「依据《数据结构》P78」 */
    @Column(length = 512)
    private String source;

    /** 是否启用（停用不参与检索） */
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 文本向量（JSON 数组），由 embedding 服务生成 */
    @Column(name = "embedding_json", columnDefinition = "LONGTEXT")
    private String embeddingJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}