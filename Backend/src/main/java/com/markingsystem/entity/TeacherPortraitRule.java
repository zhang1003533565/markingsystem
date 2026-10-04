package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "teacher_portrait_rules")
public class TeacherPortraitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "type_key", nullable = false, length = 50)
    private String typeKey;

    @Column(name = "type_name", length = 100)
    private String typeName;

    @Column(name = "course_weight", nullable = false)
    private Double courseWeight = 0D;

    @Column(name = "moral_weight", nullable = false)
    private Double moralWeight = 0D;

    @Column(name = "intellectual_weight", nullable = false)
    private Double intellectualWeight = 0D;

    @Column(name = "physical_weight", nullable = false)
    private Double physicalWeight = 0D;

    @Column(name = "extra_score_weight", nullable = false)
    private Double extraScoreWeight = 0D;

    @Column(name = "excellent_course_weight", nullable = false)
    private Double excellentCourseWeight = 0D;

    @Column(name = "growth_weight", nullable = false)
    private Double growthWeight = 0D;

    @Column(name = "keyword_values", columnDefinition = "TEXT")
    private String keywordValues;

    @Column(name = "keyword_bonus", nullable = false)
    private Double keywordBonus = 0D;

    @Column(name = "class_committee_bonus", nullable = false)
    private Double classCommitteeBonus = 18D;

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

    public List<String> getKeywordList() {
        if (keywordValues == null || keywordValues.isBlank()) {
            return new ArrayList<>();
        }
        List<String> keywords = new ArrayList<>();
        for (String kw : keywordValues.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return keywords;
    }

    public void setKeywordList(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            this.keywordValues = "";
        } else {
            this.keywordValues = String.join(",", keywords);
        }
    }
}
