package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_analysis_records")
public class StudentAnalysisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_no", nullable = false, length = 50)
    private String studentNo;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "class_name", length = 100)
    private String className;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "primary_type", nullable = false, length = 30)
    private String primaryType;

    @Column(name = "secondary_types", columnDefinition = "LONGTEXT")
    private String secondaryTypes;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Column(name = "reasons", columnDefinition = "LONGTEXT")
    private String reasons;

    @Column(name = "suggestions", columnDefinition = "LONGTEXT")
    private String suggestions;

    @Column(name = "feature_snapshot", columnDefinition = "LONGTEXT")
    private String featureSnapshot;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "payload_snapshot", columnDefinition = "LONGTEXT")
    private String payloadSnapshot;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}
