package com.markingsystem.service;

import com.markingsystem.dto.WeeklyJobRecommendationDTO;
import com.markingsystem.entity.WeeklyJobRecommendation;
import com.markingsystem.repository.WeeklyJobRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WeeklyJobRecommendationService {

    private static final int MAX_RECOMMENDATION_COUNT = 5;
    private static final String BOSS_JOB_SEARCH_URL = "https://www.zhipin.com/web/geek/job?query=";
    private static final long MANUAL_REFRESH_COOLDOWN_MILLIS = 10 * 60 * 1000L;
    private static final String FALLBACK_SOURCE = "示例数据";
    private static final String FALLBACK_MODEL_NAME = "system-fallback";

    private static final List<WeeklyJobRecommendationClient.GeneratedJobRecommendation> FALLBACK_RECOMMENDATIONS = List.of(
            new WeeklyJobRecommendationClient.GeneratedJobRecommendation(
                    "Java 后端开发工程师（Spring Cloud）",
                    "15k-30k",
                    "Java, Spring Boot, MySQL, Redis, Kafka",
                    ""
            ),
            new WeeklyJobRecommendationClient.GeneratedJobRecommendation(
                    "前端开发工程师（React/Vue）",
                    "14k-28k",
                    "JavaScript, TypeScript, React, Vue, Webpack",
                    ""
            ),
            new WeeklyJobRecommendationClient.GeneratedJobRecommendation(
                    "测试开发工程师（自动化测试）",
                    "12k-25k",
                    "Java/Python, Selenium, Pytest, CI/CD, 接口测试",
                    ""
            ),
            new WeeklyJobRecommendationClient.GeneratedJobRecommendation(
                    "DevOps 工程师（云原生）",
                    "18k-35k",
                    "Kubernetes, Docker, Linux, CI/CD, Go",
                    ""
            ),
            new WeeklyJobRecommendationClient.GeneratedJobRecommendation(
                    "全栈开发工程师（React+Node.js）",
                    "18k-35k",
                    "React, Node.js, TypeScript, MongoDB, AWS",
                    ""
            )
    );

    /**
     * AI 无法实时访问招聘平台时可能返回的占位/说明性文本。
     * 命中任一短语即视为无效输出：不落库、不展示，避免页面出现
     * “无法提供精确排名数据，需人工检索”“暂无”等占位卡片。
     */
    private static final List<String> PLACEHOLDER_PHRASES = List.of(
            "无法提供精确排名数据",
            "无法实时访问",
            "无法访问招聘平台",
            "无法提供",
            "不能提供",
            "无法获取",
            "暂时无法",
            "需人工检索",
            "人工检索",
            "暂无"
    );

    private final WeeklyJobRecommendationRepository repository;
    private final WeeklyJobRecommendationClient client;
    private final AtomicLong lastManualRefreshAt = new AtomicLong(0L);

    public WeeklyJobRecommendationService(
            WeeklyJobRecommendationRepository repository,
            WeeklyJobRecommendationClient client) {
        this.repository = repository;
        this.client = client;
    }

    public List<WeeklyJobRecommendationDTO> listLatest() {
        return repository.findLatestWeekStartDate()
                .map(repository::findByWeekStartDateOrderBySortOrderAsc)
                .map(this::toDtoList)
                .orElseGet(List::of);
    }

    @Transactional
    public List<WeeklyJobRecommendationDTO> listLatestOrRefresh() {
        List<WeeklyJobRecommendationDTO> latest = listLatest();
        if (!latest.isEmpty() && isCurrentWeek(latest.get(0).getWeekStartDate())) {
            return latest;
        }
        if (latest.isEmpty()) {
            return refreshForWeek(LocalDate.now());
        }
        try {
            return refreshForWeek(LocalDate.now());
        } catch (RuntimeException exception) {
            // 自动刷新失败时保留最近一周数据，避免页面直接空白；仍可手动触发刷新重试
            return latest;
        }
    }

    @Transactional
    public List<WeeklyJobRecommendationDTO> refreshCurrentWeek() {
        long now = System.currentTimeMillis();
        long previous = lastManualRefreshAt.get();
        if (previous > 0 && now - previous < MANUAL_REFRESH_COOLDOWN_MILLIS) {
            throw new IllegalStateException("岗位推荐刷新过于频繁，请稍后再试");
        }
        if (!lastManualRefreshAt.compareAndSet(previous, now)) {
            throw new IllegalStateException("岗位推荐正在刷新，请稍后再试");
        }
        return refreshForWeek(LocalDate.now());
    }

    /**
     * 清理超过指定周数的旧岗位推荐数据。
     * 在刷新本周数据后调用，确保只保留最近 N 周的数据。
     *
     * @param keepWeeks 保留最近的 N 周数据
     */
    @Transactional
    public void cleanupOldData(int keepWeeks) {
        LocalDate cutoffDate = resolveWeekStart(LocalDate.now()).minusWeeks(keepWeeks);
        repository.deleteByWeekStartDateBefore(cutoffDate);
    }

    @Transactional
    public List<WeeklyJobRecommendationDTO> refreshForWeek(LocalDate date) {
        LocalDate weekStart = resolveWeekStart(date == null ? LocalDate.now() : date);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<WeeklyJobRecommendationClient.GeneratedJobRecommendation> generated;
        try {
            generated = client.generateRecommendations();
        } catch (Exception exception) {
            throw new IllegalStateException("生成每周热门岗位推荐失败：" + exception.getMessage(), exception);
        }

        List<WeeklyJobRecommendation> rows = toEntities(
                generated,
                weekStart,
                weekEnd,
                client.source(),
                client.modelName()
        );
        if (rows.isEmpty()) {
            rows = toEntities(FALLBACK_RECOMMENDATIONS, weekStart, weekEnd, FALLBACK_SOURCE, FALLBACK_MODEL_NAME);
        }

        repository.deleteByWeekStartDate(weekStart);
        return toDtoList(repository.saveAll(rows));
    }

    private LocalDate resolveWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean isCurrentWeek(LocalDate weekStart) {
        return weekStart != null && weekStart.equals(resolveWeekStart(LocalDate.now()));
    }

    private List<WeeklyJobRecommendation> toEntities(
            List<WeeklyJobRecommendationClient.GeneratedJobRecommendation> generated,
            LocalDate weekStart,
            LocalDate weekEnd,
            String source,
            String modelName) {
        if (generated == null || generated.isEmpty()) {
            return List.of();
        }

        LocalDateTime generatedAt = LocalDateTime.now();
        List<WeeklyJobRecommendation> rows = new ArrayList<>();
        for (WeeklyJobRecommendationClient.GeneratedJobRecommendation item : generated) {
            if (rows.size() >= MAX_RECOMMENDATION_COUNT) {
                break;
            }
            if (item == null
                    || isBlank(item.jobTitle())
                    || isBlank(item.salary())
                    || isBlank(item.skills())
                    || isPlaceholderContent(item.jobTitle(), item.salary(), item.skills())) {
                continue;
            }
            String jobTitle = item.jobTitle().trim();
            WeeklyJobRecommendation row = new WeeklyJobRecommendation();
            row.setWeekStartDate(weekStart);
            row.setWeekEndDate(weekEnd);
            row.setSortOrder(rows.size() + 1);
            row.setJobTitle(jobTitle);
            row.setSalary(item.salary().trim());
            row.setSkills(item.skills().trim());
            row.setRecruitmentLink(buildRecruitmentSearchLink(jobTitle));
            row.setSource(source);
            row.setModelName(modelName);
            row.setGeneratedAt(generatedAt);
            rows.add(row);
        }
        return rows;
    }

    private List<WeeklyJobRecommendationDTO> toDtoList(Iterable<WeeklyJobRecommendation> rows) {
        if (rows == null) {
            return List.of();
        }
        List<WeeklyJobRecommendationDTO> result = new ArrayList<>();
        for (WeeklyJobRecommendation row : rows) {
            // 过滤已持久化的占位数据（例如 AI 无法访问招聘平台时写入的说明文本），保证页面不展示
            if (row != null && !isPlaceholderContent(row.getJobTitle(), row.getSalary(), row.getSkills())) {
                result.add(toDto(row));
            }
        }
        return result;
    }

    private WeeklyJobRecommendationDTO toDto(WeeklyJobRecommendation row) {
        WeeklyJobRecommendationDTO dto = new WeeklyJobRecommendationDTO();
        dto.setId(row.getId());
        dto.setWeekStartDate(row.getWeekStartDate());
        dto.setWeekEndDate(row.getWeekEndDate());
        dto.setSortOrder(row.getSortOrder());
        dto.setJobTitle(row.getJobTitle());
        dto.setSalary(row.getSalary());
        dto.setSkills(row.getSkills());
        dto.setRecruitmentLink(buildRecruitmentSearchLink(row.getJobTitle()));
        dto.setSource(row.getSource());
        dto.setModelName(row.getModelName());
        dto.setGeneratedAt(row.getGeneratedAt());
        return dto;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断岗位内容是否为 AI 返回的占位/说明性文本。
     * 将岗位名称、薪资、技能拼接后匹配占位短语，命中即视为无效。
     */
    private boolean isPlaceholderContent(String jobTitle, String salary, String skills) {
        String combined = String.join(" ",
                jobTitle == null ? "" : jobTitle,
                salary == null ? "" : salary,
                skills == null ? "" : skills);
        for (String phrase : PLACEHOLDER_PHRASES) {
            if (combined.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String buildRecruitmentSearchLink(String jobTitle) {
        String keyword = isBlank(jobTitle) ? "软件工程师" : jobTitle.trim();
        return BOSS_JOB_SEARCH_URL + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }
}
