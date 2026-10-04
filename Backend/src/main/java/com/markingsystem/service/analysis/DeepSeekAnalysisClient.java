package com.markingsystem.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.StudentAnalysisResultDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeepSeekAnalysisClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DeepSeekAnalysisClient(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String baseUrl, String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public StudentAnalysisResultDTO analyze(StudentAnalysisGraphState state) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "你是高校学生发展分析助手。你只能在“考研”“考公”“竞赛型人才”三类中做判断，并且必须输出严格 JSON。同时需要根据学生数据评估八个素质维度（思想品德、学业成绩、科研创新、实践服务、文体素养、技能素养、综合素质、发展潜力），每个维度给出 0 到 100 的整数比例值。"
                ),
                Map.of(
                        "role", "user",
                        "content", buildPrompt(state)
                )
        ));

        JsonNode response = restTemplate.postForObject(
                baseUrl + "/chat/completions",
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
        if (response == null) {
            throw new IllegalStateException("deepseek empty response");
        }

        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        String content = contentNode.asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("deepseek content empty");
        }

        String normalized = unwrapJsonBlock(content);
        JsonNode jsonNode = objectMapper.readTree(normalized);

        StudentAnalysisResultDTO result = new StudentAnalysisResultDTO();
        result.setPrimaryType(jsonNode.path("primary_type").asText("考研"));
        result.setSecondaryTypes(objectMapper.convertValue(jsonNode.path("secondary_types"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        result.setConfidence(jsonNode.path("confidence").asInt(75));
        result.setSummary(jsonNode.path("summary").asText(""));
        result.setReasons(objectMapper.convertValue(jsonNode.path("reasons"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        result.setSuggestions(objectMapper.convertValue(jsonNode.path("suggestions"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        result.setRadarDimensions(parseRadarDimensions(jsonNode.path("radar_dimensions")));
        result.setFeatureSnapshot(state.getFeatureSnapshot());
        result.setSource("java-graph+deepseek");
        result.setModel(model);
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }

    private String buildPrompt(StudentAnalysisGraphState state) throws Exception {
        return """
                请根据下面的学生画像做判断。

                约束：
                1. primary_type 只能是：考研、考公、竞赛型人才。
                2. secondary_types 最多两个，且不能包含 primary_type。
                3. confidence 取 0 到 100 的整数。
                4. reasons 至少输出 5 条，每条都要结合学生真实数据展开（如具体课程名与分数、优秀课程数量、竞赛/科研/实践活动的数量与类型、德育智育体育均分、各学期成绩变化趋势等），不要笼统概括；suggestions 输出 3 条，简洁可执行。
                5. 不要编造经历，只能根据输入内容判断；若某类数据缺失或为 0，用自然中文表述（如“智育数据缺失”“无竞赛记录”），禁止在 reasons/suggestions 文本中出现任何英文字段名或变量名（如 intellectualAverage、competitionCount、researchCount、approvedExtraScore、moralAverage、courseAverage、excellentCourseCount、scoreCount 等）。
                6. 仅输出 JSON，不要输出 Markdown。
                7. radar_dimensions 必须包含且仅包含这八个字段：思想品德、学业成绩、科研创新、实践服务、文体素养、技能素养、综合素质、发展潜力。
                8. 每个维度的值为 0 到 100 的整数，代表该学生在该维度的表现比例。
                9. 综合素质应反映前六个维度的整体水平，发展潜力应体现学生未来成长空间。
                10.若推荐参加竞赛，给出竞赛的具体名。

                输出 JSON 结构：
                {
                  "primary_type": "考研",
                  "secondary_types": ["竞赛型人才", "考公"],
                  "confidence": 82,
                  "summary": "一句话总结",
                  "reasons": [
                    "课程总体平均分较高，其中《数据结构》《高等数学》等课程达到优秀，学业基础扎实，符合考研方向。",
                    "智育均分与课程均分匹配度高，理论类科目得分优于实践类，体现偏理论研究型特质。",
                    "未检索到‘挑战杯’‘互联网+’等竞赛记录，也无论文或导师课题，目前不具备竞赛型人才的明显特质。",
                    "德育均分稳定，已通过志愿服务或班委等附加分若干条，实践服务维度表现中等。",
                    "高分科目集中在形势与政策、计算机网络等偏理论与记忆型课程，与考研理论型硕士培养方向契合。"
                  ],
                  "suggestions": ["建议1", "建议2", "建议3"],
                  "radar_dimensions": {
                    "思想品德": 85,
                    "学业成绩": 90,
                    "科研创新": 60,
                    "实践服务": 70,
                    "文体素养": 75,
                    "技能素养": 65,
                    "综合素质": 80,
                    "发展潜力": 85
                  }
                }

                学生原始画像：
                %s

                提炼后的特征：
                %s
                """.formatted(
                objectMapper.writeValueAsString(state.getPayload()),
                objectMapper.writeValueAsString(state.getFeatureSnapshot())
        );
    }

    private Map<String, Double> parseRadarDimensions(JsonNode node) {
        Map<String, Double> dimensions = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return dimensions;
        }
        List<String> keys = List.of(
                "思想品德", "学业成绩", "科研创新", "实践服务",
                "文体素养", "技能素养", "综合素质", "发展潜力"
        );
        for (String key : keys) {
            double value = node.path(key).asDouble(0);
            value = Math.max(0, Math.min(100, value));
            dimensions.put(key, Math.round(value * 10D) / 10D);
        }
        return dimensions;
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
}
