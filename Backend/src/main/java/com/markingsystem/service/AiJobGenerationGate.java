package com.markingsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 岗位相关 AI 生成（学习资源 / 任务卡）的全局限流门控。
 * deepseek-reasoner 联网推理耗时长，并发过高时容易互相拖垮导致前端超时。
 */
@Component
public class AiJobGenerationGate {

    private static final Logger log = LoggerFactory.getLogger(AiJobGenerationGate.class);

    private final Semaphore semaphore;
    private final long acquireTimeoutMs;

    public AiJobGenerationGate(
            @Value("${job-ai.max-concurrent:1}") int maxConcurrent,
            @Value("${job-ai.acquire-timeout-ms:240000}") long acquireTimeoutMs) {
        int permits = Math.max(1, maxConcurrent);
        this.semaphore = new Semaphore(permits, true);
        this.acquireTimeoutMs = Math.max(1000L, acquireTimeoutMs);
        log.info("岗位 AI 并发生成门控已启用: maxConcurrent={}, acquireTimeoutMs={}", permits, this.acquireTimeoutMs);
    }

    public <T> T call(String label, AiCallable<T> callable) throws Exception {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 AI 生成队列时被中断（" + label + "）", interrupted);
        }
        if (!acquired) {
            throw new IllegalStateException("AI 生成队列繁忙，请稍后再试（" + label + "）");
        }
        long started = System.currentTimeMillis();
        try {
            log.debug("获得 AI 门控许可: {}", label);
            return callable.call();
        } finally {
            semaphore.release();
            log.debug("释放 AI 门控许可: {}，耗时 {}ms", label, System.currentTimeMillis() - started);
        }
    }

    @FunctionalInterface
    public interface AiCallable<T> {
        T call() throws Exception;
    }
}
