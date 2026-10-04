package com.markingsystem.controller.student;

import com.markingsystem.dto.LearningProgressRequest;
import com.markingsystem.service.LearningProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 学生端岗位学习进度接口（发展助手「学习记录」）。
 * - GET  /overview                   汇总所有收藏岗位的学习完成度
 * - GET  /job/{recommendationId}     查询某岗位的完成记录
 * - POST /complete                   标记某项学习内容已完成
 * - POST /uncomplete                 取消某项学习内容的完成标记
 */
@RestController
@RequestMapping("/api/student/learning-progress")
public class StudentLearningProgressController {

    private static final Logger log = LoggerFactory.getLogger(StudentLearningProgressController.class);

    private final LearningProgressService learningProgressService;

    public StudentLearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", learningProgressService.getOverview(userId)
        ));
    }

    /** 每日打卡概览：今日是否打卡、连续天数、最近 7 天日期 */
    @GetMapping("/daily-checkin")
    public ResponseEntity<Map<String, Object>> getDailyCheckin(
            @RequestHeader("X-User-Id") Long userId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", learningProgressService.getDailyCheckinOverview(userId)
            ));
        } catch (Exception exception) {
            log.error("获取每日打卡概览失败 userId={}", userId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "获取打卡概览失败，请稍后再试"
            ));
        }
    }

    @GetMapping("/job/{recommendationId}")
    public ResponseEntity<Map<String, Object>> getJobProgress(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", learningProgressService.getJobProgress(userId, recommendationId)
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        }
    }

    /** 解锁学习资源（打开阅读器时调用，标记完成前必须先解锁） */
    @PostMapping("/unlock")
    public ResponseEntity<Map<String, Object>> unlockResource(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LearningProgressRequest request) {
        try {
            learningProgressService.unlockResource(
                    userId,
                    request.getRecommendationId(),
                    request.getItemKey(),
                    request.getItemTitle());
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "已解锁"
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("解锁学习资源失败 userId={} request={}", userId, request, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "操作失败，请稍后再试"
            ));
        }
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> markCompleted(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LearningProgressRequest request) {
        try {
            learningProgressService.markCompleted(
                    userId,
                    request.getRecommendationId(),
                    request.getItemType(),
                    request.getItemKey(),
                    request.getItemTitle());
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "已标记完成"
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("标记学习完成失败 userId={} request={}", userId, request, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "操作失败，请稍后再试"
            ));
        }
    }

    @PostMapping("/uncomplete")
    public ResponseEntity<Map<String, Object>> unmarkCompleted(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LearningProgressRequest request) {
        try {
            learningProgressService.unmarkCompleted(
                    userId,
                    request.getRecommendationId(),
                    request.getItemType(),
                    request.getItemKey());
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "已取消完成标记"
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            log.error("取消学习完成标记失败 userId={} request={}", userId, request, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "操作失败，请稍后再试"
            ));
        }
    }
}
