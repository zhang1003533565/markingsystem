package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "moral_education")
public class MoralEducation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private Student student;
    
    @Column(name = "activity_score")
    private Double activityScore;

    @Column(name = "second_class_score")
    private Double secondClassScore;
    
    @Column(name = "bonus_score")
    private Double bonusScore;
    
    @Column(name = "total_score")
    private Double totalScore;
    
    @Column(nullable = false)
    private String semester;

    @Column(name = "academic_year", length = 30)
    private String academicYear;
    
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
        double activity = activityScore != null ? activityScore : 0.0;
        double bonus = bonusScore != null ? bonusScore : 0.0;
        this.totalScore = activity + bonus;
    }
}
