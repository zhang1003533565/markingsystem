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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "weekly_job_recommendations",
        indexes = {
                @Index(name = "idx_weekly_job_week_start", columnList = "week_start_date"),
                @Index(name = "idx_weekly_job_week_sort", columnList = "week_start_date, sort_order")
        }
)
public class WeeklyJobRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "job_title", nullable = false, length = 120)
    private String jobTitle;

    @Column(name = "salary", nullable = false, length = 80)
    private String salary;

    @Column(name = "skills", nullable = false, columnDefinition = "LONGTEXT")
    private String skills;

    @Column(name = "recruitment_link", nullable = false, columnDefinition = "LONGTEXT")
    private String recruitmentLink;

    @Column(name = "source", length = 100)
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
