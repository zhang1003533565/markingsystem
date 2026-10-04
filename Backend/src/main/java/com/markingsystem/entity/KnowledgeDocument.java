package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档（MaxKB 风格：上传的文件或手动建立的分组）。
 *
 * <p>文档属于某个知识库（{@link KnowledgeBase}），包含若干分段（{@link KnowledgeSegment}）。
 * 处理状态：PROCESSING（处理中）/ SUCCESS（完成）/ FAILED（失败，见 errorMessage）。
 */
@Data
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属知识库 */
    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    /** 展示名称（默认取原始文件名） */
    @Column(nullable = false, length = 255)
    private String name;

    /** 原始文件名（上传时） */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 文件类型：pdf / docx / txt / md / manual（手动建立） */
    @Column(name = "file_type", length = 16)
    private String fileType;

    /** 处理状态：PROCESSING / SUCCESS / FAILED */
    @Column(length = 16)
    private String status = "SUCCESS";

    /** 字符数 */
    @Column(name = "char_count")
    private Integer charCount = 0;

    /** 分段数 */
    @Column(name = "segment_count")
    private Integer segmentCount = 0;

    /** 失败原因 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "SUCCESS";
        }
        if (charCount == null) {
            charCount = 0;
        }
        if (segmentCount == null) {
            segmentCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}