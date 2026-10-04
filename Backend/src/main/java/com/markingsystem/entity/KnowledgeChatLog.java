package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话日志（应用调试对话与学生页面聊天）。
 *
 * <p>appId 为 null 表示学生页面聊天（page 频道），否则为对应应用的调试对话；
 * feedback 记录管理员对回答的反馈：GOOD / BAD / null。
 */
@Data
@Entity
@Table(name = "knowledge_chat_log")
public class KnowledgeChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属应用；null 表示学生页面聊天 */
    @Column(name = "app_id")
    private Long appId;

    /** 会话 id */
    @Column(name = "session_id", length = 128)
    private String sessionId;

    /** 用户标识（学生姓名 / 管理员昵称） */
    @Column(name = "user_name", length = 128)
    private String userName;

    /** 学生学号（仅学生页面聊天空名归属用；非学生对话为空） */
    @Column(name = "student_no", length = 64)
    private String studentNo;

    /** 学生班级（仅学生页面聊天用于按班级聚合；非学生对话为空） */
    @Column(name = "class_name", length = 128)
    private String className;

    /** 用户消息 */
    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    /** AI 回复 */
    @Column(name = "assistant_message", columnDefinition = "TEXT")
    private String assistantMessage;

    /** 引用的知识库来源（JSON 数组） */
    @Column(name = "sources_json", columnDefinition = "TEXT")
    private String sourcesJson;

    /** 反馈：GOOD / BAD / null */
    @Column(length = 16)
    private String feedback;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}