package com.markingsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.service.llm.provider.DeepSeekModelProvider;
import com.markingsystem.service.llm.provider.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 真实大模型 Provider 的自动装配。
 *
 * <p>仅当 {@code ai.chat.enable=true}（默认）时注册 deepseek / openai Provider；
 * Mock 与 Embedding 客户端不依赖聊天开关，单独在 {@link LlmCommonBeanConfiguration}
 * 中无条件注册，避免关闭 AI 对话后启动失败。
 */
@Configuration
@ConditionalOnProperty(prefix = "ai.chat", name = "enable", havingValue = "true", matchIfMissing = true)
public class LlmModelAutoConfiguration {

    @Bean
    public DeepSeekModelProvider deepSeekModelProvider(
            ObjectMapper objectMapper,
            @Value("${ai.deepseek.api-key:}") String apiKey,
            @Value("${ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${ai.deepseek.model:deepseek-chat}") String model,
            @Value("${ai.deepseek.stream-enabled:true}") boolean streamEnabled,
            @Value("${ai.chat.sync-timeout-ms:20000}") int syncTimeoutMs,
            @Value("${ai.chat.stream-timeout-ms:60000}") int streamTimeoutMs) {
        return new DeepSeekModelProvider(objectMapper, apiKey, baseUrl, model, streamEnabled, syncTimeoutMs, streamTimeoutMs);
    }

    @Bean
    public OpenAiCompatibleProvider openAiCompatibleProvider(
            ObjectMapper objectMapper,
            @Value("${ai.openai.api-key:}") String apiKey,
            @Value("${ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.openai.stream-enabled:true}") boolean streamEnabled,
            @Value("${ai.chat.sync-timeout-ms:20000}") int syncTimeoutMs,
            @Value("${ai.chat.stream-timeout-ms:60000}") int streamTimeoutMs) {
        return new OpenAiCompatibleProvider(objectMapper, apiKey, baseUrl, model, streamEnabled, syncTimeoutMs, streamTimeoutMs);
    }
}
