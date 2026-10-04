package com.markingsystem.controller.student;

import com.markingsystem.exception.RateLimitException;
import com.markingsystem.service.JobTaskCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 学生端岗位学习任务卡接口（以岗定教）。
 * - GET /{recommendationId} 自动获取（有缓存返回缓存，无缓存则 AI 生成，计入任务卡板块额度）
 * - POST /{recommendationId}/refresh 刷新任务卡（需全部步骤完成；刷新最多 3 次/天，计入板块 5 次）
 * - GET /quota             查询今日任务卡板块额度使用情况
 *
 * 安全策略：
 * - 500 响应仅返回通用文案，详细异常记录到服务端日志，避免泄露 AI 解析细节或配置信息
 * - generate=false 且无缓存时 data 可为 null；不可用 Map.of（禁止 null 值），否则会 NPE 被误报成生成失败
 */
@RestController
@RequestMapping("/api/student/job-task-cards")
public class StudentJobTaskCardController {

    private static final Logger log = LoggerFactory.getLogger(StudentJobTaskCardController.class);

    private final JobTaskCardService jobTaskCardService;

    public StudentJobTaskCardController(JobTaskCardService jobTaskCardService) {
        this.jobTaskCardService = jobTaskCardService;
    }

    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuotaInfo(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", jobTaskCardService.getQuotaInfo(userId)
        ));
    }

    @GetMapping("/{recommendationId}")
    public ResponseEntity<Map<String, Object>> getTaskCard(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId,
            @RequestParam(value = "generate", required = false, defaultValue = "true") boolean generate) {
        try {
            Object data = generate
                    ? jobTaskCardService.getOrCreateTaskCard(userId, recommendationId)
                    : jobTaskCardService.getCachedTaskCard(userId, recommendationId);
            Map<String, Object> body = new HashMap<>();
            body.put("code", 200);
            body.put("data", data);
            body.put("quota", jobTaskCardService.getQuotaInfo(userId));
            return ResponseEntity.ok(body);
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
            log.error("岗位学习任务卡获取失败 userId={} recommendationId={}", userId, recommendationId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "学习任务卡生成失败，请稍后再试"
            ));
        }
    }

    @PostMapping("/{recommendationId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshTaskCard(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        try {
            Object data = jobTaskCardService.refreshTaskCard(userId, recommendationId);
            Map<String, Object> body = new HashMap<>();
            body.put("code", 200);
            body.put("data", data);
            body.put("quota", jobTaskCardService.getQuotaInfo(userId));
            return ResponseEntity.ok(body);
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
            log.error("岗位学习任务卡刷新失败 userId={} recommendationId={}", userId, recommendationId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "学习任务卡刷新失败，请稍后再试"
            ));
        }
    }
}
