package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库（MaxKB 风格的多知识库结构）。
 *
 * <p>一个知识库包含多份文档（{@link KnowledgeDocument}），每份文档包含多个
 * 分段（{@link KnowledgeSegment}）。停用知识库后其全部分段不参与检索。
 */
@Data
@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 知识库名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /** 卡片主题色（十六进制，如 #1677ff） */
    @Column(length = 16)
    private String color;

    /** 卡片图标（表情符号） */
    @Column(length = 16)
    private String icon;

    /** 是否启用（停用后其分段不参与检索） */
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

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