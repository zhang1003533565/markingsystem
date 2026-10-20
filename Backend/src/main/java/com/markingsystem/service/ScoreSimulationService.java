package com.markingsystem.service;

import com.markingsystem.dto.ScoreReverseRequest;
import com.markingsystem.dto.ScoreSimulateRequest;
import com.markingsystem.entity.CompositeScoreSnapshot;
import com.markingsystem.entity.CompositeScoreWeight;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.repository.CompositeScoreSnapshotRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.MoralEducationRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 综测成绩模拟器：在学生端做"假设调整"后的综合总分与班级名次推演，纯只读、绝不落库。
 *
 * <p>算分口径完全复用 {@link CompositeScoreCalculator#computeBreakdown}，与管理员刷新
 * （{@code AdminCompositeScoreService.buildRow}）保持单一事实来源；名次基于最近一次
 * {@link CompositeScoreSnapshot} 班级快照中的同学总分推演。</p>
 */
@Service
public class ScoreSimulationService {

    private static final double EPS = 1e-9;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CompositeScoreWeightService compositeScoreWeightService;

    @Autowired
    private CompositeSubjectConfigService compositeSubjectConfigService;

    @Autowired
    private CompositePhysicalFullScoreRuleService compositePhysicalFullScoreRuleService;

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    @Autowired
    private MoralEducationRepository moralEducationRepository;

    @Autowired
    private ExtraScoreApplicationRepository extraScoreApplicationRepository;

    @Autowired
    private ExtraScoreEffectiveScoreService effectiveScoreService;

    @Autowired
    private CompositeScoreSnapshotRepository compositeScoreSnapshotRepository;

    /** 正向模拟：应用调整，返回调整前后总分与名次。 */
    @Transactional(readOnly = true)
    public Map<String, Object> simulate(String studentNo, ScoreSimulateRequest request) {
        SimContext ctx = loadContext(studentNo, request == null ? null : request.getAcademicYear());
        List<CompositeScoreSnapshot> classmates = ctx.classmates;

        CompositeScoreCalculator.RawComponents beforeRaw = ctx.buildRaw();
        CompositeScoreCalculator.CompositeBreakdown beforeBreakdown =
                CompositeScoreCalculator.computeBreakdown(beforeRaw, ctx.weight);
        double beforeTotal = round(beforeBreakdown.totalScore);
        int beforeRank = computeRank(beforeTotal, ctx.studentNo, classmates);

        if (request != null && request.getAdjustments() != null) {
            for (ScoreSimulateRequest.Adjustment adjustment : request.getAdjustments()) {
                applyAdjustment(ctx, adjustment);
            }
        }

        CompositeScoreCalculator.RawComponents afterRaw = ctx.buildRaw();
        CompositeScoreCalculator.CompositeBreakdown afterBreakdown =
                CompositeScoreCalculator.computeBreakdown(afterRaw, ctx.weight);
        double afterTotal = round(afterBreakdown.totalScore);
        int afterRank = computeRank(afterTotal, ctx.studentNo, classmates);

        Map<String, Object> result = baseMeta(ctx);
        result.put("before", componentMap(beforeRaw, beforeBreakdown, beforeRank, ctx.classSize));
        result.put("after", componentMap(afterRaw, afterBreakdown, afterRank, ctx.classSize));
        result.put("delta", round(afterTotal - beforeTotal));
        result.put("rankChange", beforeRank - afterRank);
        result.put("courses", courseList(ctx));
        return result;
    }

    /** 反向计算：给定目标"班级前 P%"，反解需增加的附加分或需达到的课程分数。 */
    @Transactional(readOnly = true)
    public Map<String, Object> reverse(String studentNo, ScoreReverseRequest request) {
        SimContext ctx = loadContext(studentNo, request == null ? null : request.getAcademicYear());
        List<CompositeScoreSnapshot> classmates = ctx.classmates;

        double currentTotal = round(CompositeScoreCalculator.computeBreakdown(ctx.buildRaw(), ctx.weight).totalScore);
        int currentRank = computeRank(currentTotal, ctx.studentNo, classmates);

        double percentile = request == null || request.getTargetPercentile() == null
                ? 0D : request.getTargetPercentile();
        if (percentile <= 0D || percentile > 100D) {
            throw new IllegalArgumentException("目标百分比需在 (0, 100] 之间");
        }
        int targetRank = Math.max(1, (int) Math.ceil(ctx.classSize * percentile / 100D));

        // 目标分：为进入前 targetRank 名，需严格超过同学（不含本人）降序第 targetRank 名的分数。
        // 并列时按学号升序仍可能排在后面，故加 0.01 余量，确保反解结果真正达标。
        List<Double> peerTotalsDesc = classmates.stream()
                .filter(snapshot -> !Objects.equals(snapshot.getStudentNo(), ctx.studentNo))
                .map(snapshot -> round(snapshot.getTotalScore() == null ? 0D : snapshot.getTotalScore()))
                .sorted((left, right) -> Double.compare(right, left))
                .toList();
        Double bar = targetRank - 1 < peerTotalsDesc.size() ? peerTotalsDesc.get(targetRank - 1) : null;
        double threshold = bar == null ? 0D : round(bar);
        double targetTotal = bar == null ? 0D : bar + 0.01;
        double gap = Math.max(0D, round(targetTotal - currentTotal));

        Map<String, Object> result = baseMeta(ctx);
        result.put("currentTotal", currentTotal);
        result.put("currentRank", currentRank);
        result.put("targetPercentile", percentile);
        result.put("targetRank", targetRank);
        result.put("thresholdTotal", threshold);
        result.put("gap", gap);

        String solveFor = request == null || request.getSolveFor() == null
                ? "" : request.getSolveFor().trim().toUpperCase(Locale.ROOT);
        if (gap <= EPS) {
            // 已进入目标区间，无需调整。
            result.put("solveFor", solveFor);
            result.put("feasible", true);
            result.put("message", "当前成绩已进入目标区间，无需调整");
            result.put("projectedAfter", componentMap(ctx.buildRaw(),
                    CompositeScoreCalculator.computeBreakdown(ctx.buildRaw(), ctx.weight),
                    currentRank, ctx.classSize));
            return result;
        }

        if ("EXTRA".equals(solveFor)) {
            solveExtra(ctx, result, request.getCategory(), gap);
        } else if ("COURSE".equals(solveFor)) {
            solveCourse(ctx, result, request.getSubjectId(), gap);
        } else {
            throw new IllegalArgumentException("solveFor 必须为 EXTRA 或 COURSE");
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 反向计算：附加分 / 课程分
    // ------------------------------------------------------------------

    private void solveExtra(SimContext ctx, Map<String, Object> result, String category, double gap) {
        String normalized = normalizeCategory(category);
        if (normalized == null) {
            throw new IllegalArgumentException("附加分类型必须为 MORAL / INTELLECTUAL / PHYSICAL");
        }
        double factor = CompositeScoreCalculator.resolveFactor(extraWeight(ctx.weight, normalized));
        result.put("solveFor", "EXTRA");
        result.put("category", normalized);
        if (factor <= EPS) {
            result.put("feasible", false);
            result.put("message", "该类别附加分权重为 0，无法通过增加附加分提升名次");
            return;
        }
        double neededExtra = round(gap / factor);
        SimContext probe = ctx.copy();
        applyExtraDelta(probe, normalized, neededExtra);
        CompositeScoreCalculator.RawComponents raw = probe.buildRaw();
        CompositeScoreCalculator.CompositeBreakdown breakdown =
                CompositeScoreCalculator.computeBreakdown(raw, probe.weight);
        double projectedTotal = round(breakdown.totalScore);
        int projectedRank = computeRank(projectedTotal, probe.studentNo, probe.classmates);

        result.put("feasible", true);
        result.put("neededExtra", neededExtra);
        result.put("projectedAfter", componentMap(raw, breakdown, projectedRank, probe.classSize));
    }

    private void solveCourse(SimContext ctx, Map<String, Object> result, Long subjectId, double gap) {
        CourseItem target = ctx.courses.stream()
                .filter(course -> Objects.equals(course.subjectId, subjectId))
                .findFirst()
                .orElse(null);
        result.put("solveFor", "COURSE");
        if (target == null) {
            result.put("feasible", false);
            result.put("message", "未找到该课程或该课程不在当前学年成绩内");
            return;
        }
        result.put("subjectId", target.subjectId);
        result.put("subjectName", target.subjectName);
        result.put("currentScore", target.score == null ? 0D : round(target.score));

        String category = target.category;
        double factor;
        int count;
        if ("INTELLECTUAL".equals(category)) {
            factor = CompositeScoreCalculator.resolveFactor(ctx.weight.getIntellectualBaseWeight());
            count = (int) ctx.courses.stream()
                    .filter(course -> "INTELLECTUAL".equals(course.category) && course.score != null).count();
        } else if ("PHYSICAL".equals(category)) {
            if (ctx.physicalFullScoreEnabled) {
                result.put("feasible", false);
                result.put("message", "该班体育基础分按满分规则计算，改课程分不影响综测");
                return;
            }
            factor = CompositeScoreCalculator.resolveFactor(ctx.weight.getPhysicalBaseWeight());
            count = (int) ctx.courses.stream()
                    .filter(course -> "PHYSICAL".equals(course.category) && course.score != null).count();
        } else {
            result.put("feasible", false);
            result.put("message", "该课程不计入综测（归类为" + categoryLabel(category) + "），改分不影响综合总分");
            return;
        }

        if (factor <= EPS || count <= 0) {
            result.put("feasible", false);
            result.put("message", "该类别基础分权重为 0 或无有效课程，无法通过改课程分提升名次");
            return;
        }

        double currentScore = target.score == null ? 0D : target.score;
        double requiredDelta = gap * count / factor;
        double requiredScore = currentScore + requiredDelta;
        boolean capped = requiredScore > 100D + EPS;
        double achievableScore = Math.min(100D, requiredScore);

        SimContext probe = ctx.copy();
        applyCourseScore(probe, subjectId, achievableScore);
        CompositeScoreCalculator.RawComponents raw = probe.buildRaw();
        CompositeScoreCalculator.CompositeBreakdown breakdown =
                CompositeScoreCalculator.computeBreakdown(raw, probe.weight);
        double projectedTotal = round(breakdown.totalScore);
        int projectedRank = computeRank(projectedTotal, probe.studentNo, probe.classmates);

        result.put("feasible", !capped);
        result.put("requiredScore", round(requiredScore));
        result.put("requiredDelta", round(requiredDelta));
        result.put("achievableScore", round(achievableScore));
        result.put("cappedAt100", capped);
        if (capped) {
            result.put("remainingGap", round(Math.max(0D, thresholdOf(result) - projectedTotal)));
            result.put("message", "即使该课程提到满分 100 仍不足以进入目标区间，可结合增加附加分达成");
        }
        result.put("projectedAfter", componentMap(raw, breakdown, projectedRank, probe.classSize));
    }

    private double thresholdOf(Map<String, Object> result) {
        Object value = result.get("thresholdTotal");
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    // ------------------------------------------------------------------
    // 调整应用
    // ------------------------------------------------------------------

    private void applyAdjustment(SimContext ctx, ScoreSimulateRequest.Adjustment adjustment) {
        if (adjustment == null || adjustment.getType() == null) {
            return;
        }
        String type = adjustment.getType().trim().toUpperCase(Locale.ROOT);
        if ("COURSE".equals(type)) {
            applyCourseScore(ctx, adjustment.getSubjectId(), clampScore(adjustment.getNewScore()));
        } else if ("EXTRA".equals(type)) {
            String category = normalizeCategory(adjustment.getCategory());
            if (category != null && adjustment.getDelta() != null) {
                applyExtraDelta(ctx, category, adjustment.getDelta());
            }
        }
    }

    private void applyCourseScore(SimContext ctx, Long subjectId, Double newScore) {
        if (subjectId == null || newScore == null) {
            return;
        }
        ctx.courses.stream()
                .filter(course -> Objects.equals(course.subjectId, subjectId))
                .findFirst()
                .ifPresent(course -> course.score = newScore);
    }

    private void applyExtraDelta(SimContext ctx, String category, double delta) {
        switch (category) {
            case "MORAL" -> ctx.moralExtra += delta;
            case "INTELLECTUAL" -> ctx.intellectualExtra += delta;
            case "PHYSICAL" -> ctx.physicalExtra += delta;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // 名次与结果组装
    // ------------------------------------------------------------------

    private int computeRank(double total, String selfStudentNo, List<CompositeScoreSnapshot> classmates) {
        int better = 0;
        for (CompositeScoreSnapshot snapshot : classmates) {
            if (Objects.equals(snapshot.getStudentNo(), selfStudentNo)) {
                continue;
            }
            double peerTotal = round(snapshot.getTotalScore() == null ? 0D : snapshot.getTotalScore());
            if (peerTotal > total + EPS) {
                better++;
            } else if (Math.abs(peerTotal - total) <= EPS
                    && compareStudentNo(snapshot.getStudentNo(), selfStudentNo) < 0) {
                better++;
            }
        }
        return better + 1;
    }

    private int compareStudentNo(String left, String right) {
        String l = left == null ? "" : left;
        String r = right == null ? "" : right;
        return l.compareTo(r);
    }

    private Map<String, Object> baseMeta(SimContext ctx) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("academicYear", ctx.academicYear);
        meta.put("className", ctx.className);
        meta.put("classSize", ctx.classSize);
        return meta;
    }

    private Map<String, Object> componentMap(
            CompositeScoreCalculator.RawComponents raw,
            CompositeScoreCalculator.CompositeBreakdown breakdown,
            int rank,
            int classSize
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("moralBaseScore", round(raw.moralBase()));
        map.put("intellectualBaseScore", round(raw.intellectualBase()));
        map.put("physicalBaseScore", round(raw.physicalBase()));
        map.put("moralExtraScore", round(raw.moralExtra()));
        map.put("intellectualExtraScore", round(raw.intellectualExtra()));
        map.put("physicalExtraScore", round(raw.physicalExtra()));
        map.put("weightedMoralBaseScore", round(breakdown.weightedMoralBase));
        map.put("weightedIntellectualBaseScore", round(breakdown.weightedIntellectualBase));
        map.put("weightedPhysicalBaseScore", round(breakdown.weightedPhysicalBase));
        map.put("weightedMoralExtraScore", round(breakdown.weightedMoralExtra));
        map.put("weightedIntellectualExtraScore", round(breakdown.weightedIntellectualExtra));
        map.put("weightedPhysicalExtraScore", round(breakdown.weightedPhysicalExtra));
        map.put("totalScore", round(breakdown.totalScore));
        map.put("rank", rank);
        map.put("classSize", classSize);
        map.put("percentile", classSize <= 0 ? 0D : round(rank * 100.0 / classSize));
        return map;
    }

    private List<Map<String, Object>> courseList(SimContext ctx) {
        List<Map<String, Object>> courses = new ArrayList<>();
        for (CourseItem course : ctx.courses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subjectId", course.subjectId);
            item.put("subjectName", course.subjectName);
            item.put("category", course.category);
            item.put("categoryLabel", categoryLabel(course.category));
            item.put("score", course.score == null ? null : round(course.score));
            courses.add(item);
        }
        return courses;
    }

    private String categoryLabel(String category) {
        return switch (category == null ? "" : category) {
            case "MORAL" -> "德育";
            case "INTELLECTUAL" -> "智育";
            case "PHYSICAL" -> "体育";
            case "EXCLUDED" -> "不计入综测";
            default -> "未分类";
        };
    }

    private Double extraWeight(CompositeScoreWeight weight, String category) {
        return switch (category) {
            case "MORAL" -> weight.getMoralExtraWeight();
            case "PHYSICAL" -> weight.getPhysicalExtraWeight();
            default -> weight.getIntellectualExtraWeight();
        };
    }

    // ------------------------------------------------------------------
    // 数据装载
    // ------------------------------------------------------------------

    private SimContext loadContext(String studentNo, String academicYearRaw) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new IllegalArgumentException("缺少学生标识");
        }
        Student student = studentService.findByStudentNo(studentNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("未找到学生信息"));

        String year = academicYearRaw == null ? "" : academicYearRaw.trim();
        if (year.isBlank()) {
            year = resolveLatestYear(student.getStudentNo());
        }
        final String effectiveYear = year;

        String className = student.getClassName() == null ? "" : student.getClassName().trim();
        CompositeScoreWeight weight = compositeScoreWeightService.getCurrentWeight();
        Map<Long, String> categoryMap = compositeSubjectConfigService.getCategoryMapByClassName(effectiveYear, className);
        boolean physicalFullScoreEnabled = compositePhysicalFullScoreRuleService.isFullScoreEnabled(effectiveYear, className);

        SimContext ctx = new SimContext();
        ctx.studentNo = student.getStudentNo();
        ctx.academicYear = effectiveYear;
        ctx.className = className;
        ctx.weight = weight;
        ctx.physicalFullScoreEnabled = physicalFullScoreEnabled;

        for (SubjectScoreRAIN score : subjectScoreRAINRepository
                .findByStudentIdInWithDetails(List.of(student.getId())).stream()
                .filter(item -> matchesAcademicYear(item.getSemester(), effectiveYear))
                .toList()) {
            String category = score.getSubjectId() == null ? null : categoryMap.get(score.getSubjectId());
            if (category == null) {
                category = "UNKNOWN";
            }
            String subjectName = score.getSubject() == null ? "" : score.getSubject().getSubjectName();
            ctx.courses.add(new CourseItem(score.getSubjectId(), subjectName, category, score.getScore()));
        }

        ctx.moralBase = moralEducationRepository.findByStudentIdIn(List.of(student.getId())).stream()
                .filter(moral -> matchesAcademicYear(resolveMoralYear(moral), effectiveYear))
                .map(MoralEducation::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        List<ExtraScoreApplication> applications = extraScoreApplicationRepository
                .findByStudentNoInAndStatus(List.of(student.getStudentNo()), "approved").stream()
                .filter(application -> matchesAcademicYear(resolveApplicationYear(application), effectiveYear))
                .toList();
        Map<Long, Double> effectiveScores = effectiveScoreService.resolveEffectiveSignedScores(applications);
        for (ExtraScoreApplication application : applications) {
            double value = effectiveScores.getOrDefault(application.getId(), 0D);
            String scoreType = application.getScoreType() == null
                    ? "" : application.getScoreType().trim().toUpperCase(Locale.ROOT);
            switch (scoreType) {
                case "MORAL" -> ctx.moralExtra += value;
                case "INTELLECTUAL" -> ctx.intellectualExtra += value;
                case "PHYSICAL" -> ctx.physicalExtra += value;
                default -> {
                }
            }
        }

        ctx.classmates = year.isBlank() || className.isBlank()
                ? List.of()
                : compositeScoreSnapshotRepository.findByAcademicYearAndClassNameOrderByRankNoAscStudentNoAsc(year, className);
        ctx.classSize = (int) ctx.classmates.stream()
                .filter(snapshot -> !Objects.equals(snapshot.getStudentNo(), ctx.studentNo))
                .count() + 1;
        return ctx;
    }

    private String resolveLatestYear(String studentNo) {
        return compositeScoreSnapshotRepository.findLatestByStudentNo(studentNo).stream()
                .findFirst()
                .map(CompositeScoreSnapshot::getAcademicYear)
                .orElse("");
    }

    // ------------------------------------------------------------------
    // 工具方法（与 AdminCompositeScoreService 保持同样的匹配口径）
    // ------------------------------------------------------------------

    private boolean matchesAcademicYear(String source, String academicYear) {
        if (academicYear == null || academicYear.isBlank()) {
            return true;
        }
        return source != null && source.contains(academicYear);
    }

    private String resolveMoralYear(MoralEducation moral) {
        if (moral == null) {
            return null;
        }
        if (moral.getAcademicYear() != null && !moral.getAcademicYear().isBlank()) {
            return moral.getAcademicYear();
        }
        return moral.getSemester();
    }

    private String resolveApplicationYear(ExtraScoreApplication application) {
        if (application.getAcademicYear() != null && !application.getAcademicYear().isBlank()) {
            return application.getAcademicYear();
        }
        if (application.getSubmissionStartTime() != null) {
            return String.valueOf(application.getSubmissionStartTime());
        }
        if (application.getAwardTime() != null) {
            return String.valueOf(application.getAwardTime());
        }
        if (application.getCreatedAt() != null) {
            return String.valueOf(application.getCreatedAt());
        }
        return "";
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MORAL", "INTELLECTUAL", "PHYSICAL" -> normalized;
            default -> null;
        };
    }

    private Double clampScore(Double score) {
        if (score == null) {
            return null;
        }
        return Math.max(0D, Math.min(100D, score));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // 内部数据结构
    // ------------------------------------------------------------------

    private static final class CourseItem {
        private final Long subjectId;
        private final String subjectName;
        private final String category;
        private Double score;

        private CourseItem(Long subjectId, String subjectName, String category, Double score) {
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.category = category;
            this.score = score;
        }
    }

    private final class SimContext {
        private String studentNo;
        private String academicYear;
        private String className;
        private CompositeScoreWeight weight;
        private boolean physicalFullScoreEnabled;
        private double moralBase;
        private double moralExtra;
        private double intellectualExtra;
        private double physicalExtra;
        private final List<CourseItem> courses = new ArrayList<>();
        private List<CompositeScoreSnapshot> classmates = List.of();
        private int classSize = 1;

        private CompositeScoreCalculator.RawComponents buildRaw() {
            double intellectualSum = 0D;
            int intellectualCount = 0;
            double physicalSum = 0D;
            int physicalCount = 0;
            for (CourseItem course : courses) {
                if (course.score == null) {
                    continue;
                }
                if ("INTELLECTUAL".equals(course.category)) {
                    intellectualSum += course.score;
                    intellectualCount++;
                } else if ("PHYSICAL".equals(course.category)) {
                    physicalSum += course.score;
                    physicalCount++;
                }
            }
            double intellectualBase = intellectualCount > 0 ? intellectualSum / intellectualCount : 0D;
            Double physicalAverage = physicalCount > 0 ? physicalSum / physicalCount : null;
            double physicalBase = CompositeScoreCalculator.resolvePhysicalBaseScore(
                    physicalAverage == null ? 0D : physicalAverage,
                    physicalFullScoreEnabled
            );
            return new CompositeScoreCalculator.RawComponents(
                    moralBase, intellectualBase, physicalBase,
                    moralExtra, intellectualExtra, physicalExtra
            );
        }

        /** 深拷贝课程与附加分状态，供反向计算试算而不污染原始上下文。 */
        private SimContext copy() {
            SimContext clone = new SimContext();
            clone.studentNo = this.studentNo;
            clone.academicYear = this.academicYear;
            clone.className = this.className;
            clone.weight = this.weight;
            clone.physicalFullScoreEnabled = this.physicalFullScoreEnabled;
            clone.moralBase = this.moralBase;
            clone.moralExtra = this.moralExtra;
            clone.intellectualExtra = this.intellectualExtra;
            clone.physicalExtra = this.physicalExtra;
            for (CourseItem course : this.courses) {
                clone.courses.add(new CourseItem(course.subjectId, course.subjectName, course.category, course.score));
            }
            clone.classmates = this.classmates;
            clone.classSize = this.classSize;
            return clone;
        }
    }
}
