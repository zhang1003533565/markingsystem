package com.markingsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过 MCP-Jobs HTTP 桥接服务获取真实招聘数据的实现。
 * 桥接服务封装了 mcp-jobs（基于 Playwright 无头浏览器）的 MCP stdio 接口。
 */
public class McpJobsWeeklyJobRecommendationClient implements WeeklyJobRecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(McpJobsWeeklyJobRecommendationClient.class);
    private static final String BOSS_SEARCH_URL = "https://www.zhipin.com/web/geek/job?query=";

    /** 用于每周热门岗位推荐的搜索关键词列表 */
    private static final List<String> SEARCH_KEYWORDS = List.of(
            "Java后端开发", "前端开发工程师", "测试开发工程师", "DevOps工程师", "全栈工程师"
    );

    /** 每个关键词最多保留的结果数，避免单一关键词占满最终推荐 */
    private static final int MAX_PER_KEYWORD = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String bridgeUrl;

    public McpJobsWeeklyJobRecommendationClient(
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${job-recommendation.mcp-jobs.bridge-url:http://localhost:3100}") String bridgeUrl,
            @org.springframework.beans.factory.annotation.Value("${job-recommendation.mcp-jobs.connect-timeout-ms:5000}") int connectTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${job-recommendation.mcp-jobs.read-timeout-ms:120000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = objectMapper;
        this.bridgeUrl = bridgeUrl;
    }

    @Override
    public String source() {
        return "mcp-jobs";
    }

    @Override
    public String modelName() {
        return "mcp-jobs-1.4.0";
    }

    @Override
    public List<GeneratedJobRecommendation> generateRecommendations() throws Exception {
        List<GeneratedJobRecommendation> allJobs = new ArrayList<>();

        for (String keyword : SEARCH_KEYWORDS) {
            try {
                List<GeneratedJobRecommendation> jobs = searchByKeyword(keyword);
                allJobs.addAll(jobs);
                log.info("mcp-jobs 搜索 '{}' 返回 {} 条结果", keyword, jobs.size());
            } catch (Exception exception) {
                log.warn("mcp-jobs 搜索 '{}' 失败: {}", keyword, exception.getMessage());
            }
        }

        if (allJobs.isEmpty()) {
            throw new IllegalStateException("mcp-jobs 未返回任何有效岗位数据");
        }

        // 去重（按岗位名称 + 薪资组合，避免相同岗位不同薪资被误删）并截取前 5 条
        Map<String, GeneratedJobRecommendation> uniqueJobs = new LinkedHashMap<>();
        for (GeneratedJobRecommendation job : allJobs) {
            String dedupeKey = job.jobTitle() + "|" + job.salary();
            uniqueJobs.putIfAbsent(dedupeKey, job);
        }

        List<GeneratedJobRecommendation> result = new ArrayList<>(uniqueJobs.values());
        if (result.size() > 5) {
            result = result.subList(0, 5);
        }
        return result;
    }

    private List<GeneratedJobRecommendation> searchByKeyword(String keyword) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyword", keyword);

        JsonNode response;
        try {
            response = restTemplate.postForObject(
                    bridgeUrl + "/api/search",
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法连接 MCP-Jobs 桥接服务 (" + bridgeUrl + ")：" + exception.getMessage(), exception);
        }

        if (response == null) {
            throw new IllegalStateException("MCP-Jobs 桥接服务返回空响应");
        }

        // 检查是否有错误
        JsonNode metadata = response.path("metadata");
        if (metadata.has("error") && !metadata.path("error").isNull()) {
            String error = metadata.path("error").asText();
            if (!error.isBlank()) {
                log.warn("mcp-jobs 返回错误: {}", error);
            }
        }

        JsonNode jobs = response.path("jobs");
        if (!jobs.isArray()) {
            return List.of();
        }

        List<GeneratedJobRecommendation> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            String title = job.path("title").asText("").trim();

            // 退化数据处理：当爬虫选择器匹配失败时，岗位仅含 content 字段（原始拼接文本）
            // 使用正则从 content 中提取结构化信息
            if (title.isBlank() && job.has("content")) {
                GeneratedJobRecommendation parsed = parseContentToJob(job.path("content").asText(""), keyword);
                if (parsed != null) {
                    result.add(parsed);
                }
                continue;
            }

            if (title.isBlank()) {
                continue;
            }

            String salary = job.path("salary").asText("").trim();
            if (salary.isBlank()) {
                salary = "薪资面议";
            }

            // tags 为数组，需 join 为逗号分隔字符串
            JsonNode tagsNode = job.path("tags");
            String skills;
            if (tagsNode.isArray()) {
                List<String> tags = new ArrayList<>();
                for (JsonNode tag : tagsNode) {
                    String tagText = tag.asText("").trim();
                    if (!tagText.isBlank()) {
                        tags.add(tagText);
                    }
                }
                skills = String.join(", ", tags);
            } else {
                skills = tagsNode.asText("").trim();
            }
            if (skills.isBlank()) {
                skills = "见招聘详情";
            }

            // jobDetail 为招聘详情页链接
            String link = job.path("jobDetail").asText("").trim();
            if (link.isBlank()) {
                link = BOSS_SEARCH_URL + URLEncoder.encode(title, StandardCharsets.UTF_8);
            }

            result.add(new GeneratedJobRecommendation(title, salary, skills, link));
        }
        // 每个关键词最多保留 MAX_PER_KEYWORD 条，避免单一关键词占满最终推荐
        if (result.size() > MAX_PER_KEYWORD) {
            result = result.subList(0, MAX_PER_KEYWORD);
        }
        return result;
    }

    /**
     * 从退化的 content 文本中提取结构化岗位信息。
     * content 是招聘卡片所有文本的拼接合，包含岗位名、薪资、地区、经验、公司名等。
     * 提取策略：
     * - 薪资：正则匹配 \d+-\d+[kK] 模式
     * - 岗位名：取薪资模式之前的文本，截断过长内容
     * - 技能：无法可靠提取，使用默认值
     * - 链接：用岗位名生成 BOSS 直聘搜索链接
     */
    private GeneratedJobRecommendation parseContentToJob(String content, String fallbackTitle) {
        if (content == null || content.isBlank()) {
            return null;
        }

        // 提取薪资
        String salary = "薪资面议";
        Matcher salaryMatcher = SALARY_PATTERN.matcher(content);
        if (salaryMatcher.find()) {
            salary = salaryMatcher.group();
        }

        // 提取岗位名：取薪资模式之前的文本
        String title = fallbackTitle != null ? fallbackTitle.trim() : "";
        if (title.isBlank()) {
            // 尝试从 content 开头提取到第一个薪资/数字模式之前
            Matcher titleMatcher = TITLE_EXTRACT_PATTERN.matcher(content);
            if (titleMatcher.find()) {
                title = titleMatcher.group(1).trim();
            }
        }
        if (title.isBlank()) {
            // 取前 30 个字符作为标题
            title = content.length() > 30 ? content.substring(0, 30).trim() : content.trim();
        }
        // 截断过长的标题
        if (title.length() > 40) {
            title = title.substring(0, 40).trim();
        }

        String link = BOSS_SEARCH_URL + URLEncoder.encode(title, StandardCharsets.UTF_8);
        return new GeneratedJobRecommendation(title, salary, "见招聘详情", link);
    }

    /** 匹配薪资范围，如 15-20k、8-14K */
    private static final Pattern SALARY_PATTERN = Pattern.compile("\\d+-\\d+[kK]");
    /** 从 content 开头提取到第一个数字-数字模式之前（即岗位名区域） */
    private static final Pattern TITLE_EXTRACT_PATTERN = Pattern.compile("^(.+?)\\s*(?=\\d+-\\d+[kK年人])");
}
