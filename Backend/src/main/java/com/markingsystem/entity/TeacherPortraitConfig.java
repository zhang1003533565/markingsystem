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

@Data
@Entity
@Table(name = "teacher_portrait_config")
public class TeacherPortraitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false, unique = true)
    private Long teacherId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "competition_keyword_multiplier", nullable = false)
    private Double competitionKeywordMultiplier = 18D;

    @Column(name = "technical_keyword_multiplier", nullable = false)
    private Double technicalKeywordMultiplier = 18D;

    @Column(name = "employment_keyword_multiplier", nullable = false)
    private Double employmentKeywordMultiplier = 18D;

    @Column(name = "service_keyword_multiplier", nullable = false)
    private Double serviceKeywordMultiplier = 18D;

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
