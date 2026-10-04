package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "score_import_batch")
public class ScoreImportBatch {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "semester", nullable = false, length = 30)
    private String semester;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "created_score_count")
    private Integer createdScoreCount = 0;

    @Column(name = "updated_score_count")
    private Integer updatedScoreCount = 0;

    @Column(name = "unchanged_score_count")
    private Integer unchangedScoreCount = 0;

    @Column(name = "error_count")
    private Integer errorCount = 0;

    @Column(name = "undone", nullable = false)
    private Boolean undone = false;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @Column(name = "created_by_role", length = 30)
    private String createdByRole;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
