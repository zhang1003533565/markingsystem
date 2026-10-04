package com.markingsystem.service.llm.spi;

import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {
    private String content;
    private String model;
    private String provider;
    private Integer promptTokens;
    private Integer completionTokens;
    private List<ToolCallRequest> toolCalls;
}
