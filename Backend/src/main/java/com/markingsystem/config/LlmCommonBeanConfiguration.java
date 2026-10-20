package com.markingsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.service.EmbeddingConfigService;
import com.markingsystem.service.llm.EmbeddingClient;
import com.markingsystem.service.llm.provider.MockModelProvider;
import com.markingsystem.service.llm.provider.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 与聊天开关无关的 LLM 基础设施 Bean。
 *
 * <p>{@link MockModelProvider} 被 {@code LlmChatService} 无条件引用（未配置有效
 * Provider / Provider 失败时兜底），{@link EmbeddingClient} 被知识库与管理员页面
 * 无条件引用；{@link OpenAiCompatibleProvider}（openai-vision）被 AI 审核助手引用。
 * 三者不随 {@code ai.chat.enable} 关闭而缺失，否则关闭 AI 对话会导致相关功能启动失败。
 */
@Configuration
public class LlmCommonBeanConfiguration {

    @Bean
    public MockModelProvider mockModelProvider() {
        return new MockModelProvider();
    }

    /**
     * 知识库向量化客户端（OpenAI 兼容 embedding API）。
     * 配置（base-url/model/api-key/enabled）由管理员在前端维护，存 system_setting 表，
     * 未配置时回退 application.yml 的 ai.embedding.* 默认值。
     */
    @Bean
    public EmbeddingClient embeddingClient(ObjectMapper objectMapper, EmbeddingConfigService embeddingConfigService) {
        return new EmbeddingClient(objectMapper, embeddingConfigService);
    }

    /**
     * AI 审核助手专用视觉模型（证书识别 / 审核建议）。
     * 复用 OpenAI 兼容协议，独立 name 避免与聊天 Provider 冲突；仅同步调用、不开启流式。
     */
    @Bean
    public OpenAiCompatibleProvider visionModelProvider(
            ObjectMapper objectMapper,
            @Value("${ai.vision.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${ai.vision.base-url:https://api.openai.com}") String baseUrl,
            @Value("${ai.vision.model:gpt-4o-mini}") String model,
            @Value("${ai.chat.sync-timeout-ms:20000}") int syncTimeoutMs) {
        return new OpenAiCompatibleProvider("openai-vision", objectMapper, apiKey, baseUrl, model, false, syncTimeoutMs, 60000);
    }
}
