package com.markingsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WeeklyJobRecommendationScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyJobRecommendationScheduler.class);

    private final WeeklyJobRecommendationService weeklyJobRecommendationService;

    public WeeklyJobRecommendationScheduler(WeeklyJobRecommendationService weeklyJobRecommendationService) {
        this.weeklyJobRecommendationService = weeklyJobRecommendationService;
    }

    @Scheduled(cron = "${job-recommendation.weekly.cron:0 0 6 ? * MON}")
    public void refreshWeeklyJobRecommendations() {
        try {
            weeklyJobRecommendationService.refreshCurrentWeek();
            // 刷新成功后清理超过 1 周的旧数据，只保留最近 1 周
            weeklyJobRecommendationService.cleanupOldData(1);
        } catch (Exception exception) {
            log.warn("Failed to refresh weekly job recommendations: {}", exception.getMessage());
        }
    }
}
