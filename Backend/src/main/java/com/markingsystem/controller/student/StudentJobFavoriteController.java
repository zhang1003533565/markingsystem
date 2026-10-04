package com.markingsystem.controller.student;

import com.markingsystem.service.JobFavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/student/job-favorites")
public class StudentJobFavoriteController {

    private final JobFavoriteService jobFavoriteService;

    public StudentJobFavoriteController(JobFavoriteService jobFavoriteService) {
        this.jobFavoriteService = jobFavoriteService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFavorites(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", jobFavoriteService.listFavoriteJobs(userId)
        ));
    }

    @GetMapping("/ids")
    public ResponseEntity<Map<String, Object>> getFavoriteIds(
            @RequestHeader("X-User-Id") Long userId) {
        Set<Long> ids = jobFavoriteService.getFavoriteRecommendationIds(userId);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", ids
        ));
    }

    @PostMapping("/{recommendationId}")
    public ResponseEntity<Map<String, Object>> addFavorite(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        try {
            jobFavoriteService.addFavorite(userId, recommendationId);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "收藏成功"
            ));
        } catch (IllegalStateException exception) {
            // 收藏数量超出上限（6 个）
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", exception.getMessage()
            ));
        }
    }

    @DeleteMapping("/{recommendationId}")
    public ResponseEntity<Map<String, Object>> removeFavorite(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long recommendationId) {
        jobFavoriteService.removeFavorite(userId, recommendationId);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "已取消收藏"
        ));
    }
}
