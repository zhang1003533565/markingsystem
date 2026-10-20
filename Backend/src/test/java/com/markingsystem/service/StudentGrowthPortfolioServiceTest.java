package com.markingsystem.service;

import com.markingsystem.entity.CompositeScoreSnapshot;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CollegeRepository;
import com.markingsystem.repository.CompositeScoreSnapshotRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.MoralEducationRepository;
import com.markingsystem.repository.SubjectRAINRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成长档案聚合逻辑单测：只验证读路径（分类、排序、过滤、统计口径），
 * 所有仓储均为 Proxy 只读 stub，任何未注册的写方法会直接抛异常。
 */
class StudentGrowthPortfolioServiceTest {

    @Test
    void timelineIncludesOnlyApprovedRecordsSortedByTimeWithClassifiedTypes() {
        List<Object[]> statusQueries = new ArrayList<>();
        StudentGrowthPortfolioService service = service(statusQueries);

        List<Map<String, Object>> events = service.buildTimeline(student());

        // 只按 approved 状态查询附加分记录
        assertEquals(1, statusQueries.size());
        assertEquals("20230001", statusQueries.get(0)[0]);
        assertEquals("approved", statusQueries.get(0)[1]);

        // 3 条附加分 + 2 个学期课程聚合 + 1 条二课（无二课分的记录被过滤）
        assertEquals(6, events.size());

        // 时间倒序：2026-03-01 的竞赛记录排最前
        Map<String, Object> first = events.get(0);
        assertEquals("app-101", first.get("id"));
        assertEquals("competition", first.get("type"));
        assertEquals("2026-03-01", first.get("date"));
        assertEquals("2025-2026", first.get("groupKey"));

        Map<String, Object> volunteer = findEvent(events, "app-102");
        assertEquals("volunteer", volunteer.get("type"));
        assertEquals("志愿活动", volunteer.get("typeLabel"));

        Map<String, Object> skill = findEvent(events, "app-103");
        assertEquals("skill", skill.get("type"));

        Map<String, Object> courseEvent = findEvent(events, "course-2024-2025-1");
        assertEquals("course", courseEvent.get("type"));
        assertEquals(2, ((List<?>) courseEvent.get("courses")).size());
        assertEquals(87.5, courseEvent.get("score"));
        // 输出前已剥离排序辅助字段
        assertFalse(courseEvent.containsKey("_sortTime"));

        Map<String, Object> secondClass = findEvent(events, "second-class-900");
        assertEquals("secondClass", secondClass.get("type"));
        assertEquals(6.0, secondClass.get("score"));
    }

    @Test
    void summaryCountsMatchClassificationAndSnapshotsAreYearAscending() {
        StudentGrowthPortfolioService service = service(new ArrayList<>());

        Map<String, Object> summary = service.buildSummary(student());
        Map<?, ?> stats = (Map<?, ?>) summary.get("stats");

        assertEquals(3, stats.get("courseCount"));
        assertEquals(2L, stats.get("semesterCount"));
        assertEquals(84.33, stats.get("courseAverage"));
        assertEquals(1L, stats.get("competitionCount"));
        assertEquals(1L, stats.get("volunteerCount"));
        assertEquals(1L, stats.get("skillCount"));
        assertEquals(3, stats.get("approvedApplicationCount"));
        assertEquals(6.0, stats.get("secondClassTotal"));

        List<?> snapshots = (List<?>) summary.get("yearlySnapshots");
        // 同一学年重复快照去重，输出按学年正序
        assertEquals(2, snapshots.size());
        Map<?, ?> earliest = (Map<?, ?>) snapshots.get(0);
        assertEquals("2024-2025", earliest.get("academicYear"));

        List<?> semesterAverages = (List<?>) summary.get("semesterCourseAverages");
        assertEquals(2, semesterAverages.size());
        Map<?, ?> firstSemester = (Map<?, ?>) semesterAverages.get(0);
        assertEquals("2024-2025-1", firstSemester.get("semester"));
        assertEquals(87.5, firstSemester.get("average"));
    }

    @Test
    void resumeMaterialsGroupItemsIntoResumeSections() {
        StudentGrowthPortfolioService service = service(new ArrayList<>());

        Map<String, Object> materials = service.buildResumeMaterials(student());
        Map<?, ?> sections = (Map<?, ?>) materials;

        List<?> education = (List<?>) sections.get("education");
        assertEquals(List.of("课程平均分 84.33（共 3 门）"), education);

        List<?> honors = (List<?>) sections.get("honors");
        assertEquals(1, honors.size());
        Map<?, ?> honor = (Map<?, ?>) honors.get(0);
        assertTrue(String.valueOf(honor.get("text")).contains("蓝桥杯软件设计竞赛"));
        assertTrue(String.valueOf(honor.get("text")).contains("省级一等奖"));

        List<?> practice = (List<?>) sections.get("practice");
        // 1 条志愿记录 + 1 条二课累计
        assertEquals(2, practice.size());
        Map<?, ?> secondClassItem = (Map<?, ?>) practice.get(practice.size() - 1);
        assertEquals("第二课堂学分累计 6 分", secondClassItem.get("text"));

        List<?> skills = (List<?>) sections.get("skills");
        assertEquals(1, skills.size());
    }

