package com.markingsystem.dto;

import java.time.LocalDateTime;

/**
 * 岗位学习任务卡 DTO。
 * 用于前端展示和 AI 生成结果传输。
 */
public class JobTaskCardDTO {

    private Long id;
    private Long recommendationId;
    private String jobTitle;
    private String taskName;
    private String workScenario;
    private String steps;
    private String safetyPoints;
    private String knowledgePoints;
    private String skillPoints;
    private String campusResources;
    private String learningPath;
    private String source;
    private String modelName;
    private LocalDateTime generatedAt;

    public JobTaskCardDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(Long recommendationId) {
        this.recommendationId = recommendationId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getWorkScenario() {
        return workScenario;
    }

    public void setWorkScenario(String workScenario) {
        this.workScenario = workScenario;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public String getSafetyPoints() {
        return safetyPoints;
    }

    public void setSafetyPoints(String safetyPoints) {
        this.safetyPoints = safetyPoints;
    }

    public String getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(String knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public String getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(String skillPoints) {
        this.skillPoints = skillPoints;
    }

    public String getCampusResources() {
        return campusResources;
    }

    public void setCampusResources(String campusResources) {
        this.campusResources = campusResources;
    }

    public String getLearningPath() {
        return learningPath;
    }

    public void setLearningPath(String learningPath) {
        this.learningPath = learningPath;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
