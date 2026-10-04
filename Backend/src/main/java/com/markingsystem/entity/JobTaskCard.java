package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户专属的岗位学习任务卡（以岗定教）。
 * <p>
 * 由 AI 将企业真实工作任务转化为课堂实训学习型任务，包含：
 * 任务名称、工作情境、分步操作指南、安全要点、对应知识点与技能点、
 * 校内实训资源（微课/仿真软件链接）与个性化学习路径。
 * <p>
 * 每个用户对每个收藏岗位只保存一张任务卡（唯一索引 user_id + recommendation_id），
 * 取消收藏时级联删除。
 */
@Data
@Entity
@Table(
        name = "job_task_cards",
        indexes = {
                @Index(name = "idx_jtc_user_job", columnList = "user_id, recommendation_id", unique = true)
        }
)
public class JobTaskCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    /** 学习型任务名称 */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 工作情境（真实企业工作场景描述） */
    @Column(name = "work_scenario", columnDefinition = "LONGTEXT")
    private String workScenario;

    /** 分步操作指南（每行一步，1. xxx 编号） */
    @Column(name = "steps", columnDefinition = "LONGTEXT")
    private String steps;

    /** 安全要点（每行一条） */
    @Column(name = "safety_points", columnDefinition = "LONGTEXT")
    private String safetyPoints;

    /** 对应知识点（每行一条） */
    @Column(name = "knowledge_points", columnDefinition = "LONGTEXT")
    private String knowledgePoints;

    /** 对应技能点（每行一条） */
    @Column(name = "skill_points", columnDefinition = "LONGTEXT")
    private String skillPoints;

    /** 校内实训资源（每行一条：名称（类型）｜获取方式/链接） */
    @Column(name = "campus_resources", columnDefinition = "LONGTEXT")
    private String campusResources;

    /** 个性化学习路径（每行一步，循序渐进） */
    @Column(name = "learning_path", columnDefinition = "LONGTEXT")
    private String learningPath;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (generatedAt == null) {
            generatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
