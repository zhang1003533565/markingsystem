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
 * 学生岗位学习进度记录。
 * <p>
 * 记录学生在「岗位成长链路」中完成的每一项学习内容，用于：
 * - 学习资源（RESOURCE）：某项学习资源（图文微课）标记为已完成
 * - 任务卡步骤（TASK_STEP）：学习任务卡中的分步操作指南逐项打卡
 * - 路径步骤（PATH_STEP）：个性化学习路径中的每个阶段完成打卡
 * <p>
 * 同一用户对同一岗位的同一 itemType + itemKey 仅保留一条记录（唯一索引），
 * 取消收藏时按 recommendation_id 级联删除。
 */
@Data
@Entity
@Table(
        name = "learning_progress",
        indexes = {
                @Index(name = "idx_lp_user_job", columnList = "user_id, recommendation_id"),
                @Index(name = "idx_lp_user_job_type_key", columnList = "user_id, recommendation_id, item_type, item_key", unique = true)
        }
)
public class LearningProgress {

    /** itemType 常量：学习资源 */
    public static final String TYPE_RESOURCE = "RESOURCE";
    /** itemType 常量：任务卡分步操作 */
    public static final String TYPE_TASK_STEP = "TASK_STEP";
    /** itemType 常量：个性化学习路径步骤 */
    public static final String TYPE_PATH_STEP = "PATH_STEP";
    /** itemType 常量：每日打卡（recommendationId 传 0，itemKey 用 yyyy-MM-dd） */
    public static final String TYPE_DAILY_CHECKIN = "DAILY_CHECKIN";
    /** itemType 常量：学习资源解锁（打开阅读器后写入，标记 RESOURCE 完成前必须存在） */
    public static final String TYPE_RESOURCE_UNLOCK = "RESOURCE_UNLOCK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 收藏岗位 recommendationId */
    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    /** 记录类型：RESOURCE / TASK_STEP / PATH_STEP / DAILY_CHECKIN / RESOURCE_UNLOCK */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    /** 内容唯一键：资源 id、步骤序号、或打卡日期（yyyy-MM-dd） */
    @Column(name = "item_key", nullable = false, length = 100)
    private String itemKey;

    /** 内容标题（冗余展示用，可为空） */
    @Column(name = "item_title", length = 300)
    private String itemTitle;

    /** 是否已完成 */
    @Column(name = "completed", nullable = false)
    private Boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (completed == null) {
            completed = false;
        }
        if (completed && completedAt == null) {
            completedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (completed && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}
