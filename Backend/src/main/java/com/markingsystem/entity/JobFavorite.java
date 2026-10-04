package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "job_favorites",
        indexes = {
                @Index(name = "idx_jf_user_id", columnList = "user_id"),
                @Index(name = "idx_jf_user_job", columnList = "user_id, recommendation_id", unique = true)
        }
)
public class JobFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "salary", length = 80)
    private String salary;

    @Column(name = "skills", columnDefinition = "LONGTEXT")
    private String skills;

    @Column(name = "recruitment_link", columnDefinition = "LONGTEXT")
    private String recruitmentLink;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
