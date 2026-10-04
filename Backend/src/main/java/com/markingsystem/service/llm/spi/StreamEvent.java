package com.markingsystem.service.llm.spi;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 流式事件：携带文本增量 chunk，或在流结束时携带累积的工具调用列表。
 *
 * <p>provider 层约定：文本增量以 {@code chunk} 事件下发；流结束（[DONE] / EOF）时
 * 下发一个 {@code done=true} 事件，若本次模型请求了工具调用则附带 {@code toolCalls}。
 */
@Data
public class StreamEvent {

    private String chunk;

    private List<ToolCallRequest> toolCalls;

    private boolean done;

    public static StreamEvent content(String chunk) {
        StreamEvent event = new StreamEvent();
        event.setChunk(chunk);
        return event;
    }

    public static StreamEvent done(List<ToolCallRequest> toolCalls) {
        StreamEvent event = new StreamEvent();
        event.setDone(true);
        event.setToolCalls(toolCalls == null ? Collections.emptyList() : toolCalls);
        return event;
    }
}
