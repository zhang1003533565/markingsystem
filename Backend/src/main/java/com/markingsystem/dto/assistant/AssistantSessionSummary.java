package com.markingsystem.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantSessionSummary {
    private String sessionId;
    private String title;
    private String preview;
    private Long updatedAt;
}