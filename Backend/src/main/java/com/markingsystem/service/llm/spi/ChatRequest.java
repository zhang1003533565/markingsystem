package com.markingsystem.service.llm.spi;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {
    private String model;
    private List<Message> messages = new ArrayList<>();
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private Boolean stream = false;
    private List<ToolDefinition> tools;
    private Map<String, Object> extra;
}
