package com.markingsystem.controller.student;

import com.markingsystem.dto.JobResourceAsyncResult;
import com.markingsystem.exception.RateLimitException;
import com.markingsystem.service.JobLearningResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 学生端岗位学习资源接口（资源板块每日 5 次、各刷新操作类型最多 3 次）。
 * AI 生成为异步：接口快速返回 status=generating，前端轮询至 ready/failed。
 */
@RestController
@RequestMapping("/api/student/job-resources")
public class StudentJobLearningResourceController {

    private static final Logger log = LoggerFactory.getLogger(StudentJobLearningResourceController.class);

    private final JobLearningResourceService jobLearningResourceService;

    public StudentJobLearningResourceController(JobLearningResourceService jobLearningResourceService) {
        this.jobLearningResourceService = jobLearningResourceService;
    }

    @PostMapping("/{recommendationId}/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateResources(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        try {
            return ResponseEntity.ok(toBody(
                    jobLearningResourceService.regenerateResources(userId, recommendationId),
                    userId,
                    "学习资源正在重新生成"
            ));
        } catch (RateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "code", 429,
                    "message", exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("岗位学习资源重新生成失败 userId={} recommendationId={}", userId, recommendationId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "岗位学习资源重新生成失败，请稍后再试"
            ));
        }
    }

    @DeleteMapping("/{recommendationId}/cache")
    public ResponseEntity<Map<String, Object>> clearCache(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        try {
            return ResponseEntity.ok(toBody(
                    jobLearningResourceService.regenerateResources(userId, recommendationId),
                    userId,
                    "学习资源正在重新生成"
            ));
        } catch (RateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "code", 429,
                    "message", exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("岗位学习资源重新生成失败 userId={} recommendationId={}", userId, recommendationId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "岗位学习资源重新生成失败，请稍后再试"
            ));
        }
    }

    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuotaInfo(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", jobLearningResourceService.getQuotaInfo(userId)
        ));
    }

    @GetMapping("/{recommendationId}")
    public ResponseEntity<Map<String, Object>> getResources(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId,
            @RequestParam(value = "generate", required = false, defaultValue = "true") boolean generate) {
        try {
            JobResourceAsyncResult result = generate
                    ? jobLearningResourceService.getOrCreateResources(userId, recommendationId)
                    : jobLearningResourceService.getCachedResources(userId, recommendationId);
            return ResponseEntity.ok(toBody(result, userId, null));
        } catch (RateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "code", 429,
                    "message", exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("岗位学习资源获取失败 userId={} recommendationId={}", userId, recommendationId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "岗位学习资源获取失败，请稍后再试"
            ));
        }
    }

    @PostMapping("/{recommendationId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshResources(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        try {
            return ResponseEntity.ok(toBody(
                    jobLearningResourceService.refreshResources(userId, recommendationId),
                    userId,
                    "学习资源正在刷新"
            ));
        } catch (RateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "code", 429,
                    "message", exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("岗位学习资源刷新失败 userId={} recommendationId={}", userId, recommendationId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "岗位学习资源刷新失败，请稍后再试"
            ));
        }
    }

    private Map<String, Object> toBody(JobResourceAsyncResult result, Long userId, String defaultMessage) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 200);
        body.put("status", result.getStatus());
        body.put("data", result.getData());
        body.put("quota", jobLearningResourceService.getQuotaInfo(userId));
        String message = result.getMessage();
        if (message == null || message.isBlank()) {
            message = defaultMessage;
        }
        if (message != null && !message.isBlank()) {
            body.put("message", message);
        }
        return body;
    }
}
