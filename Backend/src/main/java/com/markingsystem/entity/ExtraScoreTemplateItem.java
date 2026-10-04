package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "extra_score_template_items")
public class ExtraScoreTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "max_authors")
    private Integer maxAuthors;

    @Column(name = "same_event_max_count")
    private Integer sameEventMaxCount;

    @Column(name = "score_direction", length = 20)
    private String scoreDirection = "add";

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "status", length = 20)
    private String status = "active";

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
