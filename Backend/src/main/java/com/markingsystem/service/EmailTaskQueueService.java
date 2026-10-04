package com.markingsystem.service;

import com.markingsystem.dto.BatchEmailResult;
import com.markingsystem.dto.EmailTaskProgress;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class EmailTaskQueueService {

    private static final Logger log = LoggerFactory.getLogger(EmailTaskQueueService.class);
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FAILED = "FAILED";

    private final LinkedBlockingQueue<String> queue;
    private final Map<String, EmailTaskProgress> tasks = new ConcurrentHashMap<>();
    private final Map<String, EmailTaskProcessor> processors = new ConcurrentHashMap<>();
    private final int maxRetainedTasks;
    private volatile boolean running = true;
    private Thread workerThread;

    public EmailTaskQueueService(
            @Value("${mail.queue.capacity:100}") int capacity,
            @Value("${mail.queue.max-retained-tasks:200}") int maxRetainedTasks
    ) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxRetainedTasks = maxRetainedTasks;
    }

    @PostConstruct
    public void startWorker() {
        workerThread = new Thread(this::runWorker, "email-task-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    public void stopWorker() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public EmailTaskProgress enqueue(String type, int recipientCount, int total, long rateLimitMillis,
                                     EmailTaskProcessor processor) {
        cleanupCompletedTasks();
        EmailTaskProgress progress = new EmailTaskProgress();
        progress.setTaskId(UUID.randomUUID().toString());
        progress.setType(type);
        progress.setStatus(STATUS_QUEUED);
        progress.setMessage("Email task queued.");
        progress.setRecipientCount(recipientCount);
        progress.setTotal(total);
        progress.setSkippedCount(Math.max(recipientCount - total, 0));
        progress.setPendingCount(total);
        progress.setRateLimitMillis(rateLimitMillis);
        progress.setCreatedAt(LocalDateTime.now());

        tasks.put(progress.getTaskId(), progress);
        processors.put(progress.getTaskId(), processor);
        if (!queue.offer(progress.getTaskId())) {
            tasks.remove(progress.getTaskId());
            processors.remove(progress.getTaskId());
            throw new IllegalStateException("Email task queue is full.");
        }
        return snapshot(progress);
    }

    public Optional<EmailTaskProgress> getTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskId)).map(this::snapshot);
    }

    public void markSuccess(EmailTaskProgress progress) {
        synchronized (progress) {
            progress.setSuccessCount(progress.getSuccessCount() + 1);
            markProcessed(progress);
        }
    }

    public void markFailure(EmailTaskProgress progress, String email, String reason) {
        synchronized (progress) {
            progress.setFailureCount(progress.getFailureCount() + 1);
            progress.getFailedEmails().add(email);
            BatchEmailResult.EmailFailure failure = new BatchEmailResult.EmailFailure();
            failure.setEmail(email);
            failure.setReason(reason);
            progress.getFailures().add(failure);
            markProcessed(progress);
        }
    }

    private void markProcessed(EmailTaskProgress progress) {
        progress.setProcessedCount(progress.getProcessedCount() + 1);
        progress.setPendingCount(Math.max(progress.getTotal() - progress.getProcessedCount(), 0));
        progress.setMessage(String.format("Processed %d/%d emails.", progress.getProcessedCount(), progress.getTotal()));
    }

    private void runWorker() {
        while (running || !queue.isEmpty()) {
            try {
                String taskId = queue.take();
                EmailTaskProgress progress = tasks.get(taskId);
                EmailTaskProcessor processor = processors.remove(taskId);
                if (progress == null || processor == null) {
                    continue;
                }
                runTask(progress, processor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    break;
                }
            } catch (Exception e) {
                log.error("Email task worker failed", e);
            }
        }
    }

    private void runTask(EmailTaskProgress progress, EmailTaskProcessor processor) {
        synchronized (progress) {
            progress.setStatus(STATUS_RUNNING);
            progress.setStartedAt(LocalDateTime.now());
            progress.setMessage("Email task is running.");
        }
        try {
            processor.process(progress);
            synchronized (progress) {
                progress.setStatus(buildDeliveryStatus(progress.getTotal(), progress.getSuccessCount()));
                progress.setMessage(buildResultMessage(progress));
                progress.setFinishedAt(LocalDateTime.now());
            }
        } catch (Exception e) {
            synchronized (progress) {
                progress.setStatus(STATUS_FAILED);
                progress.setMessage(e.getMessage() == null || e.getMessage().isBlank()
                        ? "Email task failed."
                        : e.getMessage());
                progress.setFinishedAt(LocalDateTime.now());
            }
            log.warn("Email task {} failed: {}", progress.getTaskId(), progress.getMessage());
        }
    }

    private String buildDeliveryStatus(int total, int successCount) {
        if (total == 0) {
            return "NO_RECIPIENT_EMAIL";
        }
        if (successCount == total) {
            return "SUCCESS";
        }
        if (successCount == 0) {
            return STATUS_FAILED;
        }
        return "PARTIAL_SUCCESS";
    }

    private String buildResultMessage(EmailTaskProgress progress) {
        if ("NO_RECIPIENT_EMAIL".equals(progress.getStatus())) {
            return "No recipient email addresses were available.";
        }
        if ("SUCCESS".equals(progress.getStatus())) {
            return String.format("Sent %d emails successfully.", progress.getSuccessCount());
        }
        if (STATUS_FAILED.equals(progress.getStatus())) {
            return String.format("All %d email sends failed.", progress.getFailureCount());
        }
        if ("PARTIAL_SUCCESS".equals(progress.getStatus())) {
            return String.format("Email task completed: %d succeeded, %d failed.",
                    progress.getSuccessCount(), progress.getFailureCount());
        }
        return "Email task completed.";
    }

    private void cleanupCompletedTasks() {
        if (tasks.size() <= maxRetainedTasks) {
            return;
        }
        List<EmailTaskProgress> completed = tasks.values().stream()
                .filter(task -> task.getFinishedAt() != null)
                .sorted(Comparator.comparing(EmailTaskProgress::getFinishedAt))
                .toList();
        int removeCount = Math.min(completed.size(), tasks.size() - maxRetainedTasks);
        for (int i = 0; i < removeCount; i++) {
            tasks.remove(completed.get(i).getTaskId());
        }
    }

    private EmailTaskProgress snapshot(EmailTaskProgress source) {
        synchronized (source) {
            EmailTaskProgress copy = new EmailTaskProgress();
            copy.setTaskId(source.getTaskId());
            copy.setType(source.getType());
            copy.setStatus(source.getStatus());
            copy.setMessage(source.getMessage());
            copy.setRecipientCount(source.getRecipientCount());
            copy.setTotal(source.getTotal());
            copy.setSuccessCount(source.getSuccessCount());
            copy.setFailureCount(source.getFailureCount());
            copy.setSkippedCount(source.getSkippedCount());
            copy.setProcessedCount(source.getProcessedCount());
            copy.setPendingCount(source.getPendingCount());
            copy.setRateLimitMillis(source.getRateLimitMillis());
            copy.setCreatedAt(source.getCreatedAt());
            copy.setStartedAt(source.getStartedAt());
            copy.setFinishedAt(source.getFinishedAt());
            copy.setFailedEmails(new ArrayList<>(source.getFailedEmails()));
            copy.setFailures(new ArrayList<>(source.getFailures()));
            return copy;
        }
    }

    @FunctionalInterface
    public interface EmailTaskProcessor {
        void process(EmailTaskProgress progress) throws Exception;
    }
}
