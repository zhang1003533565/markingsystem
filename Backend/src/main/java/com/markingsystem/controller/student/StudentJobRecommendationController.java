package com.markingsystem.controller.student;

import com.markingsystem.service.WeeklyJobRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/student/job-recommendations")
public class StudentJobRecommendationController {

    private final WeeklyJobRecommendationService weeklyJobRecommendationService;

    public StudentJobRecommendationController(WeeklyJobRecommendationService weeklyJobRecommendationService) {
        this.weeklyJobRecommendationService = weeklyJobRecommendationService;
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", weeklyJobRecommendationService.listLatestOrRefresh()
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "每周热门岗位推荐获取失败：" + exception.getMessage()
            ));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", weeklyJobRecommendationService.refreshCurrentWeek(),
                    "message", "每周热门岗位推荐已刷新"
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "每周热门岗位推荐刷新失败：" + exception.getMessage()
            ));
        }
    }
}
