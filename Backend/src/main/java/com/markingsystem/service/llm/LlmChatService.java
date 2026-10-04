package com.markingsystem.service.llm;

import com.markingsystem.service.llm.provider.MockModelProvider;
import com.markingsystem.service.llm.spi.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class LlmChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);

    @Value("${ai.chat.provider:mock}")
    private String configuredProvider;

    /**
     * 真实模型失败 / 未启用时是否静默降级为 Mock 模型。
     * 默认关闭：生产环境模型异常应直接报错，而不是悄悄返回 Mock 文案误导用户；
     * 本地开发直接把 ai.chat.provider 设为 mock 即可，无需打开该开关。
     */
    @Value("${ai.chat.mock-fallback-enabled:false}")
    private boolean mockFallbackEnabled;

    @Autowired
    private List<LlmModelProvider> providers;

    @Autowired
    private MockModelProvider mockProvider;

    private Map<String, LlmModelProvider> providerMap;

    @PostConstruct
    public void init() {
        providerMap = providers.stream()
                .collect(Collectors.toMap(LlmModelProvider::name, p -> p));
        log.info("LLM providers loaded: {}", providerMap.keySet());
    }

    public ChatResponse chat(ChatRequest request) {
        LlmModelProvider provider = selectProvider(false);
        try {
            ChatResponse response = provider.chat(request);
            response.setProvider(provider.name());
            return response;
        } catch (LlmException e) {
            log.warn("Provider {} failed: {} ({})", provider.name(), e.getMessage(), e.getCode());
            if (!mockFallbackEnabled) {
                throw e;
            }
            return fallbackChat(request, e);
        }
    }

    public void streamChat(ChatRequest request, Consumer<StreamEvent> eventConsumer) {
        LlmModelProvider provider = selectProvider(true);
        try {
            provider.streamChat(request, eventConsumer);
        } catch (LlmException e) {
            log.warn("Provider {} stream failed: {} ({})", provider.name(), e.getMessage(), e.getCode());
            if (!mockFallbackEnabled) {
                throw e;
            }
            fallbackStream(request, eventConsumer, e);
        }
    }

    private LlmModelProvider selectProvider(boolean forStream) {
        LlmModelProvider configured = providerMap.get(configuredProvider);
        if (configured != null && configured.isEnabled()) {
            return configured;
        }

        if (!mockFallbackEnabled) {
            throw new LlmException(LlmException.Code.MODEL_UNAVAILABLE, configuredProvider,
                    "AI 模型不可用：provider=" + configuredProvider + " 未启用或未注册");
        }
        if (configured != null && !configured.isEnabled()) {
            log.warn("Configured provider {} is not enabled, fallback to mock", configuredProvider);
        } else {
            log.warn("Unknown provider {}, fallback to mock", configuredProvider);
        }
        return mockProvider;
    }

    private ChatResponse fallbackChat(ChatRequest request, LlmException cause) {
        ChatResponse response = mockProvider.chat(request);
        response.setProvider(mockProvider.name());
        response.setContent("[已切换为 Mock 模型] " + response.getContent() + "（原因：" + cause.getMessage() + "）");
        return response;
    }

    private void fallbackStream(ChatRequest request, Consumer<StreamEvent> eventConsumer, LlmException cause) {
        eventConsumer.accept(StreamEvent.content("[已切换为 Mock 模型] "));
        mockProvider.streamChat(request, eventConsumer);
    }

    public String currentProviderName() {
        return selectProvider(false).name();
    }
}
