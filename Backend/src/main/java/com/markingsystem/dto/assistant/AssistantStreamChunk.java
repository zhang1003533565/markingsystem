package com.markingsystem.dto.assistant;

import com.markingsystem.dto.KnowledgeSearchHit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantStreamChunk {
    private String chunk;
    private Boolean done = false;
    private String model;
    private String provider;
    private String sessionId;
    /** 本轮回答引用的知识库条目（RAG 命中，done 事件携带） */
    private List<KnowledgeSearchHit> sources;
    /** 本轮推送的发展助手页面卡片（done 事件携带） */
    private List<AssistantPageCard> cards;

    public AssistantStreamChunk(String chunk, Boolean done) {
        this.chunk = chunk;
        this.done = done;
    }
}
