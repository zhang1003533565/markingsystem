package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class EmailTaskProgress {
    private String taskId;
    private String type;
    private String status;
    private String message;
    private int recipientCount;
    private int total;
    private int successCount;
    private int failureCount;
    private int skippedCount;
    private int processedCount;
    private int pendingCount;
    private long rateLimitMillis;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<String> failedEmails = new ArrayList<>();
    private List<BatchEmailResult.EmailFailure> failures = new ArrayList<>();
}
