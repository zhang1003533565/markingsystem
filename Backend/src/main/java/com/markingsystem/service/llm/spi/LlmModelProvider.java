package com.markingsystem.service.llm.spi;

import java.util.function.Consumer;

public interface LlmModelProvider {

    String name();

    boolean isEnabled();

    ChatResponse chat(ChatRequest request) throws LlmException;

    void streamChat(ChatRequest request, Consumer<StreamEvent> eventConsumer) throws LlmException;

    default boolean supportsTools() {
        return false;
    }
}
