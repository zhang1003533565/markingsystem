package com.markingsystem.service.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.service.llm.spi.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OpenAiCompatibleProvider implements LlmModelProvider {

    private final RestTemplate syncRestTemplate;
    private final RestTemplate streamRestTemplate;
    private final ObjectMapper objectMapper;
    private final String providerName;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean streamEnabled;

    public OpenAiCompatibleProvider(
            ObjectMapper objectMapper,
            @Value("${ai.openai.api-key:}") String apiKey,
            @Value("${ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.openai.stream-enabled:true}") boolean streamEnabled,
            @Value("${ai.chat.sync-timeout-ms:20000}") int syncTimeoutMs,
            @Value("${ai.chat.stream-timeout-ms:60000}") int streamTimeoutMs) {
        this("openai", objectMapper, apiKey, baseUrl, model, streamEnabled, syncTimeoutMs, streamTimeoutMs);
    }

    public OpenAiCompatibleProvider(
            String providerName,
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            boolean streamEnabled,
            int syncTimeoutMs,
            int streamTimeoutMs) {

        this.providerName = providerName;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.streamEnabled = streamEnabled;

        SimpleClientHttpRequestFactory syncFactory = new SimpleClientHttpRequestFactory();
        syncFactory.setConnectTimeout(5000);
        syncFactory.setReadTimeout(syncTimeoutMs);
        this.syncRestTemplate = new RestTemplate(syncFactory);

        SimpleClientHttpRequestFactory streamFactory = new SimpleClientHttpRequestFactory();
        streamFactory.setConnectTimeout(5000);
        streamFactory.setReadTimeout(streamTimeoutMs);
        this.streamRestTemplate = new RestTemplate(streamFactory);
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        HttpHeaders headers = buildHeaders();
        Map<String, Object> body = buildBody(request, false);

        try {
            JsonNode response = syncRestTemplate.postForObject(
                    baseUrl + "/v1/chat/completions",
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
            return parseResponse(response);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw classifyException(e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<StreamEvent> eventConsumer) throws LlmException {
        if (!streamEnabled) {
            ChatResponse response = chat(request);
            if (response.getContent() != null) {
                eventConsumer.accept(StreamEvent.content(response.getContent()));
            }
            eventConsumer.accept(StreamEvent.done(response.getToolCalls()));
            return;
        }

        HttpHeaders headers = buildHeaders();
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        Map<String, Object> body = buildBody(request, true);

        RequestCallback requestCallback = streamRestTemplate.httpEntityCallback(new HttpEntity<>(body, headers), JsonNode.class);

        ResponseExtractor<Void> responseExtractor = (ClientHttpResponse response) -> {
            StreamToolCallAccumulator toolAccumulator = new StreamToolCallAccumulator();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.path("choices");
                        if (!choices.isArray() || choices.isEmpty()) continue;
                        JsonNode delta = choices.path(0).path("delta");
                        JsonNode content = delta.path("content");
                        if (!content.isMissingNode() && content.asText() != null) {
                            eventConsumer.accept(StreamEvent.content(content.asText()));
                        }
                        toolAccumulator.accumulate(delta.path("tool_calls"));
                    } catch (Exception ignored) {
                    }
                }
            }
            eventConsumer.accept(StreamEvent.done(toolAccumulator.result()));
            return null;
        };

        try {
            streamRestTemplate.execute(baseUrl + "/v1/chat/completions", HttpMethod.POST, requestCallback, responseExtractor);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw classifyException(e);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : model);
        body.put("stream", stream);
        body.put("messages", convertMessages(request.getMessages()));
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) body.put("max_tokens", request.getMaxTokens());
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
        }
        return body;
    }

    private List<Map<String, Object>> convertMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", m.getRole());
            item.put("content", buildContent(m));
            if (m.getName() != null) item.put("name", m.getName());
            if (m.getToolCallId() != null) item.put("tool_call_id", m.getToolCallId());
            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCallRequest tc : m.getToolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.getId());
                    tcMap.put("type", tc.getType());
                    Map<String, Object> fnMap = new LinkedHashMap<>();
                    fnMap.put("name", tc.getFunction().getName());
                    fnMap.put("arguments", tc.getFunction().getArguments());
                    tcMap.put("function", fnMap);
                    tcs.add(tcMap);
                }
                item.put("tool_calls", tcs);
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 构建消息 content：纯文本时保持字符串；携带图片时切换为 OpenAI 兼容的
     * content 数组（text + image_url），供视觉模型读取证书等图片。
     */
    private Object buildContent(Message m) {
        String text = m.getContent() != null ? m.getContent() : "";
        if (m.getImageUrls() == null || m.getImageUrls().isEmpty()) {
            return text;
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        if (!text.isBlank()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", text);
            parts.add(textPart);
        }
        for (String imageUrl : m.getImageUrls()) {
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image_url");
            Map<String, Object> url = new LinkedHashMap<>();
            url.put("url", imageUrl);
            imagePart.put("image_url", url);
            parts.add(imagePart);
        }
        return parts;
    }

    private ChatResponse parseResponse(JsonNode response) {
        if (response == null) {
            throw new LlmException(LlmException.Code.MODEL_UNAVAILABLE, name(), "OpenAI 返回空响应");
        }
        JsonNode messageNode = response.path("choices").path(0).path("message");
        String content = messageNode.path("content").asText(null);

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setContent(content);
        chatResponse.setModel(response.path("model").asText(model));
        chatResponse.setProvider(name());
        chatResponse.setPromptTokens(response.path("usage").path("prompt_tokens").asInt(0));
        chatResponse.setCompletionTokens(response.path("usage").path("completion_tokens").asInt(0));

        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<ToolCallRequest> toolCalls = new ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                ToolCallRequest req = new ToolCallRequest();
                req.setId(tc.path("id").asText(null));
                req.setType(tc.path("type").asText("function"));
                ToolCallRequest.Function fn = new ToolCallRequest.Function();
                fn.setName(tc.path("function").path("name").asText(null));
                fn.setArguments(tc.path("function").path("arguments").asText(null));
                req.setFunction(fn);
                toolCalls.add(req);
            }
            chatResponse.setToolCalls(toolCalls);
        }
        return chatResponse;
    }

    private LlmException classifyException(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("read timed out")) {
            return new LlmException(LlmException.Code.MODEL_TIMEOUT, name(), "模型调用超时", e);
        }
        if (msg.contains("unauthorized") || msg.contains("401")) {
            return new LlmException(LlmException.Code.AUTH_API_KEY_INVALID, name(), "API Key 无效", e);
        }
        if (msg.contains("rate limit") || msg.contains("429")) {
            return new LlmException(LlmException.Code.MODEL_RATE_LIMIT, name(), "模型调用频率受限", e);
        }
        return new LlmException(LlmException.Code.MODEL_UNAVAILABLE, name(), "模型调用失败: " + e.getMessage(), e);
    }
}
