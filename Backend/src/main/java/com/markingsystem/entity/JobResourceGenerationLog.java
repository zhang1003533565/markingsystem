package com.markingsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位资源生成日志。
 * 用于记录每次 AI 生成岗位学习资源的事件，配合限流策略：
 * - AUTO：用户切换 Tab 时自动触发的获取（不消耗用户手动刷新额度）
 * - MANUAL：用户点击刷新按钮触发的生成（占用每日手动刷新额度）
 *
 * 自动获取的总额度：每天 7 次（包含 MANUAL 调用）
 * 手动刷新的额度：每天 1 次（属于 AUTO 总额度的子集）
 */
@Data
@Entity
@Table(
        name = "job_resource_generation_logs",
        indexes = {
                @Index(name = "idx_jrgl_user_time", columnList = "user_id, generated_at"),
                @Index(name = "idx_jrgl_user_type_time", columnList = "user_id, generation_type, generated_at")
        }
)
public class JobResourceGenerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    /**
     * 生成类型，可选值：AUTO（切换 Tab 自动生成）、MANUAL（用户点击刷新）、
     * REGENERATE（清缓存强制重刷，每日 2 次）。
     * 业务上始终是有限集合，但使用字符串字面量读写，避免引入枚举类型。
     */
    @Column(name = "generation_type", nullable = false, length = 20)
    private String generationType;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
