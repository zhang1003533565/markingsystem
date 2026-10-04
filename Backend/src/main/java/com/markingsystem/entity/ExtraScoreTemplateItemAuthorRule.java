package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "extra_score_template_item_author_rules")
public class ExtraScoreTemplateItemAuthorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "level_rule_id")
    private Long levelRuleId;

    @Column(name = "author_label", nullable = false, length = 100)
    private String authorLabel;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode = "ratio";

    @Column(name = "ratio")
    private Double ratio;

    @Column(name = "fixed_score")
    private Double fixedScore;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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
