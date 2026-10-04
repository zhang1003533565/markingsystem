package com.markingsystem.dto.assistant;

import com.markingsystem.service.llm.spi.Message;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantChatRequest {
    /**
     * 会话频道：
     * - floating：全局悬浮球聊天（短会话、临时）
     * - page：学生专属页面聊天（用于后续接入 RAG / 长期记忆）
     * 留空或传 null 时默认按 floating 处理，保持旧会话兼容。
     */
    private String channel;

    private String sessionId;
    private String message;
    private String quickCommand;
    private List<Message> history = new ArrayList<>();

    /**
     * 重新生成/重试标记：为 true 时，本轮开始前先把 Redis 历史回滚到最后一条
     * user 消息之前（移除残留的旧/半截 assistant 回复），避免刷新后旧回复复活、
     * 以及旧回复污染本轮 LLM 上下文。
     */
    private Boolean resetTail;
}