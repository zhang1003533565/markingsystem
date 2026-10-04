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
@Table(name = "composite_score_weights")
public class CompositeScoreWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moral_base_weight", nullable = false)
    private Double moralBaseWeight = 1D;

    @Column(name = "moral_extra_weight", nullable = false)
    private Double moralExtraWeight = 1D;

    @Column(name = "intellectual_base_weight", nullable = false)
    private Double intellectualBaseWeight = 1D;

    @Column(name = "intellectual_extra_weight", nullable = false)
    private Double intellectualExtraWeight = 1D;

    @Column(name = "physical_base_weight", nullable = false)
    private Double physicalBaseWeight = 1D;

    @Column(name = "physical_extra_weight", nullable = false)
    private Double physicalExtraWeight = 1D;

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
