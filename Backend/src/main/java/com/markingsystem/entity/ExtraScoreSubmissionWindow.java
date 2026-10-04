package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "extra_score_submission_windows",
        indexes = {
                @Index(name = "idx_extra_score_window_period", columnList = "period_id")
        }
)
public class ExtraScoreSubmissionWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "period_id")
    private Long periodId;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
