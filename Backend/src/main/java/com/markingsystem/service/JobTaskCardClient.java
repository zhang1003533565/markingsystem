package com.markingsystem.service;

/**
 * 岗位学习任务卡生成客户端。
 * 将企业真实工作任务转化为课堂实训学习型任务（以岗定教）。
 */
public interface JobTaskCardClient {

    String source();

    String modelName();

    /**
     * AI 生成的单张学习任务卡。
     *
     * @param taskName        学习型任务名称
     * @param workScenario    工作情境
     * @param steps           分步操作指南（每行一步）
     * @param safetyPoints    安全要点（每行一条）
     * @param knowledgePoints 对应知识点（每行一条）
     * @param skillPoints     对应技能点（每行一条）
     * @param campusResources 校内实训资源（每行一条：名称（类型）｜获取方式/链接）
     * @param learningPath    个性化学习路径（每行一步）
     */
    record GeneratedTaskCard(
            String taskName,
            String workScenario,
            String steps,
            String safetyPoints,
            String knowledgePoints,
            String skillPoints,
            String campusResources,
            String learningPath) {
    }

    /**
     * 为某岗位生成学习任务卡。
     *
     * @param jobTitle       岗位名称
     * @param studentProfile 学生情况摘要（专业/年级等，用于个性化学习路径，可为空）
     */
    GeneratedTaskCard generateTaskCard(String jobTitle, String studentProfile) throws Exception;
}
