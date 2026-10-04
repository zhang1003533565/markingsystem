package com.markingsystem.service;

import com.markingsystem.entity.*;
import com.markingsystem.repository.*;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssistantService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    @Autowired
    private ExtraScoreApplicationRepository extraScoreApplicationRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private CollegeRepository collegeRepository;

    @Autowired
    private GradeInfoRepository gradeInfoRepository;

    @Autowired
    private CompositeScoreSnapshotRepository compositeScoreSnapshotRepository;

    @Autowired
    private MoralEducationRepository moralEducationRepository;

    @Autowired
    private AcademicYearService academicYearService;

    public Map<String, Object> getStudentSummary(RequestUserContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", ctx.getRole());
        result.put("currentAcademicYear", currentAcademicYear());

        Student student = findStudent(ctx);
        if (student == null) {
            result.put("student", null);
            return result;
        }

        result.put("student", buildStudentInfo(student));

        List<SubjectScoreRAIN> scores = subjectScoreRAINRepository.findByStudentId(student.getId());
        result.put("scoreStats", buildScoreStats(scores));
        result.put("recentScores", buildRecentScores(scores, 12));
        result.put("scoreTrend", buildScoreTrend(scores));
        result.put("riskWarnings", buildRiskWarnings(scores));

        result.put("secondClassScores", buildSecondClassScores(student.getId()));
        result.put("extraScoreApplications", buildExtraScoreSummary(student.getStudentNo()));
        result.put("compositeScore", buildStudentCompositeScore(student.getStudentNo()));
        result.put("announcements", buildAnnouncements(3));

        return result;
    }

    public Map<String, Object> getTeacherSummary(RequestUserContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", ctx.getRole());
        result.put("currentAcademicYear", currentAcademicYear());

        Long teacherId = resolveTeacherId(ctx);
        if (teacherId == null) {
            result.put("teacher", null);
            result.put("classes", List.of());
            result.put("classCount", 0);
            result.put("studentCount", 0);
            result.put("classNames", List.of());
            result.put("classStats", List.of());
            result.put("pendingReviewCount", 0);
            result.put("pendingReviewTop", List.of());
            return result;
        }

        Map<String, Object> teacherInfo = new LinkedHashMap<>();
        teacherInfo.put("teacherId", teacherId);
        userRepository.findById(teacherId).ifPresent(user -> {
            teacherInfo.put("name", user.getNickname() != null && !user.getNickname().isBlank()
                    ? user.getNickname()
                    : user.getUsername());
        });
        result.put("teacher", teacherInfo);

        List<ClassInfo> classes = classInfoRepository.findByTeacherId(teacherId);
        List<Student> students = studentRepository.findByTeacherId(teacherId);
        List<SubjectScoreRAIN> allScores = subjectScoreRAINRepository.findByStudentTeacherId(teacherId);
        Map<Long, Student> studentMap = students.stream()
                .collect(Collectors.toMap(Student::getId, s -> s, (left, right) -> left));

        result.put("classCount", classes.size());
        result.put("studentCount", students.size());
        result.put("classNames", classes.stream().map(ClassInfo::getName).collect(Collectors.toList()));
        result.put("classStats", buildTeacherClassStats(classes, studentMap, allScores));

        List<ExtraScoreApplication> pending = extraScoreApplicationRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .filter(a -> isPendingStatus(a.getStatus()))
                .collect(Collectors.toList());
        result.put("pendingReviewCount", pending.size());
        result.put("pendingReviewTop", buildPendingApplications(pending, 5));
        result.put("announcements", buildAnnouncements(3));

        return result;
    }

    public Map<String, Object> getAdminSummary(RequestUserContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", ctx.getRole());
        result.put("currentAcademicYear", currentAcademicYear());

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("studentCount", studentRepository.count());
        overview.put("classCount", classInfoRepository.count());
        overview.put("userCount", userRepository.count());
        overview.put("collegeCount", collegeRepository.count());
        overview.put("majorCount", majorRepository.count());
        overview.put("gradeCount", gradeInfoRepository.count());
        overview.put("announcementCount", announcementRepository.count());
        List<ExtraScoreApplication> allApplications = extraScoreApplicationRepository.findAll();
        overview.put("pendingReviewCount", allApplications.stream().filter(a -> isPendingStatus(a.getStatus())).count());
        result.put("overview", overview);

        result.put("compositeScore", buildAdminCompositeScoreSummary());
        result.put("secondClass", buildAdminSecondClassSummary());
        result.put("announcements", buildAnnouncements(3));
        result.put("pendingReviewTop", buildPendingApplications(allApplications, 3));

        return result;
    }

    // ---------- 工具层使用：按需真实查询 ----------

    public List<Map<String, Object>> getStudentScoresForChat(RequestUserContext ctx, String semester, int limit) {
        Student student = findStudent(ctx);
        if (student == null) {
            return List.of();
        }
        List<SubjectScoreRAIN> scores = subjectScoreRAINRepository.findByStudentId(student.getId()).stream()
                .filter(s -> semester == null || semester.isBlank() || semester.equals(s.getSemester()))
                .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SubjectScoreRAIN::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 100)))
                .collect(Collectors.toList());
        return scores.stream().map(this::toScoreItem).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getStudentScoreTrendForChat(RequestUserContext ctx) {
        Student student = findStudent(ctx);
        if (student == null) {
            return List.of();
        }
        return buildScoreTrend(subjectScoreRAINRepository.findByStudentId(student.getId()));
    }

    public List<Map<String, Object>> getStudentSecondClassForChat(RequestUserContext ctx) {
        Student student = findStudent(ctx);
        if (student == null) {
            return List.of();
        }
        return buildSecondClassScores(student.getId());
    }

    public List<Map<String, Object>> getLatestAnnouncementsForChat(int limit) {
        return buildAnnouncements(Math.max(1, Math.min(limit, 10)));
    }

    public List<Map<String, Object>> getTeacherClassStatsForChat(RequestUserContext ctx) {
        Long teacherId = resolveTeacherId(ctx);
        if (teacherId == null) {
            return List.of();
        }
        List<ClassInfo> classes = classInfoRepository.findByTeacherId(teacherId);
        List<Student> students = studentRepository.findByTeacherId(teacherId);
        List<SubjectScoreRAIN> allScores = subjectScoreRAINRepository.findByStudentTeacherId(teacherId);
        Map<Long, Student> studentMap = students.stream()
                .collect(Collectors.toMap(Student::getId, s -> s, (left, right) -> left));
        return buildTeacherClassStats(classes, studentMap, allScores);
    }

    public List<Map<String, Object>> getPendingApplicationsForChat(RequestUserContext ctx, int limit) {
        List<ExtraScoreApplication> applications;
        if (ctx.isAdmin()) {
            applications = extraScoreApplicationRepository.findAll().stream()
                    .filter(a -> isPendingStatus(a.getStatus()))
                    .sorted(Comparator.comparing(ExtraScoreApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
        } else {
            Long teacherId = resolveTeacherId(ctx);
            if (teacherId == null) {
                return List.of();
            }
            applications = extraScoreApplicationRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                    .filter(a -> isPendingStatus(a.getStatus()))
                    .collect(Collectors.toList());
        }
        return buildPendingApplications(applications, Math.max(1, Math.min(limit, 20)));
    }

    public Map<String, Object> getAdminOverviewForChat(RequestUserContext ctx) {
        return getAdminSummary(ctx);
    }

    // ---------- 内部构建方法 ----------

    private Map<String, Object> buildStudentInfo(Student student) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", student.getName());
        info.put("studentNo", student.getStudentNo());
        info.put("className", student.getClassName());

        if (student.getClassName() != null && !student.getClassName().isBlank()) {
            classInfoRepository.findByName(student.getClassName().trim()).ifPresent(classInfo -> {
                info.put("grade", classInfo.getGrade());
                info.put("majorId", classInfo.getMajorId());
                info.put("collegeId", classInfo.getCollegeId());
                majorRepository.findById(classInfo.getMajorId()).ifPresent(major -> {
                    info.put("majorName", major.getName());
                    Long collegeId = major.getCollegeId() != null ? major.getCollegeId() : classInfo.getCollegeId();
                    if (collegeId != null) {
                        collegeRepository.findById(collegeId).ifPresent(college -> info.put("collegeName", college.getName()));
                    }
                });
                if (!info.containsKey("collegeName") && classInfo.getCollegeId() != null) {
                    collegeRepository.findById(classInfo.getCollegeId()).ifPresent(college -> info.put("collegeName", college.getName()));
                }
            });
        }
        if (student.getTeacherId() != null) {
            userRepository.findById(student.getTeacherId()).ifPresent(user -> info.put("teacherName",
                    user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername()));
        }
        return info;
    }

    private Map<String, Object> buildScoreStats(List<SubjectScoreRAIN> scores) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Double> validScores = scores.stream()
                .map(SubjectScoreRAIN::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        stats.put("totalCourses", validScores.size());
        if (validScores.isEmpty()) {
            stats.put("averageScore", null);
            stats.put("maxScore", null);
            stats.put("minScore", null);
            stats.put("passRate", null);
            stats.put("failedCount", 0);
            return stats;
        }
        DoubleSummaryStatistics sum = validScores.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        long failedCount = validScores.stream().filter(score -> score < 60).count();
        stats.put("averageScore", round1(sum.getAverage()));
        stats.put("maxScore", round1(sum.getMax()));
        stats.put("minScore", round1(sum.getMin()));
        stats.put("passRate", round1((sum.getCount() - failedCount) * 100.0 / sum.getCount()));
        stats.put("failedCount", failedCount);
        return stats;
    }

    private List<Map<String, Object>> buildRecentScores(List<SubjectScoreRAIN> scores, int limit) {
        return scores.stream()
                .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SubjectScoreRAIN::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::toScoreItem)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toScoreItem(SubjectScoreRAIN s) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("subjectName", s.getSubject() != null ? s.getSubject().getSubjectName() : null);
        item.put("score", s.getScore());
        item.put("semester", s.getSemester());
        item.put("isPass", s.getScore() == null ? null : s.getScore() >= 60);
        return item;
    }

    private List<Map<String, Object>> buildScoreTrend(List<SubjectScoreRAIN> scores) {
        Map<String, List<Double>> bySemester = scores.stream()
                .filter(s -> s.getScore() != null && s.getSemester() != null)
                .collect(Collectors.groupingBy(
                        SubjectScoreRAIN::getSemester,
                        LinkedHashMap::new,
                        Collectors.mapping(SubjectScoreRAIN::getScore, Collectors.toList())
                ));
        return bySemester.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("semester", entry.getKey());
                    item.put("avgScore", round1(entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0D)));
                    item.put("count", entry.getValue().size());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRiskWarnings(List<SubjectScoreRAIN> scores) {
        return scores.stream()
                .filter(s -> s.getScore() != null && s.getScore() < 60)
                .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("subjectName", s.getSubject() != null ? s.getSubject().getSubjectName() : null);
                    item.put("score", s.getScore());
                    item.put("semester", s.getSemester());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildSecondClassScores(Long studentId) {
        return moralEducationRepository.findByStudentId(studentId).stream()
                .filter(m -> m.getSecondClassScore() != null)
                .sorted(Comparator.comparing(MoralEducation::getAcademicYear, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MoralEducation::getSemester, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(m -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("academicYear", m.getAcademicYear());
                    item.put("semester", m.getSemester());
                    item.put("secondClassScore", m.getSecondClassScore());
                    item.put("moralScore", m.getActivityScore());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildExtraScoreSummary(String studentNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<ExtraScoreApplication> applications = extraScoreApplicationRepository.findByStudentNoOrderByCreatedAtDesc(studentNo);
        Map<String, Long> statusCount = applications.stream()
                .collect(Collectors.groupingBy(a -> a.getStatus() == null ? "UNKNOWN" : a.getStatus(), Collectors.counting()));
        result.put("total", applications.size());
        result.put("statusCounts", statusCount);
        result.put("recentApplications", applications.stream()
                .limit(3)
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("itemName", a.getItemName());
                    item.put("status", a.getStatus());
                    item.put("templateName", a.getTemplateName());
                    item.put("createdAt", a.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> buildStudentCompositeScore(String studentNo) {
        return compositeScoreSnapshotRepository.findLatestByStudentNo(studentNo).stream()
                .findFirst()
                .map(snapshot -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("academicYear", snapshot.getAcademicYear());
                    item.put("className", snapshot.getClassName());
                    item.put("totalScore", round1(snapshot.getTotalScore()));
                    item.put("rankNo", snapshot.getRankNo());
                    item.put("moralTotal", round1(defaultZero(snapshot.getMoralBaseScore()) + defaultZero(snapshot.getMoralExtraScore())));
                    item.put("intellectualTotal", round1(defaultZero(snapshot.getIntellectualBaseScore()) + defaultZero(snapshot.getIntellectualExtraScore())));
                    item.put("physicalTotal", round1(defaultZero(snapshot.getPhysicalBaseScore()) + defaultZero(snapshot.getPhysicalExtraScore())));
                    return item;
                })
                .orElse(null);
    }

    private List<Map<String, Object>> buildTeacherClassStats(List<ClassInfo> classes,
                                                             Map<Long, Student> studentMap,
                                                             List<SubjectScoreRAIN> allScores) {
        Map<String, List<Long>> studentIdsByClass = classes.stream()
                .collect(Collectors.toMap(
                        ClassInfo::getName,
                        c -> studentMap.values().stream()
                                .filter(s -> Objects.equals(s.getClassName(), c.getName()))
                                .map(Student::getId)
                                .collect(Collectors.toList()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, List<Double>> studentAvgScore = allScores.stream()
                .filter(s -> s.getStudentId() != null && s.getScore() != null)
                .collect(Collectors.groupingBy(
                        SubjectScoreRAIN::getStudentId,
                        Collectors.mapping(SubjectScoreRAIN::getScore, Collectors.toList())
                ));
        Map<Long, Boolean> studentHasFail = allScores.stream()
                .filter(s -> s.getStudentId() != null && s.getScore() != null && s.getScore() < 60)
                .collect(Collectors.toMap(
                        SubjectScoreRAIN::getStudentId,
                        s -> true,
                        (left, right) -> true
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassInfo clazz : classes) {
            List<Long> ids = studentIdsByClass.getOrDefault(clazz.getName(), List.of());
            List<Double> avgs = ids.stream()
                    .map(studentAvgScore::get)
                    .filter(Objects::nonNull)
                    .map(list -> list.stream().mapToDouble(Double::doubleValue).average().orElse(0D))
                    .collect(Collectors.toList());
            long riskCount = ids.stream().filter(studentHasFail::containsKey).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("className", clazz.getName());
            item.put("grade", clazz.getGrade());
            item.put("studentCount", ids.size());
            item.put("avgScore", avgs.isEmpty() ? null : round1(avgs.stream().mapToDouble(Double::doubleValue).average().orElse(0D)));
            item.put("passRate", avgs.isEmpty() ? null : round1(avgs.stream().filter(v -> v >= 60).count() * 100.0 / avgs.size()));
            item.put("riskCount", riskCount);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildPendingApplications(List<ExtraScoreApplication> applications, int limit) {
        return applications.stream()
                .filter(a -> isPendingStatus(a.getStatus()))
                .sorted(Comparator.comparing(ExtraScoreApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("studentName", a.getStudentName());
                    item.put("studentNo", a.getStudentNo());
                    item.put("className", a.getClassName());
                    item.put("itemName", a.getItemName());
                    item.put("status", a.getStatus());
                    item.put("expectedScore", a.getExpectedScore());
                    item.put("createdAt", a.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildAdminCompositeScoreSummary() {
        String academicYear = currentAcademicYear();
        if (academicYear == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("academicYear", null);
            empty.put("snapshotCount", 0);
            return empty;
        }
        List<CompositeScoreSnapshot> snapshots = compositeScoreSnapshotRepository
                .findByAcademicYearOrderByRankNoAscStudentNoAsc(academicYear);
        if (snapshots.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("academicYear", academicYear);
            empty.put("snapshotCount", 0);
            return empty;
        }
        DoubleSummaryStatistics stats = snapshots.stream()
                .map(CompositeScoreSnapshot::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("academicYear", academicYear);
        item.put("snapshotCount", snapshots.size());
        item.put("averageTotalScore", stats.getCount() == 0 ? null : round1(stats.getAverage()));
        item.put("maxTotalScore", stats.getCount() == 0 ? null : round1(stats.getMax()));
        item.put("minTotalScore", stats.getCount() == 0 ? null : round1(stats.getMin()));
        return item;
    }

    private Map<String, Object> buildAdminSecondClassSummary() {
        String academicYear = currentAcademicYear();
        if (academicYear == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("academicYear", null);
            empty.put("recordCount", 0);
            return empty;
        }
        List<MoralEducation> records = moralEducationRepository.findSecondClassRecordsByAcademicYear(academicYear);
        if (records.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("academicYear", academicYear);
            empty.put("recordCount", 0);
            return empty;
        }
        DoubleSummaryStatistics stats = records.stream()
                .map(MoralEducation::getSecondClassScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("academicYear", academicYear);
        item.put("recordCount", records.size());
        item.put("averageSecondClassScore", stats.getCount() == 0 ? null : round1(stats.getAverage()));
        item.put("maxSecondClassScore", stats.getCount() == 0 ? null : round1(stats.getMax()));
        return item;
    }

    private List<Map<String, Object>> buildAnnouncements(int limit) {
        return announcementRepository.findAll().stream()
                .sorted(Comparator.comparing(Announcement::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("title", a.getTitle());
                    item.put("category", a.getCategory());
                    item.put("publisherName", a.getPublisherName());
                    item.put("createdAt", a.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private long countPendingApplications(Long teacherId) {
        List<ExtraScoreApplication> applications = teacherId == null
                ? extraScoreApplicationRepository.findAll()
                : extraScoreApplicationRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        return applications.stream().filter(a -> isPendingStatus(a.getStatus())).count();
    }

    private boolean isPendingStatus(String status) {
        return status != null && ("PENDING".equalsIgnoreCase(status) || "SUBMITTED".equalsIgnoreCase(status));
    }

    private Student findStudent(RequestUserContext ctx) {
        if (ctx.getStudentNo() != null) {
            return studentRepository.findByStudentNo(ctx.getStudentNo()).orElse(null);
        }
        if (ctx.getUserId() != null) {
            return studentRepository.findByUserId(ctx.getUserId()).orElse(null);
        }
        return null;
    }

    private Long resolveTeacherId(RequestUserContext ctx) {
        if (ctx.getTeacherId() != null) {
            return ctx.getTeacherId();
        }
        if (ctx.getUserId() != null && ctx.isTeacher()) {
            return ctx.getUserId();
        }
        return null;
    }

    private String currentAcademicYear() {
        return academicYearService.findCurrent()
                .map(AcademicYear::getName)
                .orElse(null);
    }

    private double defaultZero(Double value) {
        return value == null ? 0D : value;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
