package com.markingsystem.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchEmailResult {
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
    private List<String> failedEmails;
    private List<EmailFailure> failures;

    @Data
    public static class EmailFailure {
        private String email;
        private String reason;
    }
}
