package com.markingsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 岗位学习任务卡生成客户端（以岗定教）。
 * <p>
 * 使用 deepseek-reasoner 模型 + search_channel 联网搜索，
 * 将企业真实工作任务转化为课堂实训学习型任务，输出结构化学习任务卡：
 * 任务名称、工作情境、分步操作指南、安全要点、对应知识点与技能点、
 * 校内实训资源（微课/仿真软件链接）与个性化学习路径。
 *
 * 安全策略（与 DeepSeekJobLearningResourceClient 保持一致）：
 * - 入参经 sanitize 清洗后注入 prompt，避免控制字符注入与超长负载
 * - 不使用 String.formatted，避免 % 触发 IllegalFormatException
 * - 抽取 JSON 对象时按 { 与 } 边界定位，对 Markdown 围栏与额外解释文本更鲁棒
 * - 字段按 DB 列长截断，任务名称与操作步骤缺失时判定生成失败
 */
public class DeepSeekJobTaskCardClient implements JobTaskCardClient {

    /** 岗位名称与输出字段的最大长度（与 DB 字段长度对齐，防止 AI 异常输出占用过多存储） */
    private static final int MAX_JOB_TITLE_LEN = 120;
    private static final int MAX_PROFILE_LEN = 300;
    private static final int MAX_TASK_NAME_LEN = 200;
    private static final int MAX_FIELD_LEN = 4000;

