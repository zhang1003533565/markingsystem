package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "score_import_item")
public class ScoreImportItem {

    public enum ActionType {
        CREATED,
        UPDATED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "score_id", nullable = false)
    private Long scoreId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Column(name = "previous_score")
    private Double previousScore;

    @Column(name = "current_score")
    private Double currentScore;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "semester", nullable = false, length = 30)
    private String semester;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
