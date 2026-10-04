package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户专属的岗位学习资源。
 * 每个用户对每个收藏岗位存储独立的资源推荐数据。
 */
@Data
@Entity
@Table(
        name = "job_learning_resources",
        indexes = {
                @Index(name = "idx_jlr_user_job", columnList = "user_id, recommendation_id"),
                @Index(name = "idx_jlr_user_job_sort", columnList = "user_id, recommendation_id, sort_order")
        }
)
public class JobLearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "guide", nullable = false, columnDefinition = "LONGTEXT")
    private String guide;

    /** 资源跳转链接（AI 生成，可能为空；为空时前端提供一键搜索兜底） */
    @Column(name = "link", columnDefinition = "LONGTEXT")
    private String link;

    /**
     * 图文微课正文（AI 生成的结构化课程内容，如章节标题/要点/示例），
     * 前端在系统内嵌阅读器中直接展示，学生读完可标记完成。
     */
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (generatedAt == null) {
            generatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
