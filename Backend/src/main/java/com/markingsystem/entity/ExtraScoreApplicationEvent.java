package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "extra_score_application_events",
        indexes = {
                @Index(name = "idx_extra_score_event_application_time", columnList = "application_id,occurred_at"),
                @Index(name = "idx_extra_score_event_type", columnList = "event_type")
        }
)
public class ExtraScoreApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 1;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(name = "review_stage", length = 30)
    private String reviewStage;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "operator_role", length = 30)
    private String operatorRole;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Lob
    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