    private StudentGrowthPortfolioService service(List<Object[]> statusQueries) {
        return new StudentGrowthPortfolioService(
                proxy(SubjectScoreRAINRepository.class, Map.of("findByStudentId", courseScores())),
                proxy(SubjectRAINRepository.class, Map.of("findAll", subjects())),
                proxy(ExtraScoreApplicationRepository.class, Map.of(
                        "findByStudentNoAndStatus", (MethodHandler) args -> {
                            statusQueries.add(new Object[]{args[0], args[1]});
                            return approvedApplications();
                        }
                )),
                proxy(MoralEducationRepository.class, Map.of("findByStudentId", moralRecords())),
                proxy(CompositeScoreSnapshotRepository.class, Map.of("findLatestByStudentNo", snapshots())),
                proxy(ClassInfoRepository.class, Map.of("findByName", Optional.empty())),
                proxy(MajorRepository.class, Map.of()),
                proxy(CollegeRepository.class, Map.of())
        );
    }

    private Map<String, Object> findEvent(List<Map<String, Object>> events, String id) {
        return events.stream().filter(event -> id.equals(event.get("id"))).findFirst().orElseThrow();
    }

    private Student student() {
        Student student = new Student();
        student.setId(7L);
        student.setStudentNo("20230001");
        student.setName("测试学生");
        return student;
    }

    private List<ExtraScoreApplication> approvedApplications() {
        ExtraScoreApplication competition = application(101L, "蓝桥杯软件设计竞赛", "省级一等奖",
                LocalDate.parse("2026-03-01"), "2025-2026", 8.0D, "INTELLECTUAL");
        ExtraScoreApplication volunteer = application(102L, "优秀志愿者", null,
                LocalDate.parse("2025-10-01"), "2024-2025", 3.0D, "MORAL");
        ExtraScoreApplication certificate = application(103L, "大学英语四级证书", null,
                LocalDate.parse("2025-12-20"), "2025-2026", 5.0D, "INTELLECTUAL");
        return List.of(competition, volunteer, certificate);
    }

    private ExtraScoreApplication application(Long id, String itemName, String levelName,
                                               LocalDate awardTime, String academicYear,
                                               Double expectedScore, String scoreType) {
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setId(id);
        application.setItemName(itemName);
        application.setLevelName(levelName);
        application.setAwardTime(awardTime);
        application.setAcademicYear(academicYear);
        application.setExpectedScore(expectedScore);
        application.setScoreType(scoreType);
        application.setTemplateName("测试模板");
        application.setStatus("approved");
        application.setCreatedAt(LocalDateTime.of(2025, 9, 1, 10, 0));
        application.setUpdatedAt(LocalDateTime.of(2025, 9, 1, 10, 0));
        return application;
    }

    private List<SubjectScoreRAIN> courseScores() {
        return List.of(
                score(1L, 1L, 90D, "2024-2025-1", LocalDateTime.of(2025, 2, 1, 9, 0)),
                score(2L, 2L, 85D, "2024-2025-1", LocalDateTime.of(2025, 2, 5, 9, 0)),
                // null 分数记录不应进入任何聚合
                score(4L, 1L, null, "2025-2026-1", LocalDateTime.of(2026, 1, 15, 9, 0)),
                score(3L, 1L, 78D, "2025-2026-1", LocalDateTime.of(2026, 1, 15, 9, 0))
        );
    }

    private SubjectScoreRAIN score(Long id, Long subjectId, Double score, String semester, LocalDateTime updatedAt) {
        SubjectScoreRAIN record = new SubjectScoreRAIN();
        record.setId(id);
        record.setStudentId(7L);
        record.setSubjectId(subjectId);
        record.setScore(score);
        record.setSemester(semester);
        record.setUpdatedAt(updatedAt);
        return record;
    }

    private List<SubjectRAIN> subjects() {
        return List.of(subject(1L, "高等数学"), subject(2L, "数据结构"));
    }

    private SubjectRAIN subject(Long id, String name) {
        SubjectRAIN subject = new SubjectRAIN();
        subject.setId(id);
        subject.setSubjectName(name);
        return subject;
    }

    private List<MoralEducation> moralRecords() {
        MoralEducation withScore = new MoralEducation();
        withScore.setId(900L);
        withScore.setStudentId(7L);
        withScore.setSecondClassScore(6.0D);
        withScore.setActivityScore(80.0D);
        withScore.setSemester("2024-2025-1");
        withScore.setAcademicYear("2024-2025");
        withScore.setUpdatedAt(LocalDateTime.of(2025, 3, 1, 9, 0));

        MoralEducation withoutScore = new MoralEducation();
        withoutScore.setId(901L);
        withoutScore.setStudentId(7L);
        withoutScore.setSecondClassScore(null);
        withoutScore.setSemester("2025-2026-1");
        withoutScore.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        return List.of(withoutScore, withScore);
    }

    private List<CompositeScoreSnapshot> snapshots() {
        // 模拟仓储按学年倒序返回，且同一学年存在重复快照
        return List.of(
                snapshot("2025-2026", 92.5D, 3),
                snapshot("2025-2026", 91.0D, 4),
                snapshot("2024-2025", 88.0D, 6)
        );
    }

    private CompositeScoreSnapshot snapshot(String academicYear, Double totalScore, Integer rankNo) {
        CompositeScoreSnapshot snapshot = new CompositeScoreSnapshot();
        snapshot.setAcademicYear(academicYear);
        snapshot.setTotalScore(totalScore);
        snapshot.setRankNo(rankNo);
        snapshot.setWeightedMoralBaseScore(70.0D);
        snapshot.setWeightedMoralExtraScore(8.0D);
        snapshot.setWeightedIntellectualBaseScore(75.0D);
        snapshot.setWeightedPhysicalBaseScore(80.0D);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Map<String, Object> responses) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (responses.containsKey(method.getName())) {
                        Object response = responses.get(method.getName());
                        if (response instanceof MethodHandler handler) {
                            return handler.invoke(args == null ? new Object[0] : args);
                        }
                        return response;
                    }
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "TestProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private interface MethodHandler {
        Object invoke(Object[] args);
    }
}
