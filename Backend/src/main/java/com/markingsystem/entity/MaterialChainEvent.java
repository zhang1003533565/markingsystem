package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "material_chain_events",
        indexes = {
                @Index(name = "idx_material_event_chain_time", columnList = "chain_file_id,occurred_at"),
                @Index(name = "idx_material_event_type", columnList = "event_type")
        }
)
public class MaterialChainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_file_id", nullable = false)
    private Long chainFileId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 1;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_stage", length = 30)
    private String fromStage;

    @Column(name = "to_stage", length = 30)
    private String toStage;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "operator_role", length = 30)
    private String operatorRole;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "file_hash_snapshot", length = 128)
    private String fileHashSnapshot;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
