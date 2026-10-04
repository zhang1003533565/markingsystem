package com.markingsystem.dto.assistant;

import com.markingsystem.dto.KnowledgeSearchHit;
import lombok.Data;

import java.util.List;

@Data
public class AssistantChatResponse {
    private String content;
    private String model;
    private String provider;
    private String sessionId;
    private Boolean fallback = false;
    /** 本轮回答引用的知识库条目（RAG 命中） */
    private List<KnowledgeSearchHit> sources;
    /** 本轮推送的发展助手页面卡片 */
    private List<AssistantPageCard> cards;
}
