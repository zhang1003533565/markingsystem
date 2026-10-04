package com.markingsystem.service.llm.provider;

import com.markingsystem.service.llm.spi.*;
import java.util.function.Consumer;

public class MockModelProvider implements LlmModelProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        String content = buildReply(request);
        ChatResponse response = new ChatResponse();
        response.setContent(content);
        response.setModel("mock");
        response.setProvider(name());
        response.setPromptTokens(0);
        response.setCompletionTokens(0);
        return response;
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<StreamEvent> eventConsumer) throws LlmException {
        String content = buildReply(request);
        for (char c : content.toCharArray()) {
            eventConsumer.accept(StreamEvent.content(String.valueOf(c)));
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        eventConsumer.accept(StreamEvent.done(null));
    }

    private String buildReply(ChatRequest request) {
        String userMessage = request.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(Message::getContent)
                .orElse("");
        return "当前使用的是 Mock 模型（ai.chat.provider=mock），无法接入真实大模型。"
                + "你发送的内容是：「" + userMessage + "」。"
                + "如需真实 AI 回复，请将 provider 切换为 deepseek 或 openai 并配置有效 API Key。";
    }
}
