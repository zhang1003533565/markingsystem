package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "physical_education")
public class PhysicalEducation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private Student student;
    
    @Column(name = "raw_score")
    private Double rawScore;
    
    @Column(name = "percentage")
    private Double percentage;
    
    @Column(name = "bonus_score")
    private Double bonusScore;
    
    @Column(name = "total_score")
    private Double totalScore;
    
    @Column(nullable = false)
    private String semester;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateTotalScore();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateTotalScore();
    }
    
    public void calculateTotalScore() {
        double raw = rawScore != null ? rawScore : 0.0;
        double pct = percentage != null ? percentage : 1.0;
        double bonus = bonusScore != null ? bonusScore : 0.0;
        this.totalScore = raw * pct + bonus;
    }
}
