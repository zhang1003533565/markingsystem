package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "composite_score_snapshots")
public class CompositeScoreSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_owner_id")
    private Long scopeOwnerId;

    @Column(name = "class_name")
    private String className;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_no", nullable = false)
    private String studentNo;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "major_id")
    private Long majorId;

    @Column(name = "major_name")
    private String majorName;

    @Column(name = "grade")
    private String grade;

    @Column(name = "rank_no")
    private Integer rankNo;

    @Column(name = "moral_base_score")
    private Double moralBaseScore;

    @Column(name = "moral_extra_score")
    private Double moralExtraScore;

    @Column(name = "intellectual_base_score")
    private Double intellectualBaseScore;

    @Column(name = "intellectual_extra_score")
    private Double intellectualExtraScore;

    @Column(name = "physical_base_score")
    private Double physicalBaseScore;

    @Column(name = "physical_course_average")
    private Double physicalCourseAverage;

    @Column(name = "physical_base_full_score_enabled")
    private Boolean physicalBaseFullScoreEnabled;

    @Column(name = "physical_base_weight")
    private Double physicalBaseWeight;

    @Column(name = "physical_extra_score")
    private Double physicalExtraScore;

    @Lob
    @Column(name = "intellectual_base_details_json", columnDefinition = "TEXT")
    private String intellectualBaseDetailsJson;

    @Lob
    @Column(name = "physical_base_details_json", columnDefinition = "TEXT")
    private String physicalBaseDetailsJson;

    @Lob
    @Column(name = "moral_base_details_json", columnDefinition = "TEXT")
    private String moralBaseDetailsJson;

    @Lob
    @Column(name = "weight_snapshot_json", columnDefinition = "TEXT")
    private String weightSnapshotJson;

    @Lob
    @Column(name = "subject_category_json", columnDefinition = "TEXT")
    private String subjectCategoryJson;

    @Lob
    @Column(name = "extra_score_detail_json", columnDefinition = "TEXT")
    private String extraScoreDetailJson;

    @Column(name = "weighted_moral_base_score")
    private Double weightedMoralBaseScore;

    @Column(name = "weighted_moral_extra_score")
    private Double weightedMoralExtraScore;

    @Column(name = "weighted_intellectual_base_score")
    private Double weightedIntellectualBaseScore;

    @Column(name = "weighted_intellectual_extra_score")
    private Double weightedIntellectualExtraScore;

    @Column(name = "weighted_physical_base_score")
    private Double weightedPhysicalBaseScore;

    @Column(name = "weighted_physical_extra_score")
    private Double weightedPhysicalExtraScore;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_by_name")
    private String updatedByName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
