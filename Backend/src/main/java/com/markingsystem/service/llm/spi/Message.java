package com.markingsystem.service.llm.spi;

import com.markingsystem.dto.KnowledgeSearchHit;
import com.markingsystem.dto.assistant.AssistantPageCard;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String role;
    private String content;
    private String name;
    private List<ToolCallRequest> toolCalls;
    private String toolCallId;
    /** AI 回复引用的知识库来源（仅展示用，不发送给大模型） */
    private List<KnowledgeSearchHit> sources;
    /** AI 推送的发展助手页面卡片（仅展示用，不发送给大模型） */
    private List<AssistantPageCard> cards;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
