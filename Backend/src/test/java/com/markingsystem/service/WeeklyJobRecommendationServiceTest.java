package com.markingsystem.service;

import com.markingsystem.dto.WeeklyJobRecommendationDTO;
import com.markingsystem.entity.WeeklyJobRecommendation;
import com.markingsystem.repository.WeeklyJobRecommendationRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyJobRecommendationServiceTest {

    @Test
    void refreshForWeekSavesFiveRecommendationsWithStableIdsAndWeekRange() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                new WeeklyJobRecommendationClient() {
                    @Override
                    public List<GeneratedJobRecommendation> generateRecommendations() {
                        return List.of(
                                item("Java 后端开发工程师", "12k-18k", "Java, Spring Boot, MySQL", "https://jobs.example.com/java"),
                                item("前端开发工程师", "10k-16k", "React, Vite, TypeScript", "https://jobs.example.com/frontend"),
                                item("软件测试工程师", "8k-13k", "自动化测试, Python, Selenium", "https://jobs.example.com/test"),
                                item("算法工程师", "18k-28k", "Python, 机器学习, PyTorch", "https://jobs.example.com/algorithm"),
                                item("DevOps 工程师", "14k-22k", "Linux, Docker, Kubernetes", "https://jobs.example.com/devops")
                        );
                    }
                    @Override public String source() { return "deepseek-weekly-job"; }
                    @Override public String modelName() { return "deepseek-chat"; }
                }
        );

        List<WeeklyJobRecommendationDTO> result = service.refreshForWeek(LocalDate.of(2026, 7, 4));

        assertEquals(5, result.size());
        assertEquals(LocalDate.of(2026, 6, 29), result.get(0).getWeekStartDate());
        assertEquals(LocalDate.of(2026, 7, 5), result.get(0).getWeekEndDate());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Java 后端开发工程师", result.get(0).getJobTitle());
        assertEquals("12k-18k", result.get(0).getSalary());
        assertEquals("Java, Spring Boot, MySQL", result.get(0).getSkills());
        assertTrue(result.get(0).getRecruitmentLink().startsWith("https://www.zhipin.com/web/geek/job?query="));
        assertEquals("deepseek-weekly-job", result.get(0).getSource());
        assertNotNull(result.get(0).getGeneratedAt());
    }

    @Test
    void listLatestReturnsSavedLatestWeekWithoutCallingClientAgain() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendation saved = new WeeklyJobRecommendation();
        saved.setId(7L);
        saved.setWeekStartDate(LocalDate.of(2026, 6, 29));
        saved.setWeekEndDate(LocalDate.of(2026, 7, 5));
        saved.setSortOrder(1);
        saved.setJobTitle("全栈开发工程师");
        saved.setSalary("13k-20k");
        saved.setSkills("React, Spring Boot, MySQL");
        saved.setRecruitmentLink("https://jobs.example.com/fullstack");
        repository.rows.add(saved);

        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> {
                    throw new AssertionError("client should not be called when latest data exists");
                }
        );

        List<WeeklyJobRecommendationDTO> result = service.listLatest();

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        assertEquals("全栈开发工程师", result.get(0).getJobTitle());
    }

    @Test
    void listLatestOrRefreshRefreshesWhenLatestWeekIsNotCurrentWeek() {
        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendation stale = new WeeklyJobRecommendation();
        stale.setId(7L);
        stale.setWeekStartDate(currentWeekStart.minusWeeks(1));
        stale.setWeekEndDate(currentWeekStart.minusDays(1));
        stale.setSortOrder(1);
        stale.setJobTitle("上一周全栈开发工程师");
        stale.setSalary("13k-20k");
        stale.setSkills("React, Spring Boot, MySQL");
        stale.setRecruitmentLink("https://jobs.example.com/fullstack");
        repository.rows.add(stale);

        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> List.of(
                        item("Java 后端开发工程师", "12k-18k", "Java, Spring Boot, MySQL", "https://jobs.example.com/java")
                )
        );

        List<WeeklyJobRecommendationDTO> result = service.listLatestOrRefresh();

        assertEquals(1, result.size());
        assertEquals(currentWeekStart, result.get(0).getWeekStartDate());
        assertEquals("Java 后端开发工程师", result.get(0).getJobTitle());
    }

    @Test
    void listLatestOrRefreshKeepsStaleDataWhenAutoRefreshFails() {
        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendation stale = new WeeklyJobRecommendation();
        stale.setId(9L);
        stale.setWeekStartDate(currentWeekStart.minusWeeks(1));
        stale.setWeekEndDate(currentWeekStart.minusDays(1));
        stale.setSortOrder(1);
        stale.setJobTitle("上一周全栈开发工程师");
        stale.setSalary("13k-20k");
        stale.setSkills("React, Spring Boot, MySQL");
        stale.setRecruitmentLink("https://jobs.example.com/fullstack");
        repository.rows.add(stale);

        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> {
                    throw new IllegalStateException("DeepSeek API Key is not configured");
                }
        );

        List<WeeklyJobRecommendationDTO> result = service.listLatestOrRefresh();

        assertEquals(1, result.size());
        assertEquals(9L, result.get(0).getId());
        assertEquals("上一周全栈开发工程师", result.get(0).getJobTitle());
    }

    @Test
    void listLatestOrRefreshKeepsCurrentWeekWithoutCallingClientAgain() {
        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendation saved = new WeeklyJobRecommendation();
        saved.setId(7L);
        saved.setWeekStartDate(currentWeekStart);
        saved.setWeekEndDate(currentWeekStart.plusDays(6));
        saved.setSortOrder(1);
        saved.setJobTitle("当前周全栈开发工程师");
        saved.setSalary("13k-20k");
        saved.setSkills("React, Spring Boot, MySQL");
        saved.setRecruitmentLink("https://jobs.example.com/fullstack");
        repository.rows.add(saved);

        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> {
                    throw new AssertionError("client should not be called when current week data exists");
                }
        );

        List<WeeklyJobRecommendationDTO> result = service.listLatestOrRefresh();

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        assertEquals("当前周全栈开发工程师", result.get(0).getJobTitle());
    }

    @Test
    void refreshForWeekFallsBackWhenOnlyPlaceholderContent() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> List.of(
                        item("无法提供精确排名数据，因无法实时访问招聘平台，需人工检索", "暂无", "暂无", "https://jobs.example.com/placeholder"),
                        item("无法提供精确排名数据，因无法实时访问招聘平台，需人工检索", "暂无", "暂无", "https://jobs.example.com/placeholder")
                )
        );

        List<WeeklyJobRecommendationDTO> result = service.refreshForWeek(LocalDate.of(2026, 9, 1));

        assertEquals(5, result.size());
        assertEquals("Java 后端开发工程师（Spring Cloud）", result.get(0).getJobTitle());
        assertEquals("示例数据", result.get(0).getSource());
        assertEquals("system-fallback", result.get(0).getModelName());
        assertTrue(repository.rows.stream().noneMatch(item -> item.getJobTitle().contains("无法提供")), "占位内容不应落库");
    }

    @Test
    void refreshForWeekSkipsPlaceholderItemsAndKeepsValidOnes() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> List.of(
                        item("无法提供精确排名数据，因无法实时访问招聘平台，需人工检索", "暂无", "暂无", "https://jobs.example.com/placeholder"),
                        item("Java 后端开发工程师", "12k-18k", "Java, Spring Boot, MySQL", "https://jobs.example.com/java"),
                        item("无法实时访问招聘平台", "薪资面议", "见招聘详情", "https://jobs.example.com/placeholder2")
                )
        );

        List<WeeklyJobRecommendationDTO> result = service.refreshForWeek(LocalDate.of(2026, 9, 1));

        assertEquals(1, result.size());
        assertEquals("Java 后端开发工程师", result.get(0).getJobTitle());
    }

    @Test
    void listLatestFiltersOutPersistedPlaceholderRows() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendation bad = new WeeklyJobRecommendation();
        bad.setId(7L);
        bad.setWeekStartDate(LocalDate.of(2026, 8, 31));
        bad.setWeekEndDate(LocalDate.of(2026, 9, 6));
        bad.setSortOrder(1);
        bad.setJobTitle("无法提供精确排名数据，因无法实时访问招聘平台，需人工检索");
        bad.setSalary("暂无");
        bad.setSkills("暂无");
        bad.setRecruitmentLink("https://jobs.example.com/placeholder");
        repository.rows.add(bad);

        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                () -> {
                    throw new AssertionError("client should not be called");
                }
        );

        List<WeeklyJobRecommendationDTO> result = service.listLatest();

        assertTrue(result.isEmpty(), "已持久化的占位数据不应展示");
    }

    private WeeklyJobRecommendationClient.GeneratedJobRecommendation item(
            String jobTitle,
            String salary,
            String skills,
            String recruitmentLink
    ) {
        return new WeeklyJobRecommendationClient.GeneratedJobRecommendation(
                jobTitle,
                salary,
                skills,
                recruitmentLink
        );
    }

    private static class FakeWeeklyJobRecommendationRepository {
        private final List<WeeklyJobRecommendation> rows = new ArrayList<>();
        private long nextId = 1L;

        WeeklyJobRecommendationRepository proxy() {
            return (WeeklyJobRecommendationRepository) Proxy.newProxyInstance(
                    WeeklyJobRecommendationRepository.class.getClassLoader(),
                    new Class<?>[]{WeeklyJobRecommendationRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findLatestWeekStartDate" -> rows.stream()
                                .map(WeeklyJobRecommendation::getWeekStartDate)
                                .max(Comparator.naturalOrder());
                        case "findByWeekStartDateOrderBySortOrderAsc" -> rows.stream()
                                .filter(item -> args[0].equals(item.getWeekStartDate()))
                                .sorted(Comparator.comparing(WeeklyJobRecommendation::getSortOrder))
                                .toList();
                        case "deleteByWeekStartDate" -> {
                            rows.removeIf(item -> args[0].equals(item.getWeekStartDate()));
                            yield null;
                        }
                        case "saveAll" -> {
                            @SuppressWarnings("unchecked")
                            Iterable<WeeklyJobRecommendation> incoming = (Iterable<WeeklyJobRecommendation>) args[0];
                            List<WeeklyJobRecommendation> saved = new ArrayList<>();
                            for (WeeklyJobRecommendation item : incoming) {
                                if (item.getId() == null) {
                                    item.setId(nextId++);
                                }
                                rows.add(item);
                                saved.add(item);
                            }
                            yield saved;
                        }
                        case "findById" -> Optional.empty();
                        case "toString" -> "FakeWeeklyJobRecommendationRepository";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
