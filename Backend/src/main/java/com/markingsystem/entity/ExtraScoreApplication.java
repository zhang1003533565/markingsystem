package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "extra_score_applications",
        indexes = {
                @Index(name = "idx_extra_score_app_student_history", columnList = "student_no,academic_year,updated_at"),
                @Index(name = "idx_extra_score_app_period", columnList = "submission_period_id")
        }
)
public class ExtraScoreApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_no", nullable = false, length = 50)
    private String studentNo;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "class_name", length = 100)
    private String className;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "submission_window_id")
    private Long submissionWindowId;

    @Column(name = "submission_period_id")
    private Long submissionPeriodId;

    @Column(name = "submission_batch_code", length = 64)
    private String submissionBatchCode;

    @Column(name = "submission_period_label", length = 50)
    private String submissionPeriodLabel;

    @Column(name = "submission_start_time")
    private LocalDateTime submissionStartTime;

    @Column(name = "submission_end_time")
    private LocalDateTime submissionEndTime;

    @Column(name = "score_type", nullable = false, length = 30)
    private String scoreType;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", length = 100)
    private String itemName;

    @Column(name = "score_direction", length = 20)
    private String scoreDirection = "add";

    @Column(name = "level_rule_id")
    private Long levelRuleId;

    @Column(name = "level_name", length = 100)
    private String levelName;

    @Column(name = "author_rule_id")
    private Long authorRuleId;

    @Column(name = "author_label", length = 100)
    private String authorLabel;

    @Column(name = "base_score")
    private Double baseScore;

    @Column(name = "author_ratio")
    private Double authorRatio;

    @Column(name = "cert_file_name", length = 255)
    private String certFileName;

    @Column(name = "cert_file_url", length = 1000)
    private String certFileUrl;

    @Column(name = "cert_name", length = 255)
    private String certName;

    @Column(name = "detail_desc", length = 2000)
    private String detailDesc;

    @Column(name = "award_time")
    private LocalDate awardTime;

    @Column(name = "expected_score")
    private Double expectedScore;

    @Column(name = "proof_text", length = 2000)
    private String proofText;

    @Column(name = "proof_images", length = 4000)
    private String proofImages;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "review_stage", nullable = false, length = 30)
    private String reviewStage = "committee_pending";

    @Column(name = "committee_status", length = 20)
    private String committeeStatus;

    @Column(name = "committee_comment", length = 1000)
    private String committeeComment;

    @Column(name = "committee_reviewer_id")
    private Long committeeReviewerId;

    @Column(name = "committee_reviewer_name", length = 100)
    private String committeeReviewerName;

    @Column(name = "committee_reviewed_at")
    private LocalDateTime committeeReviewedAt;

    @Column(name = "teacher_status", length = 20)
    private String teacherStatus;

    @Column(name = "teacher_comment", length = 1000)
    private String teacherComment;

    @Column(name = "teacher_reviewer_id")
    private Long teacherReviewerId;

    @Column(name = "teacher_reviewer_name", length = 100)
    private String teacherReviewerName;

    @Column(name = "teacher_reviewed_at")
    private LocalDateTime teacherReviewedAt;

    @Column(name = "return_reason", length = 1000)
    private String returnReason;

    /** 打回类型：reject=驳回，supplement=退回补充材料；为空时按 reject 语义处理。 */
    @Column(name = "return_type", length = 20)
    private String returnType;

    @Column(name = "withdrawal_status", length = 20)
    private String withdrawalStatus;

    @Column(name = "withdrawal_requested_at")
    private LocalDateTime withdrawalRequestedAt;

    @Column(name = "withdrawal_reviewed_at")
    private LocalDateTime withdrawalReviewedAt;

    @Column(name = "withdrawal_reviewer_id")
    private Long withdrawalReviewerId;

    @Column(name = "withdrawal_reviewer_name", length = 100)
    private String withdrawalReviewerName;

    @Column(name = "withdrawal_comment", length = 1000)
    private String withdrawalComment;

    @Column(name = "submission_type", length = 20)
    private String submissionType = "normal";

    @Column(name = "current_revision_no", nullable = false)
    private Integer currentRevisionNo = 1;

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
