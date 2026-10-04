package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BatchAnalysisTaskDTO {
    private String taskId;
    private String status;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
