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

/**
 * 教师侧 AI 报告历史（通用表）。
 *
 * <p>用于承载可回看的 AI 生成报告正文，避免一事一表。
 * 当前类型：{@code CLASS_ANSWER_SUMMARY}（班级答疑总结）。
 * 同类新功能应复用本表，通过 {@code reportType} 区分，禁止再开专用历史表。
 */
@Data
@Entity
@Table(
        name = "teacher_ai_reports",
        indexes = {
                @Index(name = "idx_tar_teacher_type_time", columnList = "teacher_id, report_type, generated_at"),
                @Index(name = "idx_tar_teacher_class_type", columnList = "teacher_id, class_name, report_type")
        }
)
public class TeacherAiReport {

    /** 班级答疑总结 */
    public static final String TYPE_CLASS_ANSWER_SUMMARY = "CLASS_ANSWER_SUMMARY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "report_type", nullable = false, length = 64)
    private String reportType;

    @Column(name = "class_name", length = 128)
    private String className;

    /** 查询起始时间原文；空表示不限 */
    @Column(name = "time_range_start", length = 64)
    private String timeRangeStart;

    /** 查询结束时间原文；空表示不限 */
    @Column(name = "time_range_end", length = 64)
    private String timeRangeEnd;

    /** 班情总结等场景的会话条数；其他类型可空 */
    @Column(name = "conversation_count")
    private Integer conversationCount;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    /** 扩展字段 JSON（如 unattributedCount），避免为单类型再加列 */
    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}
