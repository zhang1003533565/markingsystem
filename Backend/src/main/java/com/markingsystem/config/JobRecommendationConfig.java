package com.markingsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.service.DeepSeekJobLearningResourceClient;
import com.markingsystem.service.DeepSeekJobTaskCardClient;
import com.markingsystem.service.DeepSeekWeeklyJobRecommendationClient;
import com.markingsystem.service.JobLearningResourceClient;
import com.markingsystem.service.JobTaskCardClient;
import com.markingsystem.service.McpJobsWeeklyJobRecommendationClient;
import com.markingsystem.service.WeeklyJobRecommendationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 岗位推荐数据源配置。
 * 通过 job-recommendation.provider 切换实现：
 * - mcp-jobs：通过 HTTP 桥接调用 mcp-jobs 获取真实招聘数据（默认）
 * - deepseek：调用 DeepSeek 大模型生成岗位数据
 */
@Configuration
public class JobRecommendationConfig {

    private static final Logger log = LoggerFactory.getLogger(JobRecommendationConfig.class);

    @Value("${job-recommendation.provider:deepseek}")
    private String provider;

    @Bean
    public WeeklyJobRecommendationClient weeklyJobRecommendationClient(ObjectMapper objectMapper) {
        log.info("岗位推荐数据源 provider: {}", provider);
        if ("deepseek".equalsIgnoreCase(provider)) {
            return new DeepSeekWeeklyJobRecommendationClient(
                    objectMapper,
                    deepseekApiKey,
                    deepseekBaseUrl,
                    jobDeepseekModel,
                    connectTimeoutMs,
                    readTimeoutMs
            );
        }
        return new McpJobsWeeklyJobRecommendationClient(
                objectMapper,
                bridgeUrl,
                bridgeConnectTimeoutMs,
                bridgeReadTimeoutMs
        );
    }

    /**
     * 岗位学习资源生成客户端。
     * 使用 deepseek-reasoner 联网搜索，读超时单独放宽（联网搜索 + 推理耗时较长）。
     */
    @Bean
    public JobLearningResourceClient jobLearningResourceClient(ObjectMapper objectMapper) {
        return new DeepSeekJobLearningResourceClient(
                objectMapper,
                deepseekApiKey,
                deepseekBaseUrl,
                jobDeepseekModel,
                connectTimeoutMs,
                learningResourceReadTimeoutMs
        );
    }

    /**
     * 岗位学习任务卡生成客户端（以岗定教）。
     * 使用 deepseek-reasoner 联网搜索，读超时单独放宽。
     */
    @Bean
    public JobTaskCardClient jobTaskCardClient(ObjectMapper objectMapper) {
        return new DeepSeekJobTaskCardClient(
                objectMapper,
                deepseekApiKey,
                deepseekBaseUrl,
                jobDeepseekModel,
                connectTimeoutMs,
                learningResourceReadTimeoutMs
        );
    }

    // ── DeepSeek 参数 ─────────────────────────────────────────────────────

    @Value("${ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    /** 岗位推荐专用模型（支持联网搜索的版本） */
    @Value("${job-recommendation.deepseek.model:deepseek-reasoner}")
    private String jobDeepseekModel;

    @Value("${ai.analysis.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${ai.analysis.read-timeout-ms:20000}")
    private int readTimeoutMs;

    /** 学习资源生成专用读超时（deepseek-reasoner 联网搜索耗时较长） */
    @Value("${job-resource.read-timeout-ms:120000}")
    private int learningResourceReadTimeoutMs;

    // ── MCP-Jobs 桥接参数 ─────────────────────────────────────────────────

    @Value("${job-recommendation.mcp-jobs.bridge-url:http://localhost:3100}")
    private String bridgeUrl;

    @Value("${job-recommendation.mcp-jobs.connect-timeout-ms:5000}")
    private int bridgeConnectTimeoutMs;

    @Value("${job-recommendation.mcp-jobs.read-timeout-ms:120000}")
    private int bridgeReadTimeoutMs;
}
