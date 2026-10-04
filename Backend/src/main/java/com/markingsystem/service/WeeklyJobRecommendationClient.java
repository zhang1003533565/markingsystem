package com.markingsystem.service;

import java.util.List;

@FunctionalInterface
public interface WeeklyJobRecommendationClient {

    List<GeneratedJobRecommendation> generateRecommendations() throws Exception;

    /** 数据来源标识，用于持久化到数据库 source 字段 */
    default String source() {
        return "unknown";
    }

    /** 模型/工具名称标识 */
    default String modelName() {
        return "unknown";
    }

    record GeneratedJobRecommendation(
            String jobTitle,
            String salary,
            String skills,
            String recruitmentLink
    ) {
    }
}
