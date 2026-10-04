package com.markingsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeepSeekWeeklyJobRecommendationClient implements WeeklyJobRecommendationClient {

    private static final String PROMPT = """
        请按以下要求检索并输出近一周国内主流招聘平台（BOSS直聘、猎聘、前程无忧）上新发布的「软件工程 / 互联网研发」岗位中，热度排名前五的具体岗位，不要有冷门岗位。
          岗位范围（必须全部属于软件工程方向，优先覆盖以下细分，且五条尽量方向不重复）：
          - Java/Go/Python 后端、前端（React/Vue）、全栈、移动端（Android/iOS/鸿蒙）、测试开发/自动化测试、
            DevOps/SRE/云原生、数据开发、算法/大模型工程（偏工程落地，非纯科研）
          明确排除（出现则视为无效）：
          - 销售、运营、产品、设计、行政、人事、财务、教培、物流、生产制造、硬件产线、机械、汽车维修、
            新能源电池/电控拆装、土木、医护等非软件研发岗位
          热度排名依据：
          1. 近一周新增岗位发布数量
          2. 岗位的薪资竞争力（与同行业同期对比）
          3. 头部科技公司（如字节、华为、腾讯、阿里、美团、网易等）的招聘集中度
          输出要求：
          - 岗位名称必须具体，不得使用“后端开发”“前端开发”等笼统名称，须体现技术栈或业务细分方向。
          - skills 必须是软件工程相关技术栈（语言、框架、中间件、工程化工具等），不得出现非 IT 技能。
          - 只返回以下四个字段：岗位名称、薪资参考、所需技能、招聘链接
          - 招聘链接需为可直接访问的具体岗位页面URL，优先BOSS直聘、猎聘、企业官网链接，不得使用平台首页或搜索页
          - 若无法获取精确排名数据或无法实时访问招聘平台，直接返回空数组 []，严禁编造岗位，
            严禁在 jobTitle、salary、skills 中填写说明文字、道歉或“暂无”“无法提供”“需人工检索”等占位内容
            要求：
            1. 只返回 JSON，不要返回 Markdown。
            2. JSON 必须是数组，长度最多 5；完全无法获取真实数据时返回空数组 []。
            3. 每个元素必须包含 jobTitle、salary、skills、recruitmentLink 四个字段。
            4. jobTitle、salary、skills 必须是真实岗位信息，出现“暂无”“无法提供”等占位文字视为无效输出。
            5. skills 用中文逗号或英文逗号分隔，保持简洁。
            6. recruitmentLink 必须是可访问的招聘详情页或招聘搜索页链接。
            示例：
            [
              {
                "jobTitle": "Java 后端开发工程师（Spring Cloud 微服务）",
                "salary": "12k-20k",
                "skills": "Java, Spring Boot, MySQL, Redis, Kafka",
                "recruitmentLink": "https://example.com/jobs/java"
              }
            ]
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DeepSeekWeeklyJobRecommendationClient(
            ObjectMapper objectMapper,
            @Value("${ai.deepseek.api-key:}") String apiKey,
            @Value("${ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${ai.deepseek.model:deepseek-chat}") String model,
            @Value("${ai.analysis.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.analysis.read-timeout-ms:20000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String source() {
        return "deepseek-weekly-job";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<GeneratedJobRecommendation> generateRecommendations() throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "你是软件工程就业数据整理助手，只整理互联网/软件研发类岗位，必须输出可解析 JSON。"),
                Map.of("role", "user", "content", PROMPT)
        ));

        // 如果模型支持联网搜索，添加 search_channel 参数
        if ("deepseek-reasoner".equals(model)) {
            body.put("search_channel", "auto");
        }

        JsonNode response = restTemplate.postForObject(
                baseUrl + "/chat/completions",
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
        if (response == null) {
            throw new IllegalStateException("DeepSeek empty response");
        }

        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("DeepSeek content empty");
        }

        JsonNode root = objectMapper.readTree(unwrapJsonBlock(content));
        JsonNode items = root.isArray() ? root : root.path("jobs");
        if (!items.isArray()) {
            throw new IllegalStateException("DeepSeek job recommendation JSON is not an array");
        }

        List<GeneratedJobRecommendation> result = new ArrayList<>();
        for (JsonNode item : items) {
            result.add(new GeneratedJobRecommendation(
                    item.path("jobTitle").asText(item.path("岗位").asText("")),
                    item.path("salary").asText(item.path("薪资").asText("")),
                    item.path("skills").asText(item.path("所需技能").asText("")),
                    item.path("recruitmentLink").asText(item.path("招聘链接").asText(""))
            ));
        }
        return result;
    }

    private String unwrapJsonBlock(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > -1 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    /**
     * 处理工具调用，支持联网搜索
     */
    private String processToolCalls(JsonNode response, HttpHeaders headers) throws Exception {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek response has no choices");
        }

        JsonNode firstChoice = choices.path(0);
        JsonNode message = firstChoice.path("message");
        JsonNode toolCalls = message.path("tool_calls");

        // 如果没有工具调用，直接返回内容
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            String content = message.path("content").asText();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("DeepSeek content empty");
            }
            return content;
        }

        // 处理工具调用，构建继续对话的 messages
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是软件工程就业数据整理助手，只整理互联网/软件研发类岗位，必须输出可解析 JSON。"));
        messages.add(Map.of("role", "user", "content", PROMPT));

        // 添加之前的助手消息和工具调用结果
        messages.add(Map.of(
                "role", "assistant",
                "tool_calls", toolCalls
        ));

        // 处理每个工具调用
        for (JsonNode toolCall : toolCalls) {
            String toolName = toolCall.path("function").path("name").asText();
            String toolArgs = toolCall.path("function").path("arguments").asText();

            if ("search".equals(toolName)) {
                JsonNode args = objectMapper.readTree(toolArgs);
                String query = args.path("query").asText();
                String searchResult = executeSearch(query);

                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCall.path("id").asText(),
                        "content", searchResult
                ));
            }
        }

        // 继续对话获取最终结果
        Map<String, Object> continueBody = new LinkedHashMap<>();
        continueBody.put("model", model);
        continueBody.put("stream", false);
        continueBody.put("messages", messages);

        JsonNode continueResponse = restTemplate.postForObject(
                baseUrl + "/chat/completions",
                new HttpEntity<>(continueBody, headers),
                JsonNode.class
        );

        if (continueResponse == null) {
            throw new IllegalStateException("DeepSeek continue response is null");
        }

        String content = continueResponse.path("choices").path(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("DeepSeek continue content empty");
        }
        return content;
    }

    /**
     * 模拟搜索执行（实际需要调用真实搜索 API）
     * DeepSeek 联网搜索模型会自动处理搜索，此处仅作备用
     */
    private String executeSearch(String query) {
        // 在实际联网搜索模式下，DeepSeek 会自动调用搜索工具
        // 此方法仅在需要手动处理搜索时使用
        return "搜索: " + query;
    }
}
