package com.markingsystem.service.llm.spi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 流式 tool_calls 增量累积器（OpenAI 兼容格式）。
 *
 * <p>SSE 流中 {@code delta.tool_calls} 按 index 分片到达：首个分片携带
 * {@code id/type/function.name}，后续分片仅携带 {@code function.arguments} 的增量，
 * 需要按 index 合并并按序拼接 arguments。
 */
public class StreamToolCallAccumulator {

    private final Map<Integer, ToolCallRequest> byIndex = new TreeMap<>();

    public void accumulate(JsonNode deltaToolCalls) {
        if (deltaToolCalls == null || !deltaToolCalls.isArray()) {
            return;
        }
        for (JsonNode item : deltaToolCalls) {
            int index = item.path("index").asInt(0);
            ToolCallRequest tcr = byIndex.computeIfAbsent(index, k -> new ToolCallRequest());
            if (item.hasNonNull("id")) {
                tcr.setId(item.path("id").asText());
            }
            if (item.hasNonNull("type")) {
                tcr.setType(item.path("type").asText("function"));
            }
            JsonNode fn = item.path("function");
            if (!fn.isMissingNode()) {
                if (tcr.getFunction() == null) {
                    tcr.setFunction(new ToolCallRequest.Function());
                }
                if (fn.hasNonNull("name")) {
                    tcr.getFunction().setName(fn.path("name").asText());
                }
                if (fn.hasNonNull("arguments")) {
                    String existing = tcr.getFunction().getArguments();
                    String delta = fn.path("arguments").asText("");
                    tcr.getFunction().setArguments(existing == null ? delta : existing + delta);
                }
            }
        }
    }

    public List<ToolCallRequest> result() {
        return new ArrayList<>(byIndex.values());
    }

    public boolean isEmpty() {
        return byIndex.isEmpty();
    }
}
