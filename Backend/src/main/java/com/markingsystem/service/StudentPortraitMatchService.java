package com.markingsystem.service;

import com.markingsystem.dto.StudentPortraitMatchResultDTO;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.IntellectualEducation;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.PhysicalEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.entity.TeacherPortraitConfig;
import com.markingsystem.entity.TeacherPortraitRule;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StudentPortraitMatchService {

    private static final List<String> COMPETITION_KEYWORDS = List.of("挑战杯", "互联网+", "创新创业", "大创", "创赛", "竞赛", "比赛", "学科竞赛");
    private static final List<String> TECH_KEYWORDS = List.of("计算机设计", "软件杯", "蓝桥杯", "程序设计", "算法", "编程", "代码", "Java", "Python", "C语言", "C++", "数据结构", "数据库", "网络", "软件工程", "人工智能");
    private static final List<String> EMPLOYMENT_KEYWORDS = List.of("实习", "项目", "证书", "企业", "就业", "实践", "见习", "校企", "岗位", "招聘");
    private static final List<String> SERVICE_KEYWORDS = List.of("志愿", "基层", "党员", "干部", "班委", "团委", "团学", "社会实践", "支教", "社区", "文明", "纪律");
    private static final List<String> TECH_SUBJECT_KEYWORDS = List.of("程序", "软件", "算法", "数据", "Java", "Python", "C语言", "数据库", "网络", "操作系统", "计算机");

    private final StudentService studentService;
    private final MoralEducationService moralEducationService;
    private final IntellectualEducationService intellectualEducationService;
    private final PhysicalEducationService physicalEducationService;
    private final SubjectScoreRAINRepository subjectScoreRAINRepository;
    private final ExtraScoreApplicationRepository extraScoreApplicationRepository;
    private final TeacherPortraitRuleService teacherPortraitRuleService;

    public StudentPortraitMatchService(
            StudentService studentService,
            MoralEducationService moralEducationService,
            IntellectualEducationService intellectualEducationService,
            PhysicalEducationService physicalEducationService,
            SubjectScoreRAINRepository subjectScoreRAINRepository,
            ExtraScoreApplicationRepository extraScoreApplicationRepository,
            TeacherPortraitRuleService teacherPortraitRuleService) {
        this.studentService = studentService;
        this.moralEducationService = moralEducationService;
        this.intellectualEducationService = intellectualEducationService;
        this.physicalEducationService = physicalEducationService;
        this.subjectScoreRAINRepository = subjectScoreRAINRepository;
        this.extraScoreApplicationRepository = extraScoreApplicationRepository;
        this.teacherPortraitRuleService = teacherPortraitRuleService;
    }

    public Optional<StudentPortraitMatchResultDTO> getPortraitMatch(String studentNo, Long userId) {
        Optional<Student> studentOpt = resolveStudent(studentNo, userId);
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        // 自动获取学生关联的教师 ID，使教师的自定义规则生效
        return Optional.of(buildPortraitMatch(studentOpt.get(), studentOpt.get().getTeacherId()));
    }

    public Optional<StudentPortraitMatchResultDTO> getPortraitMatch(String studentNo, Long userId, Long teacherId) {
        Optional<Student> studentOpt = resolveStudent(studentNo, userId);
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildPortraitMatch(studentOpt.get(), teacherId));
    }

    private StudentPortraitMatchResultDTO buildPortraitMatch(Student student, Long teacherId) {
        PortraitMetrics metrics = buildMetrics(student);

        // 检查是否有自定义规则
        boolean useCustomRules = teacherId != null && teacherPortraitRuleService.isEnabled(teacherId);
        TeacherPortraitConfig customConfig = useCustomRules ? teacherPortraitRuleService.getConfig(teacherId).orElse(null) : null;
        List<TeacherPortraitRule> customRules = useCustomRules ? teacherPortraitRuleService.getRules(teacherId) : List.of();

        List<PortraitCandidate> candidates;
        if (useCustomRules && !customRules.isEmpty()) {
            candidates = buildCandidatesWithCustomRules(metrics, customConfig, customRules);
        } else {
            candidates = List.of(
                    buildAllRoundCandidate(metrics),
                    buildAcademicCandidate(metrics),
                    buildCompetitionCandidate(metrics),
                    buildTechnicalCandidate(metrics),
                    buildEmploymentCandidate(metrics),
                    buildCivilServiceCandidate(metrics),
                    buildPotentialCandidate(metrics)
            );
        }

        double total = candidates.stream().mapToDouble(candidate -> Math.max(candidate.rawScore, 1D)).sum();
        List<StudentPortraitMatchResultDTO.PortraitMatchItemDTO> items = new ArrayList<>();
        for (PortraitCandidate candidate : candidates) {
            StudentPortraitMatchResultDTO.PortraitMatchItemDTO item = candidate.toDto();
            double safeRaw = Math.max(candidate.rawScore, 1D);
            item.setRawScore(round1(safeRaw));
            item.setPercentage(round1(safeRaw * 100D / total));
            items.add(item);
        }

        items.sort(Comparator.comparing(StudentPortraitMatchResultDTO.PortraitMatchItemDTO::getPercentage, Comparator.nullsLast(Double::compareTo)).reversed());
        if (!items.isEmpty()) {
            double sum = items.stream().mapToDouble(item -> item.getPercentage() == null ? 0D : item.getPercentage()).sum();
            double delta = round1(100D - round1(sum));
            StudentPortraitMatchResultDTO.PortraitMatchItemDTO last = items.get(items.size() - 1);
            last.setPercentage(round1((last.getPercentage() == null ? 0D : last.getPercentage()) + delta));
        }

        StudentPortraitMatchResultDTO result = new StudentPortraitMatchResultDTO();
        result.setStudentId(student.getId());
        result.setStudentNo(student.getStudentNo());
        result.setStudentName(student.getName());
        result.setClassName(student.getClassName());
        result.setItems(items);
        result.setPrimaryType(items.isEmpty() ? "发展潜力型" : items.get(0).getTypeName());
        result.setSummary(buildSummary(items));
        result.setReasons(buildOverallReasons(metrics, items));
        result.setDataSources(buildDataSources(metrics));
        result.setFeatureSnapshot(buildFeatureSnapshot(metrics, items));
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }

    private List<PortraitCandidate> buildCandidatesWithCustomRules(PortraitMetrics metrics, TeacherPortraitConfig config, List<TeacherPortraitRule> rules) {
        List<PortraitCandidate> candidates = new ArrayList<>();

        for (TeacherPortraitRule rule : rules) {
            double rawScore = buildRawScoreWithRule(metrics, rule);
            String typeName = rule.getTypeName() != null ? rule.getTypeName() : getDefaultTypeName(rule.getTypeKey());
            candidates.add(candidate(rule.getTypeKey(), typeName, rawScore, buildReasonsForRule(metrics, rule)));
        }

        return candidates;
    }

    private double buildRawScoreWithRule(PortraitMetrics metrics, TeacherPortraitRule rule) {
        // 各维度标准化分数（值/最大值，范围0-1）
        double moralNorm = Math.min(metrics.moralAverage / 100.0, 1.0);
        double intellectualNorm = Math.min(metrics.intellectualAverage / 100.0, 1.0);
        double physicalNorm = Math.min(metrics.physicalAverage / 100.0, 1.0);
        double extraNorm = Math.min(metrics.approvedExtraScore / 30.0, 1.0); // 附加分上限30
        double excellentNorm = Math.min(metrics.excellentCourseCount / 5.0, 1.0); // 优秀课程上限5门

        double moralW = toRatio(rule.getMoralWeight());
        double intellectualW = toRatio(rule.getIntellectualWeight());
        double physicalW = toRatio(rule.getPhysicalWeight());
        double extraW = toRatio(rule.getExtraScoreWeight());
        double excellentW = toRatio(rule.getExcellentCourseWeight());
        double baseScore = (moralNorm * moralW + intellectualNorm * intellectualW + physicalNorm * physicalW
                + extraNorm * extraW + excellentNorm * excellentW) * 100.0;

        // 关键词加分（额外加分，不占权重百分比）
        double keywordScore = calcKeywordScore(metrics, rule);
        // 最终分数 = 基础权重分 + 关键词分
        return baseScore + keywordScore;
    }

    // 权重百分比转小数
    private double toRatio(Double v) {
        return v != null ? v / 100.0 : 0.0;
    }

    // 计算老师设置的关键词匹配分数
    // keywordWeight 表示关键词贡献占总分上限的百分比
    private double calcKeywordScore(PortraitMetrics metrics, TeacherPortraitRule rule) {
        List<String> ruleKeywords = rule.getKeywordList();
        if (ruleKeywords == null || ruleKeywords.isEmpty() || rule.getKeywordBonus() == null || rule.getKeywordBonus() <= 0) {
            return 0.0;
        }
        int matchCount = 0;
        for (String keyword : ruleKeywords) {
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            for (String matched : metrics.matchedKeywords) {
                if (matched.toLowerCase(Locale.ROOT).equals(lowerKeyword)) {
                    matchCount++;
                    break;
                }
            }
        }
        // keywordBonus 是百分比，比如 20 表示每个关键词贡献 20% 的分
        // 总分上限为100，所以每个关键词贡献 = keywordBonus 分
        return matchCount * rule.getKeywordBonus();
    }

    private String[] buildReasonsForRule(PortraitMetrics metrics, TeacherPortraitRule rule) {
        return switch (rule.getTypeKey()) {
            case "all_round" -> new String[]{
                    "三维均分 %.1f / %.1f / %.1f，课程均分 %.1f".formatted(metrics.moralAverage, metrics.intellectualAverage, metrics.physicalAverage, metrics.courseAverage),
                    "优秀课程 %d 门，附加分申请总量 %.1f".formatted(metrics.excellentCourseCount, metrics.approvedExtraScore)
            };
            case "academic" -> new String[]{
                    "智育均分 %.1f，课程均分 %.1f".formatted(metrics.intellectualAverage, metrics.courseAverage),
                    "高分课程 %d 门，近期学业变化 %.1f 分".formatted(metrics.excellentCourseCount, metrics.semesterGrowth)
            };
            case "competition" -> new String[]{
                    "竞赛类关键词命中 %.1f 次，附加分 %.1f".formatted(metrics.competitionWeight, metrics.approvedExtraScore),
                    "学业基础支撑：课程均分 %.1f，智育均分 %.1f".formatted(metrics.courseAverage, metrics.intellectualAverage)
            };
            case "technical" -> new String[]{
                    "技术类关键词命中 %.1f 次，相关课程 %d 门".formatted(metrics.technicalWeight, metrics.technicalSubjectHits),
                    !metrics.techSubjectNames.isEmpty() ? "相关课程：" + String.join("、", metrics.techSubjectNames) : ""
            };
            case "employment" -> new String[]{
                    "实习/项目/证书类关键词命中 %.1f 次".formatted(metrics.employmentWeight),
                    "附加分总量 %.1f，课程均分 %.1f".formatted(metrics.approvedExtraScore, metrics.courseAverage)
            };
            case "civil_service" -> new String[]{
                    "干部/志愿/基层类关键词命中 %.1f 次".formatted(metrics.serviceWeight),
                    "德育均分 %.1f，体能均分 %.1f".formatted(metrics.moralAverage, metrics.physicalAverage)
            };
            case "potential" -> new String[]{
                    "课程均分 %.1f，学期变化 %.1f 分".formatted(metrics.courseAverage, metrics.semesterGrowth),
                    "单项峰值 %.1f，课程样本 %d 门".formatted(metrics.subjectPeakAverage, metrics.courseScoreCount)
            };
            default -> new String[]{"使用自定义规则计算"};
        };
    }

    private String getDefaultTypeName(String typeKey) {
        return switch (typeKey) {
            case "all_round" -> "全面优秀型";
            case "academic" -> "学术升学型";
            case "competition" -> "复合竞赛型";
            case "technical" -> "技术专长型";
            case "employment" -> "就业实践型";
            case "civil_service" -> "稳定考公型";
            case "potential" -> "发展潜力型";
            default -> typeKey;
        };
    }

    private PortraitMetrics buildMetrics(Student student) {
        List<SubjectScoreRAIN> subjectScores = subjectScoreRAINRepository.findByStudentId(student.getId());
        List<MoralEducation> morals = moralEducationService.findByStudentId(student.getId());
        List<IntellectualEducation> intellectuals = intellectualEducationService.findByStudentId(student.getId());
        List<PhysicalEducation> physicals = physicalEducationService.findByStudentId(student.getId());
        List<ExtraScoreApplication> applications = extraScoreApplicationRepository.findByStudentNoOrderByCreatedAtDesc(student.getStudentNo());

        PortraitMetrics metrics = new PortraitMetrics();
        metrics.student = student;
        metrics.subjectScores = subjectScores;
        metrics.morals = morals;
        metrics.intellectuals = intellectuals;
        metrics.physicals = physicals;
        metrics.applications = applications;
        metrics.courseAverage = average(subjectScores.stream().map(SubjectScoreRAIN::getScore).toList());
        metrics.moralAverage = average(morals.stream().map(MoralEducation::getTotalScore).toList());
        metrics.intellectualAverage = average(intellectuals.stream().map(IntellectualEducation::getTotalScore).toList());
        metrics.physicalAverage = average(physicals.stream().map(PhysicalEducation::getTotalScore).toList());
        metrics.courseScoreCount = (int) subjectScores.stream().map(SubjectScoreRAIN::getScore).filter(Objects::nonNull).count();
        metrics.excellentCourseCount = (int) subjectScores.stream().map(SubjectScoreRAIN::getScore).filter(score -> score != null && score >= 90).count();
        metrics.passCourseCount = (int) subjectScores.stream().map(SubjectScoreRAIN::getScore).filter(score -> score != null && score >= 60).count();
        metrics.approvedExtraScore = round1(applications.stream()
                .filter(application -> "approved".equalsIgnoreCase(application.getStatus()))
                .map(ExtraScoreApplication::getExpectedScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        metrics.competitionWeight = weightedKeywordScore(applications, COMPETITION_KEYWORDS);
        metrics.technicalWeight = weightedKeywordScore(applications, TECH_KEYWORDS);
        metrics.employmentWeight = weightedKeywordScore(applications, EMPLOYMENT_KEYWORDS);
        metrics.serviceWeight = weightedKeywordScore(applications, SERVICE_KEYWORDS);
        metrics.classCommittee = Boolean.TRUE.equals(student.getClassCommittee());
        metrics.subjectAverages = buildSubjectAverages(subjectScores);
        metrics.technicalSubjectHits = (int) metrics.subjectAverages.keySet().stream().filter(name -> containsAny(name, TECH_SUBJECT_KEYWORDS)).count();
        metrics.subjectPeakAverage = metrics.subjectAverages.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        metrics.subjectTopGap = buildTopGap(metrics.subjectAverages.values().stream().toList());
        metrics.semesterGrowth = buildSemesterGrowth(subjectScores);
        metrics.balanceStdDev = stdDev(List.of(metrics.moralAverage, metrics.intellectualAverage, metrics.physicalAverage));
        metrics.techSubjectNames = metrics.subjectAverages.entrySet().stream()
                .filter(entry -> containsAny(entry.getKey(), TECH_SUBJECT_KEYWORDS))
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.nullsLast(Double::compareTo)).reversed())
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();
        // 收集所有已匹配的关键词（来自附加分申请）
        List<String> allMatched = new ArrayList<>();
        allMatched.addAll(collectMatchedKeywords(applications, COMPETITION_KEYWORDS));
        allMatched.addAll(collectMatchedKeywords(applications, TECH_KEYWORDS));
        allMatched.addAll(collectMatchedKeywords(applications, EMPLOYMENT_KEYWORDS));
        allMatched.addAll(collectMatchedKeywords(applications, SERVICE_KEYWORDS));
        metrics.matchedKeywords = allMatched;
        return metrics;
    }

    private PortraitCandidate buildAllRoundCandidate(PortraitMetrics metrics) {
        double raw = 8D
                + tierScore(metrics.courseAverage, 90, 24, 85, 18, 80, 12, 75, 6)
                + tierScore(metrics.intellectualAverage, 90, 16, 85, 11, 80, 7, 75, 4)
                + tierScore(metrics.physicalAverage, 90, 12, 80, 8, 70, 4, 60, 2)
                + tierScore(metrics.moralAverage, 90, 10, 80, 6, 70, 3, 60, 1)
                + tierScore(metrics.approvedExtraScore, 20, 12, 10, 8, 5, 4, 1, 1)
                + tierScore(metrics.excellentCourseCount, 5, 10, 3, 6, 1, 3, 0, 0)
                + Math.max(0D, 12D - metrics.balanceStdDev * 1.5D);
        return candidate("all_round", "全面优秀型", raw,
                "三维均分 %.1f / %.1f / %.1f，课程均分 %.1f".formatted(metrics.moralAverage, metrics.intellectualAverage, metrics.physicalAverage, metrics.courseAverage),
                "优秀课程 %d 门，附加分申请总量 %.1f".formatted(metrics.excellentCourseCount, metrics.approvedExtraScore));
    }

    private PortraitCandidate buildAcademicCandidate(PortraitMetrics metrics) {
        double raw = 8D
                + tierScore(metrics.intellectualAverage, 92, 24, 88, 18, 84, 13, 80, 8)
                + tierScore(metrics.courseAverage, 90, 16, 85, 12, 80, 8, 75, 4)
                + tierScore(metrics.excellentCourseCount, 5, 12, 3, 8, 1, 4, 0, 0)
                + tierScore(metrics.subjectPeakAverage, 92, 10, 88, 7, 84, 4, 80, 2)
                + tierScore(metrics.semesterGrowth, 3, 8, 1, 4, 0, 2, -1, 0)
                + tierScore(metrics.subjectAverages.size(), 10, 6, 6, 4, 3, 2, 1, 0);
        return candidate("academic", "学术升学型", raw,
                "智育均分 %.1f，课程均分 %.1f".formatted(metrics.intellectualAverage, metrics.courseAverage),
                "高分课程 %d 门，近期学业变化 %.1f 分".formatted(metrics.excellentCourseCount, metrics.semesterGrowth));
    }

    private PortraitCandidate buildCompetitionCandidate(PortraitMetrics metrics) {
        double raw = 6D
                + Math.min(metrics.competitionWeight * 18D, 48D)
                + tierScore(metrics.approvedExtraScore, 15, 10, 8, 6, 3, 3, 0, 0)
                + tierScore(metrics.intellectualAverage, 85, 6, 80, 4, 75, 2, 70, 0)
                + tierScore(metrics.courseAverage, 85, 4, 80, 3, 75, 1, 70, 0);
        return candidate("competition", "复合竞赛型", raw,
                "竞赛类关键词命中 %.1f 次，附加分 %.1f".formatted(metrics.competitionWeight, metrics.approvedExtraScore),
                "学业基础支撑：课程均分 %.1f，智育均分 %.1f".formatted(metrics.courseAverage, metrics.intellectualAverage));
    }

    private PortraitCandidate buildTechnicalCandidate(PortraitMetrics metrics) {
        double raw = 6D
                + Math.min(metrics.technicalWeight * 18D, 48D)
                + Math.min(metrics.technicalSubjectHits * 8D, 24D)
                + tierScore(metrics.courseAverage, 85, 6, 80, 4, 75, 2, 70, 0)
                + tierScore(metrics.intellectualAverage, 85, 6, 80, 4, 75, 2, 70, 0);
        List<String> reasons = new ArrayList<>();
        reasons.add("技术类关键词命中 %.1f 次，相关课程 %d 门".formatted(metrics.technicalWeight, metrics.technicalSubjectHits));
        if (!metrics.techSubjectNames.isEmpty()) {
            reasons.add("相关课程：" + String.join("、", metrics.techSubjectNames));
        }
        return candidate("technical", "技术专长型", raw, reasons.toArray(new String[0]));
    }

    private PortraitCandidate buildEmploymentCandidate(PortraitMetrics metrics) {
        double raw = 6D
                + Math.min(metrics.employmentWeight * 18D, 48D)
                + tierScore(metrics.approvedExtraScore, 15, 8, 8, 4, 3, 2, 0, 0)
                + tierScore(metrics.courseAverage, 78, 5, 72, 3, 65, 1, 60, 0);
        return candidate("employment", "就业实践型", raw,
                "实习/项目/证书类关键词命中 %.1f 次".formatted(metrics.employmentWeight),
                "附加分总量 %.1f，课程均分 %.1f".formatted(metrics.approvedExtraScore, metrics.courseAverage));
    }

    private PortraitCandidate buildCivilServiceCandidate(PortraitMetrics metrics) {
        double raw = 6D
                + Math.min(metrics.serviceWeight * 18D, 48D)
                + tierScore(metrics.moralAverage, 90, 12, 85, 8, 80, 4, 75, 2)
                + tierScore(metrics.physicalAverage, 85, 8, 75, 4, 65, 2, 60, 0)
                + tierScore(metrics.passCourseCount, 8, 6, 5, 4, 3, 2, 1, 0);
        return candidate("civil_service", "稳定考公型", raw,
                "干部/志愿/基层类关键词命中 %.1f 次".formatted(metrics.serviceWeight),
                "德育均分 %.1f，体能均分 %.1f".formatted(metrics.moralAverage, metrics.physicalAverage));
    }

    private PortraitCandidate buildPotentialCandidate(PortraitMetrics metrics) {
        double raw = 8D;
        raw += metrics.courseAverage >= 85 ? 2D : metrics.courseAverage >= 70 ? 14D : metrics.courseAverage >= 60 ? 18D : 24D;
        raw += metrics.semesterGrowth >= 6 ? 24D : metrics.semesterGrowth >= 3 ? 18D : metrics.semesterGrowth >= 1 ? 12D : metrics.semesterGrowth >= 0 ? 8D : 4D;
        raw += metrics.subjectTopGap >= 8 ? 8D : metrics.subjectTopGap >= 5 ? 5D : 2D;
        raw += metrics.courseScoreCount < 6 ? 8D : 0D;
        if (metrics.courseAverage >= 60 && metrics.courseAverage < 85) {
            raw += 6D;
        }
        if (metrics.subjectPeakAverage >= 90 && metrics.subjectTopGap >= 5) {
            raw += 6D;
        }
        return candidate("potential", "发展潜力型", raw,
                "课程均分 %.1f，学期变化 %.1f 分".formatted(metrics.courseAverage, metrics.semesterGrowth),
                "单项峰值 %.1f，课程样本 %d 门".formatted(metrics.subjectPeakAverage, metrics.courseScoreCount));
    }

    private PortraitCandidate candidate(String key, String name, double rawScore, String... reasons) {
        PortraitCandidate candidate = new PortraitCandidate();
        candidate.typeKey = key;
        candidate.typeName = name;
        candidate.rawScore = rawScore;
        candidate.reasons = List.of(reasons);
        return candidate;
    }

    private List<String> buildOverallReasons(PortraitMetrics metrics, List<StudentPortraitMatchResultDTO.PortraitMatchItemDTO> items) {
        List<String> reasons = new ArrayList<>();
        if (!items.isEmpty()) {
            StudentPortraitMatchResultDTO.PortraitMatchItemDTO top = items.get(0);
            reasons.add("主画像：%s（%.1f%%）".formatted(top.getTypeName(), top.getPercentage() == null ? 0D : top.getPercentage()));
        }
        reasons.add("课程均分 %.1f，智育均分 %.1f，德育均分 %.1f，体育均分 %.1f".formatted(metrics.courseAverage, metrics.intellectualAverage, metrics.moralAverage, metrics.physicalAverage));
        reasons.add("竞赛/技术/就业/服务关键词权重：%.1f / %.1f / %.1f / %.1f".formatted(metrics.competitionWeight, metrics.technicalWeight, metrics.employmentWeight, metrics.serviceWeight));
        return reasons;
    }

    private List<String> buildDataSources(PortraitMetrics metrics) {
        List<String> sources = new ArrayList<>();
        if (!metrics.subjectScores.isEmpty()) {
            sources.add("课程成绩");
        }
        if (!metrics.morals.isEmpty() || !metrics.intellectuals.isEmpty() || !metrics.physicals.isEmpty()) {
            sources.add("三维成绩");
        }
        if (!metrics.applications.isEmpty()) {
            sources.add("附加分申请");
        }
        if (metrics.student.getClassCommittee() != null) {
            sources.add("班委状态");
        }
        return sources;
    }

    private Map<String, Object> buildFeatureSnapshot(PortraitMetrics metrics, List<StudentPortraitMatchResultDTO.PortraitMatchItemDTO> items) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("courseAverage", round1(metrics.courseAverage));
        snapshot.put("moralAverage", round1(metrics.moralAverage));
        snapshot.put("intellectualAverage", round1(metrics.intellectualAverage));
        snapshot.put("physicalAverage", round1(metrics.physicalAverage));
        snapshot.put("courseScoreCount", metrics.courseScoreCount);
        snapshot.put("excellentCourseCount", metrics.excellentCourseCount);
        snapshot.put("passCourseCount", metrics.passCourseCount);
        snapshot.put("approvedExtraScore", round1(metrics.approvedExtraScore));
        snapshot.put("competitionWeight", round1(metrics.competitionWeight));
        snapshot.put("technicalWeight", round1(metrics.technicalWeight));
        snapshot.put("employmentWeight", round1(metrics.employmentWeight));
        snapshot.put("serviceWeight", round1(metrics.serviceWeight));
        snapshot.put("semesterGrowth", round1(metrics.semesterGrowth));
        snapshot.put("subjectPeakAverage", round1(metrics.subjectPeakAverage));
        snapshot.put("subjectTopGap", round1(metrics.subjectTopGap));
        snapshot.put("items", items);
        return snapshot;
    }

    private Map<String, Double> buildSubjectAverages(List<SubjectScoreRAIN> subjectScores) {
        Map<String, List<Double>> grouped = subjectScores.stream()
                .filter(score -> score.getScore() != null)
                .filter(score -> score.getSubject() != null && score.getSubject().getSubjectName() != null)
                .collect(Collectors.groupingBy(score -> score.getSubject().getSubjectName(), LinkedHashMap::new, Collectors.mapping(SubjectScoreRAIN::getScore, Collectors.toList())));
        return grouped.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> average(entry.getValue()), (left, right) -> left, LinkedHashMap::new));
    }

    private double buildTopGap(List<Double> values) {
        List<Double> sorted = values.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList();
        if (sorted.size() < 2) {
            return sorted.isEmpty() ? 0D : sorted.get(0);
        }
        return round1(sorted.get(0) - sorted.get(1));
    }

    private double buildSemesterGrowth(List<SubjectScoreRAIN> subjectScores) {
        Map<String, List<Double>> semesterMap = subjectScores.stream()
                .filter(score -> score.getSemester() != null && score.getScore() != null)
                .collect(Collectors.groupingBy(SubjectScoreRAIN::getSemester, LinkedHashMap::new, Collectors.mapping(SubjectScoreRAIN::getScore, Collectors.toList())));
        if (semesterMap.size() < 2) {
            return 0D;
        }
        List<String> semesters = new ArrayList<>(semesterMap.keySet());
        semesters.sort(String::compareTo);
        return round1(average(semesterMap.get(semesters.get(semesters.size() - 1))) - average(semesterMap.get(semesters.get(0))));
    }

    private double weightedKeywordScore(List<ExtraScoreApplication> applications, List<String> keywords) {
        double score = 0D;
        for (ExtraScoreApplication application : applications) {
            if (!containsAny(applicationText(application), keywords)) {
                continue;
            }
            score += statusWeight(application.getStatus());
        }
        return round1(score);
    }

    private List<String> collectMatchedKeywords(List<ExtraScoreApplication> applications, List<String> keywords) {
        List<String> matched = new ArrayList<>();
        for (ExtraScoreApplication application : applications) {
            String text = applicationText(application);
            for (String keyword : keywords) {
                if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                    matched.add(keyword);
                }
            }
        }
        return matched;
    }

    private String applicationText(ExtraScoreApplication application) {
        return Stream.of(
                        application.getTemplateName(),
                        application.getGroupName(),
                        application.getItemName(),
                        application.getLevelName(),
                        application.getDetailDesc(),
                        application.getProofText(),
                        application.getCertName(),
                        application.getAuthorLabel(),
                        application.getScoreType()
                )
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }

    private double statusWeight(String status) {
        if (status == null) {
            return 0.4D;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> 1D;
            case "pending" -> 0.6D;
            case "rejected" -> 0.2D;
            default -> 0.4D;
        };
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(keyword -> normalized.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private double average(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double stdDev(List<Double> values) {
        List<Double> filtered = values.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return 0D;
        }
        double mean = filtered.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double variance = filtered.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0D);
        return round1(Math.sqrt(variance));
    }

    private double tierScore(double value, double firstThreshold, double firstScore, double secondThreshold, double secondScore, double thirdThreshold, double thirdScore, double fourthThreshold, double fourthScore) {
        if (value >= firstThreshold) return firstScore;
        if (value >= secondThreshold) return secondScore;
        if (value >= thirdThreshold) return thirdScore;
        if (value >= fourthThreshold) return fourthScore;
        return 0D;
    }

    private double tierScore(int value, int firstThreshold, double firstScore, int secondThreshold, double secondScore, int thirdThreshold, double thirdScore, int fourthThreshold, double fourthScore) {
        if (value >= firstThreshold) return firstScore;
        if (value >= secondThreshold) return secondScore;
        if (value >= thirdThreshold) return thirdScore;
        if (value >= fourthThreshold) return fourthScore;
        return 0D;
    }

    private double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private String buildSummary(List<StudentPortraitMatchResultDTO.PortraitMatchItemDTO> items) {
        if (items.isEmpty()) {
            return "当前暂无足够数据，已按基础画像进行均衡分配。";
        }
        if (items.size() == 1) {
            return "当前学生以%s为主。".formatted(items.get(0).getTypeName());
        }
        return "当前学生以%s为主，兼具%s等特征。".formatted(items.get(0).getTypeName(), items.get(1).getTypeName());
    }

    private Optional<Student> resolveStudent(String studentNo, Long userId) {
        if (studentNo != null && !studentNo.isBlank()) {
            return studentService.findByStudentNo(studentNo.trim());
        }
        if (userId != null) {
            return studentService.findByUserId(userId);
        }
        return Optional.empty();
    }

    private static class PortraitCandidate {
        private String typeKey;
        private String typeName;
        private double rawScore;
        private List<String> reasons = List.of();

        private StudentPortraitMatchResultDTO.PortraitMatchItemDTO toDto() {
            StudentPortraitMatchResultDTO.PortraitMatchItemDTO item = new StudentPortraitMatchResultDTO.PortraitMatchItemDTO();
            item.setTypeKey(typeKey);
            item.setTypeName(typeName);
            item.setRawScore(Math.round(rawScore * 10D) / 10D);
            item.setReasons(new ArrayList<>(reasons));
            return item;
        }
    }

    private static class PortraitMetrics {
        private Student student;
        private List<SubjectScoreRAIN> subjectScores = List.of();
        private List<MoralEducation> morals = List.of();
        private List<IntellectualEducation> intellectuals = List.of();
        private List<PhysicalEducation> physicals = List.of();
        private List<ExtraScoreApplication> applications = List.of();
        private double courseAverage;
        private double moralAverage;
        private double intellectualAverage;
        private double physicalAverage;
        private int courseScoreCount;
        private int excellentCourseCount;
        private int passCourseCount;
        private double approvedExtraScore;
        private double competitionWeight;
        private double technicalWeight;
        private double employmentWeight;
        private double serviceWeight;
        private boolean classCommittee;
        private Map<String, Double> subjectAverages = new LinkedHashMap<>();
        private List<String> techSubjectNames = List.of();
        private double subjectPeakAverage;
        private double subjectTopGap;
        private double semesterGrowth;
        private double balanceStdDev;
        private int technicalSubjectHits;
        private List<String> matchedKeywords = List.of();
    }
}
