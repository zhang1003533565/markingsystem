package com.markingsystem.service;

import com.markingsystem.entity.CompositeScoreSnapshot;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.Major;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 学生电子成长档案：把课程成绩、竞赛荣誉、社会实践、志愿活动、技能证书、第二课堂学分
 * 聚合为统一时间轴与概览数据，并可生成求职简历素材。全部只读，不做任何写入。
 */
@Service
public class StudentGrowthPortfolioService {

    /** 时间轴事件类型 -> 中文标签 */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "course", "课程",
            "competition", "竞赛",
            "honor", "荣誉",
            "volunteer", "志愿活动",
            "practice", "社会实践",
            "skill", "技能证书",
            "secondClass", "第二课堂",
            "other", "其他"
    );

    /** 排序辅助字段（输出前剥离），避免为排序引入额外包装结构 */
    private static final String SORT_TIME_KEY = "_sortTime";

    private final SubjectScoreRAINRepository subjectScoreRAINRepository;
    private final SubjectRAINRepository subjectRAINRepository;
    private final ExtraScoreApplicationRepository extraScoreApplicationRepository;
    private final MoralEducationRepository moralEducationRepository;
    private final CompositeScoreSnapshotRepository compositeScoreSnapshotRepository;
    private final ClassInfoRepository classInfoRepository;
    private final MajorRepository majorRepository;
    private final CollegeRepository collegeRepository;

    public StudentGrowthPortfolioService(SubjectScoreRAINRepository subjectScoreRAINRepository,
                                         SubjectRAINRepository subjectRAINRepository,
                                         ExtraScoreApplicationRepository extraScoreApplicationRepository,
                                         MoralEducationRepository moralEducationRepository,
                                         CompositeScoreSnapshotRepository compositeScoreSnapshotRepository,
                                         ClassInfoRepository classInfoRepository,
                                         MajorRepository majorRepository,
                                         CollegeRepository collegeRepository) {
        this.subjectScoreRAINRepository = subjectScoreRAINRepository;
        this.subjectRAINRepository = subjectRAINRepository;
        this.extraScoreApplicationRepository = extraScoreApplicationRepository;
        this.moralEducationRepository = moralEducationRepository;
        this.compositeScoreSnapshotRepository = compositeScoreSnapshotRepository;
        this.classInfoRepository = classInfoRepository;
        this.majorRepository = majorRepository;
        this.collegeRepository = collegeRepository;
    }

    /**
     * 成长档案概览：基本信息、统计数据、学年级综测快照、学期课程均分、二课学分序列。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildSummary(Student student) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basic", buildBasicInfo(student));

        List<SubjectScoreRAIN> courseScores = loadCourseScores(student);
        List<ExtraScoreApplication> approvedApplications =
                extraScoreApplicationRepository.findByStudentNoAndStatus(student.getStudentNo(), "approved");

        // 各类型事件计数（与时间轴复用同款分类逻辑，保证口径一致）
        Map<String, Long> typeCounts = approvedApplications.stream()
                .collect(Collectors.groupingBy(this::classifyExtraScore, Collectors.counting()));

        List<MoralEducation> moralRecords = moralEducationRepository.findByStudentId(student.getId()).stream()
                .filter(record -> record.getSecondClassScore() != null)
                .toList();
        double secondClassTotal = moralRecords.stream()
                .mapToDouble(MoralEducation::getSecondClassScore)
                .sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("courseCount", courseScores.size());
        stats.put("courseAverage", round(average(courseScores.stream().map(SubjectScoreRAIN::getScore).toList())));
        stats.put("semesterCount", courseScores.stream()
                .map(score -> normalizeText(score.getSemester()))
                .filter(item -> !item.isEmpty())
                .distinct()
                .count());
        stats.put("competitionCount", typeCounts.getOrDefault("competition", 0L));
        stats.put("honorCount", typeCounts.getOrDefault("honor", 0L));
        stats.put("volunteerCount", typeCounts.getOrDefault("volunteer", 0L));
        stats.put("practiceCount", typeCounts.getOrDefault("practice", 0L));
        stats.put("skillCount", typeCounts.getOrDefault("skill", 0L));
        stats.put("approvedApplicationCount", approvedApplications.size());
        stats.put("secondClassTotal", round(secondClassTotal));
        result.put("stats", stats);

        result.put("yearlySnapshots", buildYearlySnapshots(student.getStudentNo()));
        result.put("semesterCourseAverages", buildSemesterCourseAverages(courseScores));
        result.put("secondClassSeries", moralRecords.stream()
                .sorted(Comparator.comparing((MoralEducation record) -> normalizeText(record.getSemester())))
                .map(record -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("semester", normalizeText(record.getSemester()));
                    row.put("academicYear", resolveMoralAcademicYear(record));
                    row.put("secondClassScore", round(record.getSecondClassScore()));
                    row.put("convertedMoralScore", record.getActivityScore() == null ? null : round(record.getActivityScore()));
                    return row;
                })
                .toList());
        return result;
    }

    /**
     * 成长时间轴：课程（按学期聚合）、二课学分（按学期）、已通过审核的附加分记录（逐条）。
     * 事件按时间倒序排列，前端可按 groupKey（学年/学期）分组展示。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildTimeline(Student student) {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<Long, String> subjectNames = loadSubjectNameMap();

        // 1. 已通过审核的附加分记录：竞赛 / 荣誉 / 志愿 / 实践 / 技能证书
        for (ExtraScoreApplication app : extraScoreApplicationRepository
                .findByStudentNoAndStatus(student.getStudentNo(), "approved")) {
            String type = classifyExtraScore(app);
            LocalDate eventDate = app.getAwardTime() != null
                    ? app.getAwardTime()
                    : (app.getCreatedAt() == null ? null : app.getCreatedAt().toLocalDate());

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", "app-" + app.getId());
            event.put("type", type);
            event.put("typeLabel", TYPE_LABELS.getOrDefault(type, "其他"));
            event.put("date", eventDate == null ? null : eventDate.toString());
            event.put("groupKey", firstNonBlank(app.getAcademicYear(), app.getSubmissionPeriodLabel()));
            event.put("title", firstNonBlank(app.getItemName(), app.getTemplateName(), "附加分记录"));
            event.put("subtitle", firstNonBlank(app.getLevelName(), app.getGroupName()));
            event.put("description", firstNonBlank(app.getDetailDesc(), app.getProofText()));
            event.put("score", app.getExpectedScore() == null ? null : round(app.getExpectedScore()));
            event.put("certName", app.getCertName());
            event.put("templateName", app.getTemplateName());
            event.put("scoreType", app.getScoreType());
            event.put(SORT_TIME_KEY, resolveApplicationSortTime(app));
            events.add(event);
        }

        // 2. 课程成绩：按学期聚合成一条事件，明细科目放在 courses 里
        Map<String, List<SubjectScoreRAIN>> bySemester = loadCourseScores(student).stream()
                .collect(Collectors.groupingBy(
                        score -> firstNonBlank(score.getSemester(), "未标注学期"),
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (Map.Entry<String, List<SubjectScoreRAIN>> entry : bySemester.entrySet()) {
            List<SubjectScoreRAIN> semesterScores = entry.getValue();
            List<Map<String, Object>> courseRows = semesterScores.stream()
                    .sorted(Comparator.comparing(SubjectScoreRAIN::getScore).reversed())
                    .map(score -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("subjectName", subjectNames.getOrDefault(score.getSubjectId(), "科目#" + score.getSubjectId()));
                        row.put("score", round(score.getScore()));
                        return row;
                    })
                    .toList();

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", "course-" + entry.getKey());
            event.put("type", "course");
            event.put("typeLabel", TYPE_LABELS.get("course"));
            event.put("date", null);
            event.put("groupKey", entry.getKey());
            event.put("title", entry.getKey() + " 课程成绩");
            event.put("subtitle", courseRows.size() + " 门");
            event.put("courses", courseRows);
            event.put("score", round(average(semesterScores.stream().map(SubjectScoreRAIN::getScore).toList())));
            event.put(SORT_TIME_KEY, semesterScores.stream()
                    .map(SubjectScoreRAIN::getUpdatedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null));
            events.add(event);
        }

        // 3. 第二课堂学分：每学期一条
        for (MoralEducation record : moralEducationRepository.findByStudentId(student.getId())) {
            if (record.getSecondClassScore() == null) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", "second-class-" + record.getId());
            event.put("type", "secondClass");
            event.put("typeLabel", TYPE_LABELS.get("secondClass"));
            event.put("date", null);
            event.put("groupKey", firstNonBlank(record.getSemester(), resolveMoralAcademicYear(record)));
            event.put("title", firstNonBlank(record.getSemester(), resolveMoralAcademicYear(record)) + " 第二课堂学分");
            event.put("score", round(record.getSecondClassScore()));
            if (record.getActivityScore() != null) {
                event.put("description", "折算德育活动分 " + round(record.getActivityScore()) + " 分");
            }
            event.put(SORT_TIME_KEY, record.getUpdatedAt());
            events.add(event);
        }

        // 时间倒序；无时间的排最后，再按标题保证稳定
        Comparator<Map<String, Object>> byTime = Comparator.comparing(
                (Map<String, Object> event) -> resolveEventSortTime(event),
                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()));
        events.sort(byTime.thenComparing(
                event -> normalizeText(event.get("title")),
                Comparator.naturalOrder()));
        events.forEach(event -> event.remove(SORT_TIME_KEY));
        return events;
    }

    /**
     * 求职简历素材：按简历板块（教育经历 / 荣誉与竞赛 / 实践经历 / 技能证书）输出可直接复制的文本。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildResumeMaterials(Student student) {
        List<Map<String, Object>> timeline = buildTimeline(student);
        Map<String, Object> summary = buildSummary(student);
        Map<String, Object> basic = asMap(summary.get("basic"));
        Map<String, Object> stats = asMap(summary.get("stats"));

        List<String> education = new ArrayList<>();
        String majorClass = joinNonBlank(" · ", normalizeText(basic.get("majorName")), normalizeText(student.getClassName()));
        String gradeLine = normalizeText(basic.get("grade"));
        if (!majorClass.isEmpty() || !gradeLine.isEmpty()) {
            education.add((gradeLine + " " + majorClass).trim());
        }
        Object courseAverage = stats.get("courseAverage");
        Object courseCount = stats.get("courseCount");
        if (courseAverage != null && courseCount instanceof Number number && number.intValue() > 0) {
            education.add("课程平均分 " + courseAverage + "（共 " + number.intValue() + " 门）");
        }

        Map<String, List<Map<String, Object>>> groupedByType = timeline.stream()
                .collect(Collectors.groupingBy(event -> normalizeText(event.get("type"))));

        List<Map<String, Object>> honors = collectResumeItems(groupedByType, List.of("competition", "honor"));
        List<Map<String, Object>> practice = collectResumeItems(groupedByType, List.of("volunteer", "practice"));
        double secondClassTotal = stats.get("secondClassTotal") instanceof Number number ? number.doubleValue() : 0D;
        if (secondClassTotal > 0) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "resume-second-class-total");
            item.put("text", "第二课堂学分累计 " + formatNumber(secondClassTotal) + " 分");
            item.put("date", null);
            practice.add(item);
        }
        List<Map<String, Object>> skills = collectResumeItems(groupedByType, List.of("skill"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("education", education);
        result.put("honors", honors);
        result.put("practice", practice);
        result.put("skills", skills);
        return result;
    }

    /** 附加分记录 -> 时间轴类型：关键词优先，未命中时按 scoreType 兜底 */
    private String classifyExtraScore(ExtraScoreApplication app) {
        String haystack = String.join(" ",
                normalizeText(app.getItemName()),
                normalizeText(app.getGroupName()),
                normalizeText(app.getTemplateName()),
                normalizeText(app.getCertName()),
                normalizeText(app.getLevelName()));

        if (containsAny(haystack, "志愿", "公益", "支教")) {
            return "volunteer";
        }
        if (containsAny(haystack, "竞赛", "大赛", "比赛", "挑战杯", "蓝桥", "数模", "创新创业")) {
            return "competition";
        }
        if (containsAny(haystack, "证书", "资格", "四六级", "计算机等级", "技能")) {
            return "skill";
        }
        if (containsAny(haystack, "实践", "军训", "劳动", "研学")) {
            return "practice";
        }
        if (containsAny(haystack, "荣誉", "表彰", "获奖", "三好", "优秀", "奖学金", "标兵")) {
            return "honor";
        }
        return switch (normalizeText(app.getScoreType()).toUpperCase()) {
            case "MORAL" -> "practice";
            case "PHYSICAL" -> "honor";
            default -> "other";
        };
    }

    private Map<String, Object> buildBasicInfo(Student student) {
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("studentNo", student.getStudentNo());
        basic.put("name", student.getName());
        basic.put("className", student.getClassName());

        if (student.getClassName() != null && !student.getClassName().isBlank()) {
            classInfoRepository.findByName(student.getClassName().trim()).ifPresent(classInfo -> {
                basic.put("grade", classInfo.getGrade());
                Major major = classInfo.getMajorId() == null
                        ? null
                        : majorRepository.findById(classInfo.getMajorId()).orElse(null);
                if (major != null) {
                    basic.put("majorName", major.getName());
                }
                Long resolvedCollegeId = classInfo.getCollegeId() != null
                        ? classInfo.getCollegeId()
                        : (major == null ? null : major.getCollegeId());
                if (resolvedCollegeId != null) {
                    collegeRepository.findById(resolvedCollegeId)
                            .ifPresent(college -> basic.put("collegeName", college.getName()));
                }
            });
        }
        return basic;
    }

    private List<Map<String, Object>> buildYearlySnapshots(String studentNo) {
        Set<String> seenYears = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CompositeScoreSnapshot snapshot : compositeScoreSnapshotRepository.findLatestByStudentNo(studentNo)) {
            if (snapshot.getAcademicYear() == null || !seenYears.add(snapshot.getAcademicYear())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("academicYear", snapshot.getAcademicYear());
            row.put("totalScore", snapshot.getTotalScore() == null ? null : round(snapshot.getTotalScore()));
            row.put("rankNo", snapshot.getRankNo());
            row.put("moralScore", round(sum(snapshot.getWeightedMoralBaseScore(), snapshot.getWeightedMoralExtraScore(),
                    snapshot.getMoralBaseScore(), snapshot.getMoralExtraScore())));
            row.put("intellectualScore", round(sum(
                    snapshot.getWeightedIntellectualBaseScore(), snapshot.getWeightedIntellectualExtraScore(),
                    snapshot.getIntellectualBaseScore(), snapshot.getIntellectualExtraScore())));
            row.put("physicalScore", round(sum(
                    snapshot.getWeightedPhysicalBaseScore(), snapshot.getWeightedPhysicalExtraScore(),
                    snapshot.getPhysicalBaseScore(), snapshot.getPhysicalExtraScore())));
            rows.add(row);
        }
        // findLatest 是学年倒序，图表需要正序年份
        Collections.reverse(rows);
        return rows;
    }

    private List<Map<String, Object>> buildSemesterCourseAverages(List<SubjectScoreRAIN> courseScores) {
        Map<String, List<SubjectScoreRAIN>> bySemester = courseScores.stream()
                .collect(Collectors.groupingBy(
                        score -> firstNonBlank(score.getSemester(), "未标注学期"),
                        TreeMap::new,
                        Collectors.toList()));
        return bySemester.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("semester", entry.getKey());
            row.put("average", round(average(entry.getValue().stream().map(SubjectScoreRAIN::getScore).toList())));
            row.put("courseCount", entry.getValue().size());
            return row;
        }).toList();
    }

    private List<SubjectScoreRAIN> loadCourseScores(Student student) {
        return subjectScoreRAINRepository.findByStudentId(student.getId()).stream()
                .filter(score -> score.getScore() != null)
                .toList();
    }

    private Map<Long, String> loadSubjectNameMap() {
        return subjectRAINRepository.findAll().stream()
                .collect(Collectors.toMap(SubjectRAIN::getId, SubjectRAIN::getSubjectName, (left, right) -> left));
    }

    private List<Map<String, Object>> collectResumeItems(Map<String, List<Map<String, Object>>> groupedByType,
                                                         List<String> types) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String type : types) {
            for (Map<String, Object> event : groupedByType.getOrDefault(type, List.of())) {
                items.add(toResumeItem(event));
            }
        }
        return items;
    }

    private Map<String, Object> toResumeItem(Map<String, Object> event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "resume-" + event.get("id"));
        StringBuilder text = new StringBuilder();
        String date = normalizeText(event.get("date"));
        if (!date.isEmpty()) {
            text.append(date).append("　");
        }
        text.append(normalizeText(event.get("title")));
        String subtitle = normalizeText(event.get("subtitle"));
        if (!subtitle.isEmpty()) {
            text.append("（").append(subtitle).append("）");
        }
        String description = normalizeText(event.get("description"));
        if (!description.isEmpty()) {
            text.append("：").append(description);
        }
        item.put("text", text.toString());
        item.put("date", event.get("date"));
        item.put("type", event.get("type"));
        return item;
    }

    private LocalDateTime resolveEventSortTime(Map<String, Object> event) {
        Object value = event.get(SORT_TIME_KEY);
        return value instanceof LocalDateTime time ? time : null;
    }

    private LocalDateTime resolveApplicationSortTime(ExtraScoreApplication app) {
        if (app.getAwardTime() != null) {
            return app.getAwardTime().atTime(12, 0);
        }
        if (app.getTeacherReviewedAt() != null) {
            return app.getTeacherReviewedAt();
        }
        if (app.getCommitteeReviewedAt() != null) {
            return app.getCommitteeReviewedAt();
        }
        return app.getUpdatedAt() != null ? app.getUpdatedAt() : app.getCreatedAt();
    }

    private String resolveMoralAcademicYear(MoralEducation record) {
        if (record.getAcademicYear() != null && !record.getAcademicYear().isBlank()) {
            return record.getAcademicYear();
        }
        return record.getSemester() == null ? "" : record.getSemester();
    }

    private double sum(Double... values) {
        double total = 0D;
        for (Double value : values) {
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private double average(List<Double> values) {
        List<Double> valid = values.stream().filter(Objects::nonNull).toList();
        if (valid.isEmpty()) {
            return 0D;
        }
        return valid.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** 简历文本里的数字去掉多余尾零（6.0 -> 6，84.33 -> 84.33） */
    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(round(value));
    }

    private boolean containsAny(String haystack, String... keywords) {
        if (haystack == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> valid = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                valid.add(value.trim());
            }
        }
        return String.join(separator, valid);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
