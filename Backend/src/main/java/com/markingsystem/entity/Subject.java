package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subjects")
public class Subject {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "subject_code", unique = true, nullable = false)
    private String subjectCode;
    
    @Column(nullable = false)
    private String subjectName;
    
    @Column(name = "subject_type")
    private String subjectType;
    
    @Column(name = "max_score")
    private Double maxScore;
    
    @Column(name = "is_required")
    private Boolean isRequired;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "active";
        }
        if (isRequired == null) {
            isRequired = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}