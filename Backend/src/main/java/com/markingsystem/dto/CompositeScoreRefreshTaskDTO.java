package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompositeScoreRefreshTaskDTO {
    private String taskId;
    private String status;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private String message;
    private String academicYear;
    private String scopeLabel;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