    private static final String PROMPT_TEMPLATE = """
        你是面向软件工程专业的"以岗定教"智能体。请围绕目标软件工程岗位「__JOB_TITLE__」，
        把该岗位的典型研发工作任务转化为可用于课堂实训的学习型任务，生成一张学习任务卡。
        学生情况：__STUDENT_PROFILE__
        请结合学生专业、年级与学情，让任务难度和个性化学习路径贴合该学生。
        注意：若学生情况中带有"已掌握学习路径步骤"，表示该学生已完成上一轮的学习路径，请跳过已掌握的内容，生成衔接进阶、不重复的下一步路径。
        内容导向：任务、情境、步骤、知识点、技能点与学习路径必须围绕软件研发（编码、调试、测试、部署、协作），
        不得写成硬件拆装、产线操作、汽车维修、电池电控等非软件工程实训。
        只返回一个 JSON 对象（不要 Markdown，不要额外解释），字段如下：
        1. taskName：学习型任务名称，20 字以内，突出"任务"导向（如"订单服务接口联调实训"）。
        2. workScenario：工作情境，80-150 字，描述互联网/软件团队真实研发场景与任务背景。
        3. steps：分步操作指南，5-8 步，每步一行，用"1. xxx"编号，必须可在电脑上执行（读需求、写代码、跑测试、提 PR 等）。
        4. safetyPoints：规范与质量要点，2-4 条，每行一条（如密钥不入库、SQL 注入防护、Code Review 清单等）。
        5. knowledgePoints：对应知识点，2-4 条，每行一条（语言/框架/数据结构/工程化等）。
        6. skillPoints：对应技能点，2-4 条，每行一条（编码、调试、测试、部署等）。
        7. campusResources：校内/公开实训资源，2-4 条，每行一条，格式"资源名称（类型：微课/仿真软件/实训平台）｜获取方式或链接"。必须真实可操作，如中国大学MOOC、实验楼、LeetCode、智慧职教等可检索到的软件类资源，不得编造。
        8. learningPath：个性化学习路径，3-5 步，每行一条，循序渐进（如"先看 Spring Boot REST 微课 → 完成 CRUD 接口练习 → 补齐单元测试与接口联调"）。若学生已掌握部分步骤，新路径应从下一步衔接。
        示例：
        {
          "taskName": "订单服务接口联调实训",
          "workScenario": "某电商团队的订单微服务需要对接支付回调……",
          "steps": "1. 阅读接口文档与错误码约定……\\n2. ……",
          "safetyPoints": "1. 回调验签密钥不得写入仓库……",
          "knowledgePoints": "1. RESTful 接口设计……",
          "skillPoints": "1. 使用 Postman/curl 完成联调……",
          "campusResources": "Spring Boot 微服务实战微课（微课）｜中国大学MOOC 搜索关键词：Spring Boot",
          "learningPath": "1. 先学习 REST 与异常处理微课……"
        }
        """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DeepSeekJobTaskCardClient(
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            int connectTimeoutMs,
            int readTimeoutMs) {
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
        return "deepseek";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public GeneratedTaskCard generateTaskCard(String jobTitle, String studentProfile) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key is not configured");
        }
        if (jobTitle == null || jobTitle.isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        // 防御：清除控制字符并限制长度，避免 prompt 注入与超大负载
        String safeJobTitle = sanitize(jobTitle, MAX_JOB_TITLE_LEN);
        if (safeJobTitle.isEmpty()) {
            throw new IllegalArgumentException("岗位名称无效");
        }
        String safeProfile = studentProfile == null || studentProfile.isBlank()
                ? "未提供，请给出通用建议"
                : sanitize(studentProfile, MAX_PROFILE_LEN);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "你是面向软件工程的以岗定教智能体，任务与路径须围绕软件研发，必须输出可解析 JSON。"),
                Map.of("role", "user", "content", buildPrompt(safeJobTitle, safeProfile))
        ));

        // 如果模型支持联网搜索，添加 search_channel 参数，保证校内实训资源真实可查
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

        JsonNode root;
        try {
            root = objectMapper.readTree(extractJsonObject(content));
        } catch (Exception parseEx) {
            throw new IllegalStateException("AI 返回的数据无法解析为 JSON", parseEx);
        }

        String taskName = field(root, "taskName", "任务名称");
        String workScenario = field(root, "workScenario", "工作情境");
        String steps = field(root, "steps", "分步操作指南", "操作步骤");
        String safetyPoints = field(root, "safetyPoints", "安全要点");
        String knowledgePoints = field(root, "knowledgePoints", "知识点");
        String skillPoints = field(root, "skillPoints", "技能点");
        String campusResources = field(root, "campusResources", "校内实训资源", "实训资源");
        String learningPath = field(root, "learningPath", "学习路径", "个性化学习路径");

        if (taskName.isBlank() || steps.isBlank()) {
            throw new IllegalStateException("AI 返回的任务卡缺少必要字段（taskName/steps）");
        }
        return new GeneratedTaskCard(
                taskName, workScenario, steps, safetyPoints,
                knowledgePoints, skillPoints, campusResources, learningPath
        );
    }

    private String buildPrompt(String jobTitle, String studentProfile) {
        // 使用占位符替换避免 String.formatted 对 % 字面量敏感
        return PROMPT_TEMPLATE
                .replace("__JOB_TITLE__", jobTitle)
                .replace("__STUDENT_PROFILE__", studentProfile);
    }

    /** 读取字段：优先英文键，其次中文键，按列长截断 */
    private String field(JsonNode root, String englishKey, String... chineseKeys) {
        JsonNode node = root.path(englishKey);
        if (node.isMissingNode() || node.isNull() || node.asText("").isBlank()) {
            for (String key : chineseKeys) {
                JsonNode alt = root.path(key);
                if (!alt.isMissingNode() && !alt.isNull() && !alt.asText("").isBlank()) {
                    node = alt;
                    break;
                }
            }
        }
        return truncate(node.isMissingNode() || node.isNull() ? "" : node.asText(""), MAX_FIELD_LEN);
    }

    /**
     * 清洗文本：去除控制字符（保留普通空白）、压缩连续换行、限制最大长度。
     */
    private String sanitize(String raw, int maxLen) {
        String cleaned = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        cleaned = cleaned.trim();
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen);
        }
        return cleaned;
    }

    /**
     * 提取 JSON 对象片段。
     * 先剥除 Markdown 代码围栏，再以第一个 { 与最后一个 } 定位，
     * 避免 AI 在对象外附带头尾说明时导致解析失败。
     */
    private String extractJsonObject(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > -1) {
                trimmed = trimmed.substring(firstLineEnd + 1).trim();
            }
        }
        if (trimmed.endsWith("```")) {
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence > -1) {
                trimmed = trimmed.substring(0, lastFence).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
