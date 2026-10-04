package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "composite_subject_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"academic_year", "class_name", "subject_id"})
)
public class CompositeSubjectConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "major_id", nullable = false)
    private Long majorId;

    @Column(name = "major_name", nullable = false)
    private String majorName;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Column(name = "class_name", length = 100)
    private String className;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "composite_category", nullable = false)
    private String compositeCategory;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (compositeCategory == null || compositeCategory.isBlank()) {
            compositeCategory = "INTELLECTUAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (compositeCategory == null || compositeCategory.isBlank()) {
            compositeCategory = "INTELLECTUAL";
        }
    }
}
