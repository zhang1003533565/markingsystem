package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "extra_score_submission_periods",
        indexes = {
                @Index(name = "idx_extra_score_period_teacher_year", columnList = "teacher_id,academic_year"),
                @Index(name = "idx_extra_score_period_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_extra_score_period_batch", columnNames = "batch_code")
        }
)
public class ExtraScoreSubmissionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, length = 64)
    private String batchCode;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType = "supplemental";

    @Column(name = "period_no", nullable = false)
    private Integer periodNo = 0;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "scheduled";

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_by")
    private Long createdBy;

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
