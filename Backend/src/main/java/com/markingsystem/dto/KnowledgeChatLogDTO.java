package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话日志传输对象。
 */
@Data
public class KnowledgeChatLogDTO {

    private Long id;

    /** 所属应用；null 表示学生页面聊天 */
    private Long appId;

    /** 应用名称（只读展示用，page 聊天为「学生页面聊天」） */
    private String appName;

    private String sessionId;

    private String userName;

    /** 学生学号（学生页面聊天时可为空） */
    private String studentNo;

    /** 学生班级（学生页面聊天时可为空） */
    private String className;

    private String userMessage;

    private String assistantMessage;

    /** 引用的知识库来源（JSON 数组字符串） */
    private String sourcesJson;

    /** 反馈：GOOD / BAD / null */
    private String feedback;

    private LocalDateTime createdAt;
}