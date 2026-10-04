package com.markingsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.CompositeScoreRefreshTaskDTO;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.CompositeScoreWeight;
import com.markingsystem.entity.CompositeScoreRefreshLog;
import com.markingsystem.entity.CompositeScoreSnapshot;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.IntellectualEducation;
import com.markingsystem.entity.Major;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.PhysicalEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CompositeScoreSnapshotRepository;
import com.markingsystem.repository.CompositeScoreRefreshLogRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.IntellectualEducationRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.MoralEducationRepository;
import com.markingsystem.repository.PhysicalEducationRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminCompositeScoreService {

    @Autowired
    private StudentService studentService;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    @Autowired
    private ExtraScoreApplicationRepository extraScoreApplicationRepository;

    @Autowired
    private ExtraScoreEffectiveScoreService effectiveScoreService;

    @Autowired
    private MoralEducationRepository moralEducationRepository;

    @Autowired
    private IntellectualEducationRepository intellectualEducationRepository;

    @Autowired
    private PhysicalEducationRepository physicalEducationRepository;

    @Autowired
    private CompositeScoreWeightService compositeScoreWeightService;

    @Autowired
    private CompositePhysicalFullScoreRuleService compositePhysicalFullScoreRuleService;

    @Autowired
    private CompositeSubjectConfigService compositeSubjectConfigService;

    @Autowired
    private CompositeScoreSnapshotRepository compositeScoreSnapshotRepository;

    @Autowired
    private CompositeScoreRefreshLogRepository compositeScoreRefreshLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final TypeReference<List<Map<String, Object>>> DETAIL_LIST_TYPE = new TypeReference<>() {};

    private final ConcurrentMap<String, RefreshTaskState> refreshTasks = new ConcurrentHashMap<>();

    public List<Map<String, Object>> listCompositeScores(String academicYear, String className, String keyword, Long teacherId) {
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<CompositeScoreSnapshot> snapshots = (className == null || className.isBlank())
                ? compositeScoreSnapshotRepository.findByAcademicYearOrderByRankNoAscStudentNoAsc(normalizedYear)
                : compositeScoreSnapshotRepository.findByAcademicYearAndClassNameOrderByRankNoAscStudentNoAsc(
                normalizedYear, className.trim());
        Set<String> managedStudentNos = teacherId == null
                ? Set.of()
                : studentService.findManagedByTeacherId(teacherId).stream()
                    .map(Student::getStudentNo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

        return snapshots.stream()
                .filter(snapshot -> teacherId == null || managedStudentNos.contains(snapshot.getStudentNo()))
                .filter(snapshot -> matchesStudentKeyword(snapshot, normalizedKeyword))
                .map(this::toSnapshotRow)
                .toList();
    }

    public Map<String, Object> listCompositeScores(String academicYear, String className, String keyword, Long teacherId, int page, int size) {
        List<Map<String, Object>> rows = listCompositeScores(academicYear, className, keyword, teacherId);
        return buildPage(rows, page, size);
    }

    public Optional<Map<String, Object>> getLatestStudentSnapshot(String studentNo) {
        String normalizedStudentNo = studentNo == null ? "" : studentNo.trim();
        if (normalizedStudentNo.isBlank()) {
            return Optional.empty();
        }
        return compositeScoreSnapshotRepository.findLatestByStudentNo(normalizedStudentNo).stream()
                .findFirst()
                .map(this::toSnapshotRow);
    }

    /**
     * 按学年汇总学生综测快照，供成绩趋势图使用（同一学年取最新一条）。
     */
    public List<Map<String, Object>> listStudentScoreTrendPoints(String studentNo) {
        String normalizedStudentNo = studentNo == null ? "" : studentNo.trim();
        if (normalizedStudentNo.isBlank()) {
            return List.of();
        }

        Map<String, CompositeScoreSnapshot> latestByYear = loadLatestSnapshotsByYear(normalizedStudentNo);

        return latestByYear.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toTrendPointFromSnapshot(entry.getKey(), entry.getValue()))
                .filter(this::hasTrendScore)
                .toList();
    }

    /**
     * 将学期德智体趋势与综测快照合并：有快照的学年优先用快照点（与成绩快照同源），
     * 避免仅写入德育学期分时曲线缺智育/体育。无快照的学年仍保留学期点。
     */
    public List<Map<String, Object>> mergeSemesterTrendWithSnapshots(
            String studentNo,
            List<Map<String, Object>> semesterPoints) {
        List<Map<String, Object>> snapshotPoints = listStudentScoreTrendPoints(studentNo);
        if (snapshotPoints.isEmpty()) {
            return semesterPoints == null ? List.of() : semesterPoints;
        }
        if (semesterPoints == null || semesterPoints.isEmpty()) {
            return snapshotPoints;
        }

        Map<String, Map<String, Object>> snapshotByYear = new LinkedHashMap<>();
        for (Map<String, Object> point : snapshotPoints) {
            String yearKey = toAcademicYearKey(String.valueOf(point.getOrDefault("semester", "")));
            if (!yearKey.isBlank()) {
                snapshotByYear.put(yearKey, point);
            }
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        Set<String> usedSnapshotYears = new LinkedHashSet<>();

        for (Map<String, Object> point : semesterPoints) {
            String semester = String.valueOf(point.getOrDefault("semester", ""));
            String yearKey = toAcademicYearKey(semester);
            Map<String, Object> snapshotPoint = yearKey.isBlank() ? null : snapshotByYear.get(yearKey);
            if (snapshotPoint != null) {
                if (usedSnapshotYears.add(yearKey)) {
                    merged.add(snapshotPoint);
                }
                continue;
            }
            merged.add(point);
        }

        for (Map.Entry<String, Map<String, Object>> entry : snapshotByYear.entrySet()) {
            if (usedSnapshotYears.add(entry.getKey())) {
                merged.add(entry.getValue());
            }
        }

        merged.sort((left, right) -> String.valueOf(left.getOrDefault("semester", ""))
                .compareTo(String.valueOf(right.getOrDefault("semester", ""))));
        return merged;
    }

    private Map<String, CompositeScoreSnapshot> loadLatestSnapshotsByYear(String studentNo) {
        Map<String, CompositeScoreSnapshot> latestByYear = new LinkedHashMap<>();
        for (CompositeScoreSnapshot snapshot : compositeScoreSnapshotRepository.findLatestByStudentNo(studentNo)) {
            String year = snapshot.getAcademicYear() == null ? "" : snapshot.getAcademicYear().trim();
            if (year.isBlank()) {
                continue;
            }
            CompositeScoreSnapshot existing = latestByYear.get(year);
            if (existing == null
                    || (snapshot.getGeneratedAt() != null
                    && (existing.getGeneratedAt() == null || snapshot.getGeneratedAt().isAfter(existing.getGeneratedAt())))) {
                latestByYear.put(year, snapshot);
            }
        }
        return latestByYear;
    }

    private Map<String, Object> toTrendPointFromSnapshot(String academicYear, CompositeScoreSnapshot snapshot) {
        Double moral = sumNullable(snapshot.getMoralBaseScore(), snapshot.getMoralExtraScore());
        Double intellectual = sumNullable(snapshot.getIntellectualBaseScore(), snapshot.getIntellectualExtraScore());
        Double physical = sumNullable(snapshot.getPhysicalBaseScore(), snapshot.getPhysicalExtraScore());

        List<Double> available = new ArrayList<>();
        if (moral != null) {
            available.add(moral);
        }
        if (intellectual != null) {
            available.add(intellectual);
        }
        if (physical != null) {
            available.add(physical);
        }

        // 曲线图例为「综合平均分」：优先用德智体均分，与学期趋势点口径一致；无分项时再回退总分
        Double overall = null;
        if (!available.isEmpty()) {
            overall = available.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        } else if (snapshot.getTotalScore() != null) {
            overall = snapshot.getTotalScore();
        }

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("semester", academicYear);
        point.put("overall", overall == null ? null : roundScore(overall));
        point.put("moral", moral == null ? null : roundScore(moral));
        point.put("intellectual", intellectual == null ? null : roundScore(intellectual));
        point.put("physical", physical == null ? null : roundScore(physical));
        return point;
    }

    private boolean hasTrendScore(Map<String, Object> point) {
        return point.get("overall") != null
                || point.get("moral") != null
                || point.get("intellectual") != null
                || point.get("physical") != null;
    }

    /** 2026-2027-1 / 2026-2027 → 2026-2027 */
    private String toAcademicYearKey(String semesterOrYear) {
        if (semesterOrYear == null) {
            return "";
        }
        String normalized = semesterOrYear.trim()
                .replace('–', '-')
                .replaceAll("\\s+", "");
        if (normalized.isBlank() || "null".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized.replaceAll("[-./][12]$", "");
    }

    private Double sumNullable(Double base, Double extra) {
        if (base == null && extra == null) {
            return null;
        }
        return (base == null ? 0D : base) + (extra == null ? 0D : extra);
    }

    private double roundScore(double value) {
        return Math.round(value * 10D) / 10D;
    }

    public Optional<Map<String, Object>> getStudentSnapshot(String studentNo, String academicYear) {
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        if (normalizedYear.isBlank()) {
            return getLatestStudentSnapshot(studentNo);
        }
        String normalizedStudentNo = studentNo == null ? "" : studentNo.trim();
        if (normalizedStudentNo.isBlank()) {
            return Optional.empty();
        }
        return compositeScoreSnapshotRepository
                .findFirstByAcademicYearAndStudentNoOrderByGeneratedAtDesc(normalizedYear, normalizedStudentNo)
                .map(this::toSnapshotRow);
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows.subList(fromIndex, toIndex));
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private boolean matchesStudentKeyword(CompositeScoreSnapshot snapshot, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        if (snapshot == null) {
            return false;
        }
        String studentNo = snapshot.getStudentNo() == null ? "" : snapshot.getStudentNo().toLowerCase(Locale.ROOT);
        String studentName = snapshot.getStudentName() == null ? "" : snapshot.getStudentName().toLowerCase(Locale.ROOT);
        return studentNo.contains(keyword) || studentName.contains(keyword);
    }

    public CompositeScoreRefreshTaskDTO startRefreshCompositeScores(String academicYear, String className, Long teacherId, Long updatedByUserId, String updatedByName, String updatedByRole) {
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        String normalizedClassName = normalizeClassName(className);
        List<Student> allScopedStudents = teacherId != null
                ? studentService.findManagedByTeacherId(teacherId)
                : studentService.findAll();
        final List<Student> students = normalizedClassName.isBlank()
                ? allScopedStudents
                : allScopedStudents.stream()
                    .filter(student -> normalizedClassName.equals(student.getClassName()))
                    .toList();

        RefreshTaskState task = new RefreshTaskState();
        task.taskId = UUID.randomUUID().toString();
        task.status = "running";
        task.message = "综测刷新进行中";
        task.academicYear = normalizedYear;
        task.scopeLabel = resolveScopeLabel(teacherId, normalizedClassName);
        task.startedAt = LocalDateTime.now();
        task.totalCount.set((int) students.stream().filter(student -> student != null && student.getId() != null).count());
        refreshTasks.put(task.taskId, task);

        CompletableFuture.runAsync(() -> {
            try {
                Integer savedCount = transactionTemplate.execute(status -> refreshCompositeScoresInternal(
                        normalizedYear,
                        normalizedClassName,
                        teacherId,
                        updatedByUserId,
                        updatedByName,
                        updatedByRole,
                        task,
                        students
                ));
                task.successCount.set(savedCount == null ? 0 : savedCount);
                task.status = "completed";
                task.message = "综测刷新完成";
                task.finishedAt = LocalDateTime.now();
            } catch (Exception ex) {
                task.status = "failed";
                task.message = ex.getMessage() == null || ex.getMessage().isBlank() ? "综测刷新失败" : ex.getMessage();
                task.finishedAt = LocalDateTime.now();
            }
        });

        return toRefreshTaskDto(task);
    }

    public java.util.Optional<CompositeScoreRefreshTaskDTO> getRefreshTask(String taskId) {
        RefreshTaskState task = refreshTasks.get(taskId);
        return task == null ? java.util.Optional.empty() : java.util.Optional.of(toRefreshTaskDto(task));
    }

    @Transactional
    public Map<String, Object> refreshCompositeScores(String academicYear, String className, Long teacherId, Long updatedByUserId, String updatedByName, String updatedByRole) {
        String normalizedClassName = normalizeClassName(className);
        int savedCount = refreshCompositeScoresInternal(
                academicYear,
                normalizedClassName,
                teacherId,
                updatedByUserId,
                updatedByName,
                updatedByRole,
                null,
                null
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicYear", academicYear == null ? "" : academicYear.trim());
        result.put("generatedAt", LocalDateTime.now());
        result.put("count", savedCount);
        result.put("updatedByName", resolveUpdatedByName(updatedByName));
        result.put("scopeLabel", resolveScopeLabel(teacherId, normalizedClassName));
        return result;
    }

    @Transactional
    protected int refreshCompositeScoresInternal(
            String academicYear,
            String className,
            Long teacherId,
            Long updatedByUserId,
            String updatedByName,
            String updatedByRole,
            RefreshTaskState task,
            List<Student> scopedStudents
    ) {
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        String normalizedClassName = normalizeClassName(className);
        String scopeType = resolveScopeType(teacherId);
        Long scopeOwnerId = resolveScopeOwnerId(teacherId);
        CompositeScoreWeight weight = compositeScoreWeightService.getCurrentWeight();
        List<Student> students = scopedStudents == null
                ? (teacherId != null ? studentService.findManagedByTeacherId(teacherId) : studentService.findAll())
                : scopedStudents;
        if (!normalizedClassName.isBlank()) {
            students = students.stream()
                    .filter(student -> normalizedClassName.equals(student.getClassName()))
                    .toList();
        }

        if (students.isEmpty()) {
            if (task != null) {
                task.message = "没有可刷新的学生数据";
                task.processedCount.set(0);
            }
            return 0;
        }

        List<Long> studentIds = students.stream()
                .map(Student::getId)
                .filter(Objects::nonNull)
                .toList();
        List<String> studentNos = students.stream()
                .map(Student::getStudentNo)
                .filter(Objects::nonNull)
                .toList();
        Map<String, ClassInfo> classInfoMap = classInfoRepository.findAll().stream()
                .collect(Collectors.toMap(ClassInfo::getName, Function.identity(), (left, right) -> left));
        Map<String, Boolean> physicalFullScoreRuleMap =
                compositePhysicalFullScoreRuleService.getFullScoreRuleMap(normalizedYear);
        Map<Long, String> majorNameMap = majorRepository.findAll().stream()
                .collect(Collectors.toMap(Major::getId, Major::getName, (left, right) -> left));
        Map<Long, List<SubjectScoreRAIN>> subjectScoresByStudentId = subjectScoreRAINRepository.findByStudentIdInWithDetails(studentIds).stream()
                .filter(score -> matchesAcademicYear(score.getSemester(), normalizedYear))
                .collect(Collectors.groupingBy(SubjectScoreRAIN::getStudentId));
        Map<Long, List<MoralEducation>> moralByStudentId = moralEducationRepository.findByStudentIdIn(studentIds).stream()
                .filter(score -> matchesAcademicYear(resolveMoralAcademicYear(score), normalizedYear))
                .collect(Collectors.groupingBy(MoralEducation::getStudentId));
        Map<Long, List<IntellectualEducation>> intellectualByStudentId = intellectualEducationRepository.findByStudentIdIn(studentIds).stream()
                .filter(score -> matchesAcademicYear(score.getSemester(), normalizedYear))
                .collect(Collectors.groupingBy(IntellectualEducation::getStudentId));
        Map<Long, List<PhysicalEducation>> physicalByStudentId = physicalEducationRepository.findByStudentIdIn(studentIds).stream()
                .filter(score -> matchesAcademicYear(score.getSemester(), normalizedYear))
                .collect(Collectors.groupingBy(PhysicalEducation::getStudentId));
        Map<String, List<ExtraScoreApplication>> extraByStudentNo = extraScoreApplicationRepository.findByStudentNoInAndStatus(studentNos, "approved").stream()
                .filter(application -> matchesAcademicYear(resolveApplicationYear(application), normalizedYear))
                .collect(Collectors.groupingBy(ExtraScoreApplication::getStudentNo));
        Set<String> scopeKeys = students.stream()
                .map(student -> normalizedYear + "|" + normalizeClassName(student.getClassName()))
                .filter(key -> !key.endsWith("|"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Map<Long, String>> categoryMapByScope = new HashMap<>();
        for (String scopeKey : scopeKeys) {
            String[] parts = scopeKey.split("\\|", 3);
            categoryMapByScope.put(scopeKey, compositeSubjectConfigService.getCategoryMapByClassName(parts[0], parts[1]));
        }
        validateSubjectCategoryConfig(normalizedYear, students, classInfoMap, majorNameMap, subjectScoresByStudentId, categoryMapByScope);

        List<Map<String, Object>> rows = new ArrayList<>();
        AtomicInteger processedCounter = task == null ? null : task.processedCount;
        for (Student student : students) {
            ClassInfo classInfo = classInfoMap.get(student.getClassName());
            Map<String, Object> row = buildRow(
                    student,
                    normalizedYear,
                    weight,
                    classInfo,
                    compositePhysicalFullScoreRuleService.isFullScoreEnabled(
                            physicalFullScoreRuleMap,
                            student.getClassName()
                    ),
                    subjectScoresByStudentId.getOrDefault(student.getId(), List.of()),
                    moralByStudentId.getOrDefault(student.getId(), List.of()),
                    intellectualByStudentId.getOrDefault(student.getId(), List.of()),
                    physicalByStudentId.getOrDefault(student.getId(), List.of()),
                    extraByStudentNo.getOrDefault(student.getStudentNo(), List.of()),
                    categoryMapByScope,
                    majorNameMap
            );
            if (row != null) {
                rows.add(row);
            }
            if (processedCounter != null) {
                processedCounter.incrementAndGet();
            }
        }

        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> ((Number) row.get("totalScore")).doubleValue())
                .reversed()
                .thenComparing(row -> String.valueOf(row.get("studentNo"))));

        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).put("rank", index + 1);
        }

        Set<String> refreshedStudentNos = rows.stream()
                .map(row -> String.valueOf(row.getOrDefault("studentNo", "")))
                .filter(studentNo -> !studentNo.isBlank())
                .collect(Collectors.toSet());
        if (!refreshedStudentNos.isEmpty()) {
            compositeScoreSnapshotRepository.deleteByAcademicYearAndStudentNoIn(normalizedYear, refreshedStudentNos);
        }
        LocalDateTime generatedAt = LocalDateTime.now();
        List<CompositeScoreSnapshot> snapshots = rows.stream()
                .map(row -> toSnapshotEntity(row, normalizedYear, generatedAt, "CURRENT", 0L, updatedByUserId, updatedByName))
                .toList();
        compositeScoreSnapshotRepository.saveAll(snapshots);
        compositeScoreRefreshLogRepository.save(buildRefreshLog(
                normalizedYear,
                scopeType,
                scopeOwnerId,
                updatedByUserId,
                updatedByName,
                updatedByRole,
                snapshots.size(),
                generatedAt
        ));
        return snapshots.size();
    }

    private void validateSubjectCategoryConfig(
            String academicYear,
            List<Student> students,
            Map<String, ClassInfo> classInfoMap,
            Map<Long, String> majorNameMap,
            Map<Long, List<SubjectScoreRAIN>> subjectScoresByStudentId,
            Map<String, Map<Long, String>> categoryMapByScope
    ) {
        Map<String, Set<String>> missingSubjectsByScope = new LinkedHashMap<>();
        for (Student student : students) {
            if (student == null || student.getId() == null) {
                continue;
            }
            ClassInfo classInfo = classInfoMap.get(student.getClassName());
            Long majorId = classInfo != null ? classInfo.getMajorId() : null;
            String grade = classInfo != null ? classInfo.getGrade() : "";
            if (majorId == null || grade == null || grade.isBlank()) {
                continue;
            }
            String scopeKey = academicYear + "|" + normalizeClassName(student.getClassName());
            Map<Long, String> subjectCategoryMap = categoryMapByScope.getOrDefault(scopeKey, Map.of());
            for (SubjectScoreRAIN score : subjectScoresByStudentId.getOrDefault(student.getId(), List.of())) {
                if (score == null || score.getSubjectId() == null) {
                    continue;
                }
                if (subjectCategoryMap.containsKey(score.getSubjectId())) {
                    continue;
                }
                String scopeLabel = buildScopeLabel(majorNameMap.getOrDefault(majorId, "未命名专业"), grade);
                String subjectName = score.getSubject() == null || score.getSubject().getSubjectName() == null
                        ? "科目ID " + score.getSubjectId()
                        : score.getSubject().getSubjectName();
                missingSubjectsByScope.computeIfAbsent(scopeLabel, key -> new LinkedHashSet<>()).add(subjectName);
            }
        }

        if (!missingSubjectsByScope.isEmpty()) {
            String details = missingSubjectsByScope.entrySet().stream()
                    .map(entry -> entry.getKey() + " 缺少配置: " + String.join("、", entry.getValue()))
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("存在未配置的综测科目，请先在“综测配置”页面完成配置后再刷新。 " + details);
        }
    }

    public Map<String, Object> getSnapshotMeta(String academicYear, Long teacherId) {
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        String scopeType = resolveScopeType(teacherId);
        Long scopeOwnerId = resolveScopeOwnerId(teacherId);
        CompositeScoreRefreshLog refreshLog = compositeScoreRefreshLogRepository
                .findTopByAcademicYearAndScopeTypeAndScopeOwnerIdOrderByCreatedAtDesc(normalizedYear, scopeType, scopeOwnerId)
                .orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicYear", normalizedYear);
        result.put("generatedAt", refreshLog == null ? null : refreshLog.getCreatedAt());
        result.put("updatedByName", refreshLog == null ? null : refreshLog.getUpdatedByName());
        result.put("updatedByRole", refreshLog == null ? null : refreshLog.getUpdatedByRole());
        result.put("scopeLabel", resolveScopeLabel(teacherId));
        result.put("count", listCompositeScores(normalizedYear, null, null, teacherId).size());
        return result;
    }

    public List<Map<String, Object>> listRefreshHistory(String academicYear, Long teacherId) {
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        String scopeType = resolveScopeType(teacherId);
        Long scopeOwnerId = resolveScopeOwnerId(teacherId);
        return compositeScoreRefreshLogRepository
                .findTop20ByAcademicYearAndScopeTypeAndScopeOwnerIdOrderByCreatedAtDesc(normalizedYear, scopeType, scopeOwnerId)
                .stream()
                .map(log -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", log.getId());
                    item.put("academicYear", log.getAcademicYear());
                    item.put("updatedByName", log.getUpdatedByName());
                    item.put("updatedByRole", log.getUpdatedByRole());
                    item.put("refreshCount", log.getRefreshCount());
                    item.put("createdAt", log.getCreatedAt());
                    item.put("scopeLabel", resolveScopeLabel(teacherId));
                    return item;
                })
                .toList();
    }

    public List<Map<String, Object>> listExtraScoreDetails(String studentNo, String academicYear, String category, Long teacherId) {
        if (studentNo == null || studentNo.isBlank()) {
            return List.of();
        }
        Student student = studentService.findByStudentNo(studentNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("学生不存在"));
        if (teacherId != null) {
            boolean accessible = studentService.findManagedByTeacherId(teacherId).stream()
                    .anyMatch(item -> Objects.equals(item.getId(), student.getId()));
            if (!accessible) {
                throw new IllegalArgumentException("无权查看该学生的附加分明细");
            }
        }

        List<ExtraScoreApplication> scopedApplications = extraScoreApplicationRepository.findByStudentNoAndStatus(student.getStudentNo(), "approved").stream()
                .filter(application -> matchesAcademicYear(resolveApplicationYear(application), academicYear == null ? "" : academicYear.trim()))
                .filter(application -> matchesScoreType(application.getScoreType(), category))
                .sorted(Comparator.comparing(ExtraScoreApplication::getAwardTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExtraScoreApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<Long, ExtraScoreEffectiveScoreService.EffectiveScoreResult> effectiveScores =
                effectiveScoreService.resolveEffectiveScoreResults(scopedApplications);
        return scopedApplications.stream()
                .map(application -> toExtraScoreDetail(application, effectiveScores.get(application.getId())))
                .toList();
    }

    public List<Map<String, Object>> listBaseScoreDetails(String studentNo, String academicYear, String category, Long teacherId) {
        if (studentNo == null || studentNo.isBlank()) {
            return List.of();
        }
        Student student = studentService.findByStudentNo(studentNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("学生不存在"));
        if (teacherId != null) {
            boolean accessible = studentService.findManagedByTeacherId(teacherId).stream()
                    .anyMatch(item -> Objects.equals(item.getId(), student.getId()));
            if (!accessible) {
                throw new IllegalArgumentException("无权查看该学生的基础成绩明细");
            }
        }

        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        String normalizedCategory = normalizeScoreType(category);
        CompositeScoreSnapshot snapshot = normalizedYear.isBlank()
                ? null
                : compositeScoreSnapshotRepository
                        .findFirstByAcademicYearAndStudentNoOrderByGeneratedAtDesc(normalizedYear, student.getStudentNo())
                        .orElse(null);

        if ("MORAL".equals(normalizedCategory)) {
            List<Map<String, Object>> snapshotDetails = snapshot == null ? null : deserializeDetails(snapshot.getMoralBaseDetailsJson());
            if (snapshotDetails != null) {
                return snapshotDetails;
            }
            return moralEducationRepository.findByStudentId(student.getId()).stream()
                    .filter(score -> matchesAcademicYear(resolveMoralAcademicYear(score), normalizedYear))
                    .sorted(Comparator.comparing(MoralEducation::getSemester, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(this::toMoralBaseDetail)
                    .toList();
        }
        if ("PHYSICAL".equals(normalizedCategory)) {
            Map<Long, String> subjectCategoryMap = compositeSubjectConfigService.getCategoryMapByClassName(
                    normalizedYear,
                    student.getClassName()
            );
            boolean fullScoreEnabled = snapshot != null && snapshot.getPhysicalBaseFullScoreEnabled() != null
                    ? Boolean.TRUE.equals(snapshot.getPhysicalBaseFullScoreEnabled())
                    : compositePhysicalFullScoreRuleService.isFullScoreEnabled(
                            normalizedYear,
                            student.getClassName()
                    );
            Double physicalCourseAverage = snapshot != null && snapshot.getPhysicalCourseAverage() != null
                    ? snapshot.getPhysicalCourseAverage()
                    : null;
            double countedScore = snapshot != null && snapshot.getPhysicalBaseScore() != null
                    ? snapshot.getPhysicalBaseScore()
                    : CompositeScoreCalculator.resolvePhysicalBaseScore(
                            physicalCourseAverage == null ? 0D : physicalCourseAverage,
                            fullScoreEnabled
                    );
            List<Map<String, Object>> snapshotDetails = snapshot == null ? null : deserializeDetails(snapshot.getPhysicalBaseDetailsJson());
            if (snapshotDetails != null) {
                return snapshotDetails;
            }
            List<SubjectScoreRAIN> physicalSubjectScores = subjectScoreRAINRepository
                    .findByStudentId(student.getId()).stream()
                    .filter(score -> matchesAcademicYear(score.getSemester(), normalizedYear))
                    .filter(score -> "PHYSICAL".equals(resolveCompositeCategory(score, subjectCategoryMap)))
                    .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(item -> item.getSubject() == null ? "" : String.valueOf(item.getSubject().getSubjectName())))
                    .toList();
            if (physicalSubjectScores.isEmpty() && fullScoreEnabled) {
                return List.of(toPhysicalFullScoreRuleDetail(
                        normalizedYear,
                        physicalCourseAverage,
                        countedScore
                ));
            }
            return physicalSubjectScores.stream()
                    .map(score -> toPhysicalBaseDetail(
                            score,
                            fullScoreEnabled,
                            physicalCourseAverage,
                            countedScore
                    ))
                    .toList();
        }
        List<Map<String, Object>> snapshotDetails = snapshot == null ? null : deserializeDetails(snapshot.getIntellectualBaseDetailsJson());
        if (snapshotDetails != null) {
            return snapshotDetails;
        }
        return subjectScoreRAINRepository.findByStudentId(student.getId()).stream()
                .filter(score -> matchesAcademicYear(score.getSemester(), normalizedYear))
                .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.getSubject() == null ? "" : String.valueOf(item.getSubject().getSubjectName())))
                .map(this::toIntellectualBaseDetail)
                .toList();
    }

    private Map<String, Object> buildRow(
            Student student,
            String academicYear,
            CompositeScoreWeight weight,
            ClassInfo classInfo,
            boolean physicalBaseFullScoreEnabled,
            List<SubjectScoreRAIN> scopedScores,
            List<MoralEducation> moralScores,
            List<IntellectualEducation> intellectualScores,
            List<PhysicalEducation> physicalScores,
            List<ExtraScoreApplication> scopedApplications,
            Map<String, Map<Long, String>> categoryMapByScope,
            Map<Long, String> majorNameMap
    ) {
        if (student == null || student.getId() == null) {
            return null;
        }
        Long majorId = classInfo != null ? classInfo.getMajorId() : null;
        String grade = classInfo != null ? classInfo.getGrade() : "";
        String scopeKey = academicYear + "|" + normalizeClassName(student.getClassName());
        Map<Long, String> subjectCategoryMap = categoryMapByScope.getOrDefault(scopeKey, Map.of());

        double courseAverage = scopedScores.stream()
                .filter(score -> !isExcludedFromComposite(score, subjectCategoryMap))
                .map(SubjectScoreRAIN::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        double intellectualBaseScore = scopedScores.stream()
                .filter(score -> "INTELLECTUAL".equals(resolveCompositeCategory(score, subjectCategoryMap)))
                .map(SubjectScoreRAIN::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        Double physicalCourseAverage = scopedScores.stream()
                .filter(score -> "PHYSICAL".equals(resolveCompositeCategory(score, subjectCategoryMap)))
                .map(SubjectScoreRAIN::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
        double physicalCourseScore = physicalCourseAverage == null ? 0D : physicalCourseAverage;
        double physicalBaseScore = CompositeScoreCalculator.resolvePhysicalBaseScore(
                physicalCourseScore,
                physicalBaseFullScoreEnabled
        );

        double moralScore = moralScores.stream()
                .map(MoralEducation::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        double intellectualScore = intellectualScores.stream()
                .map(IntellectualEducation::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        double physicalScore = physicalScores.stream()
                .map(PhysicalEducation::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        Map<Long, Double> effectiveExtraScores = effectiveScoreService.resolveEffectiveSignedScores(scopedApplications);

        double moralExtraScore = scopedApplications.stream()
                .filter(application -> "MORAL".equalsIgnoreCase(application.getScoreType()))
                .mapToDouble(application -> effectiveExtraScores.getOrDefault(application.getId(), 0D))
                .sum();

        double intellectualExtraScore = scopedApplications.stream()
                .filter(application -> "INTELLECTUAL".equalsIgnoreCase(application.getScoreType()))
                .mapToDouble(application -> effectiveExtraScores.getOrDefault(application.getId(), 0D))
                .sum();

        double physicalExtraScore = scopedApplications.stream()
                .filter(application -> "PHYSICAL".equalsIgnoreCase(application.getScoreType()))
                .mapToDouble(application -> effectiveExtraScores.getOrDefault(application.getId(), 0D))
                .sum();

        double totalExtraScore = moralExtraScore + intellectualExtraScore + physicalExtraScore;

        double weightedMoralBase = moralScore * resolveFactor(weight.getMoralBaseWeight());
        double weightedIntellectualBase = intellectualBaseScore * resolveFactor(weight.getIntellectualBaseWeight());
        double weightedPhysicalBase = physicalBaseScore * resolveFactor(weight.getPhysicalBaseWeight());
        double weightedMoralExtra = moralExtraScore * resolveFactor(weight.getMoralExtraWeight());
        double weightedIntellectualExtra = intellectualExtraScore * resolveFactor(weight.getIntellectualExtraWeight());
        double weightedPhysicalExtra = physicalExtraScore * resolveFactor(weight.getPhysicalExtraWeight());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("studentId", student.getId());
        row.put("studentNo", student.getStudentNo());
        row.put("studentName", student.getName());
        row.put("className", student.getClassName());
        row.put("grade", classInfo != null ? classInfo.getGrade() : "");
        row.put("majorId", classInfo != null ? classInfo.getMajorId() : null);
        row.put("majorName", majorId == null ? "" : majorNameMap.getOrDefault(majorId, ""));
        row.put("baseScore", round(courseAverage));
        row.put("moralScore", round(moralScore));
        row.put("intellectualScore", round(intellectualScore));
        row.put("physicalScore", round(physicalScore));
        row.put("moralBaseScore", round(moralScore));
        row.put("moralExtraScore", round(moralExtraScore));
        row.put("intellectualBaseScore", round(intellectualBaseScore));
        row.put("intellectualExtraScore", round(intellectualExtraScore));
        row.put("physicalBaseScore", round(physicalBaseScore));
        row.put("physicalExtraScore", round(physicalExtraScore));
        row.put("intellectualCourseAverage", round(courseAverage));
        row.put("physicalCourseAverage", physicalCourseAverage == null ? null : round(physicalCourseAverage));
        row.put("physicalBaseFullScoreEnabled", physicalBaseFullScoreEnabled);
        row.put("physicalBaseWeight", toPercentValue(weight.getPhysicalBaseWeight()));
        row.put("extraScore", round(totalExtraScore));
        row.put("weightedMoralBaseScore", round(weightedMoralBase));
        row.put("weightedMoralExtraScore", round(weightedMoralExtra));
        row.put("weightedIntellectualBaseScore", round(weightedIntellectualBase));
        row.put("weightedIntellectualExtraScore", round(weightedIntellectualExtra));
        row.put("weightedPhysicalBaseScore", round(weightedPhysicalBase));
        row.put("weightedPhysicalExtraScore", round(weightedPhysicalExtra));
        row.put("weight", toWeightMap(weight));
        row.put("moralBaseDetailsJson", serializeDetails(buildMoralBaseDetails(moralScores)));
        row.put("intellectualBaseDetailsJson", serializeDetails(buildIntellectualBaseDetails(scopedScores, subjectCategoryMap)));
        row.put("physicalBaseDetailsJson", serializeDetails(buildPhysicalBaseDetails(scopedScores, subjectCategoryMap, physicalBaseFullScoreEnabled, physicalCourseAverage, physicalBaseScore)));
        row.put("totalScore", round(
                weightedMoralBase
                        + weightedMoralExtra
                        + weightedIntellectualBase
                        + weightedIntellectualExtra
                        + weightedPhysicalBase
                        + weightedPhysicalExtra
        ));
        row.put("scoreCount", scopedScores.size());
        return row;
    }

    private boolean matchesAcademicYear(String source, String academicYear) {
        if (academicYear == null || academicYear.isBlank()) {
            return true;
        }
        return source != null && source.contains(academicYear);
    }

    private String resolveMoralAcademicYear(MoralEducation score) {
        if (score == null) {
            return null;
        }
        if (score.getAcademicYear() != null && !score.getAcademicYear().isBlank()) {
            return score.getAcademicYear();
        }
        return score.getSemester();
    }

    private boolean matchesScoreType(String sourceType, String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return true;
        }
        return normalizeScoreType(sourceType).equals(normalizeScoreType(targetType));
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Map<String, Object> toExtraScoreDetail(
            ExtraScoreApplication application,
            ExtraScoreEffectiveScoreService.EffectiveScoreResult effectiveScoreResult
    ) {
        double originalSignedScore = effectiveScoreResult == null
                ? effectiveScoreService.resolveSignedScore(application)
                : effectiveScoreResult.originalSignedScore();
        double effectiveSignedScore = effectiveScoreResult == null
                ? originalSignedScore
                : effectiveScoreResult.effectiveSignedScore();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", application.getId());
        detail.put("scoreType", application.getScoreType());
        detail.put("templateName", application.getTemplateName());
        detail.put("groupName", application.getGroupName());
        detail.put("itemName", application.getItemName());
        detail.put("scoreDirection", normalizeScoreDirection(application.getScoreDirection()));
        detail.put("levelName", application.getLevelName());
        detail.put("authorLabel", application.getAuthorLabel());
        detail.put("expectedScore", round(application.getExpectedScore() == null ? 0D : application.getExpectedScore()));
        detail.put("originalSignedScore", round(originalSignedScore));
        detail.put("signedScore", round(effectiveSignedScore));
        detail.put("effectiveSignedScore", round(effectiveSignedScore));
        detail.put("counted", effectiveScoreResult == null || effectiveScoreResult.counted());
        detail.put("excludeReason", effectiveScoreResult == null ? null : effectiveScoreResult.excludeReason());
        detail.put("exclusiveRule", effectiveScoreResult == null ? null : effectiveScoreResult.exclusiveRule());
        detail.put("countedApplicationId", effectiveScoreResult == null ? null : effectiveScoreResult.countedApplicationId());
        detail.put("countedApplicationLabel", effectiveScoreResult == null ? null : effectiveScoreResult.countedApplicationLabel());
        detail.put("certName", application.getCertName());
        detail.put("awardTime", application.getAwardTime());
        detail.put("status", application.getStatus());
        detail.put("detailDesc", application.getDetailDesc());
        return detail;
    }

    private double resolveSignedExtraScore(ExtraScoreApplication application) {
        return effectiveScoreService.resolveSignedScore(application);
    }

    private String normalizeScoreDirection(String scoreDirection) {
        return scoreDirection == null || scoreDirection.isBlank() ? "add" : scoreDirection.trim().toLowerCase();
    }

    private String normalizeScoreType(String scoreType) {
        String normalized = scoreType == null ? "" : scoreType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MORAL", "德育" -> "MORAL";
            case "PHYSICAL", "体育" -> "PHYSICAL";
            case "INTELLECTUAL", "智育" -> "INTELLECTUAL";
            default -> "INTELLECTUAL";
        };
    }

    private Map<String, Object> toIntellectualBaseDetail(SubjectScoreRAIN score) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", score.getId());
        detail.put("category", "智育基础分");
        detail.put("subjectName", score.getSubject() == null ? "" : score.getSubject().getSubjectName());
        detail.put("score", round(score.getScore() == null ? 0D : score.getScore()));
        detail.put("semester", score.getSemester());
        detail.put("detailCategory", score.getSubject() == null || score.getSubject().getCategory() == null
                ? null
                : score.getSubject().getCategory().name());
        return detail;
    }

    private Map<String, Object> toMoralBaseDetail(MoralEducation score) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", score.getId());
        detail.put("category", "德育基础分");
        detail.put("academicYear", resolveMoralAcademicYear(score));
        detail.put("secondClassScore", round(score.getSecondClassScore() == null ? 0D : score.getSecondClassScore()));
        detail.put("activityScore", round(score.getActivityScore() == null ? 0D : score.getActivityScore()));
        detail.put("bonusScore", round(score.getBonusScore() == null ? 0D : score.getBonusScore()));
        detail.put("score", round(score.getTotalScore() == null ? 0D : score.getTotalScore()));
        return detail;
    }

    private Map<String, Object> toPhysicalBaseDetail(PhysicalEducation score) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", score.getId());
        detail.put("category", "体育基础分");
        detail.put("semester", score.getSemester());
        detail.put("rawScore", round(score.getRawScore() == null ? 0D : score.getRawScore()));
        detail.put("percentage", round((score.getPercentage() == null ? 0D : score.getPercentage()) * 100D));
        detail.put("bonusScore", round(score.getBonusScore() == null ? 0D : score.getBonusScore()));
        detail.put("score", round(score.getTotalScore() == null ? 0D : score.getTotalScore()));
        return detail;
    }

    private Map<String, Object> toPhysicalBaseDetail(
            SubjectScoreRAIN score,
            boolean fullScoreEnabled,
            Double physicalCourseAverage,
            double countedScore
    ) {
        double rawScore = score.getScore() == null ? 0D : score.getScore();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", score.getId());
        detail.put("category", "体育基础分");
        detail.put("subjectName", score.getSubject() == null ? "" : score.getSubject().getSubjectName());
        detail.put("semester", score.getSemester());
        detail.put("rawScore", round(rawScore));
        detail.put("percentage", 100D);
        detail.put("bonusScore", 0D);
        detail.put("score", round(countedScore));
        detail.put("fullScoreEnabled", fullScoreEnabled);
        detail.put("physicalCourseAverage", physicalCourseAverage == null ? null : round(physicalCourseAverage));
        detail.put("ruleOnly", false);
        return detail;
    }

    private Map<String, Object> toPhysicalFullScoreRuleDetail(
            String academicYear,
            Double physicalCourseAverage,
            double countedScore
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", "physical-full-score-rule");
        detail.put("category", "体育基础分");
        detail.put("subjectName", "");
        detail.put("semester", academicYear);
        detail.put("rawScore", null);
        detail.put("percentage", 100D);
        detail.put("bonusScore", 0D);
        detail.put("score", round(countedScore));
        detail.put("fullScoreEnabled", true);
        detail.put("physicalCourseAverage", physicalCourseAverage == null ? null : round(physicalCourseAverage));
        detail.put("ruleOnly", true);
        return detail;
    }

    private List<Map<String, Object>> buildMoralBaseDetails(List<MoralEducation> moralScores) {
        return moralScores.stream()
                .sorted(Comparator.comparing(MoralEducation::getSemester, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toMoralBaseDetail)
                .toList();
    }

    private List<Map<String, Object>> buildIntellectualBaseDetails(
            List<SubjectScoreRAIN> scopedScores,
            Map<Long, String> subjectCategoryMap
    ) {
        return scopedScores.stream()
                .filter(score -> !isExcludedFromComposite(score, subjectCategoryMap))
                .filter(score -> "INTELLECTUAL".equals(resolveCompositeCategory(score, subjectCategoryMap)))
                .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.getSubject() == null ? "" : String.valueOf(item.getSubject().getSubjectName())))
                .map(this::toIntellectualBaseDetail)
                .toList();
    }

    private List<Map<String, Object>> buildPhysicalBaseDetails(
            List<SubjectScoreRAIN> scopedScores,
            Map<Long, String> subjectCategoryMap,
            boolean fullScoreEnabled,
            Double physicalCourseAverage,
            double countedScore
    ) {
        List<SubjectScoreRAIN> physicalScores = scopedScores.stream()
                .filter(score -> !isExcludedFromComposite(score, subjectCategoryMap))
                .filter(score -> "PHYSICAL".equals(resolveCompositeCategory(score, subjectCategoryMap)))
                .sorted(Comparator.comparing(SubjectScoreRAIN::getSemester, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.getSubject() == null ? "" : String.valueOf(item.getSubject().getSubjectName())))
                .toList();
        if (physicalScores.isEmpty() && fullScoreEnabled) {
            return List.of(toPhysicalFullScoreRuleDetail("", physicalCourseAverage, countedScore));
        }
        Double physicalCourseAverageForDetail = physicalCourseAverage == null ? null : round(physicalCourseAverage);
        return physicalScores.stream()
                .map(score -> toPhysicalBaseDetail(score, fullScoreEnabled, physicalCourseAverageForDetail, countedScore))
                .toList();
    }

    private String serializeDetails(List<Map<String, Object>> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? List.of() : details);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<Map<String, Object>> deserializeDetails(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DETAIL_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    public Map<String, Object> getWeightConfig() {
        return toWeightMap(compositeScoreWeightService.getCurrentWeight());
    }

    public Map<String, Object> saveWeightConfig(CompositeScoreWeight weight) {
        return toWeightMap(compositeScoreWeightService.save(weight));
    }

    private Map<String, Object> toWeightMap(CompositeScoreWeight weight) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moralBaseWeight", toPercentValue(weight.getMoralBaseWeight()));
        result.put("moralExtraWeight", toPercentValue(weight.getMoralExtraWeight()));
        result.put("intellectualBaseWeight", toPercentValue(weight.getIntellectualBaseWeight()));
        result.put("intellectualExtraWeight", toPercentValue(weight.getIntellectualExtraWeight()));
        result.put("physicalBaseWeight", toPercentValue(weight.getPhysicalBaseWeight()));
        result.put("physicalExtraWeight", toPercentValue(weight.getPhysicalExtraWeight()));
        return result;
    }

    private double safeWeight(Double value) {
        return value == null ? 0D : value;
    }

    private double toPercentValue(Double value) {
        double safe = safeWeight(value);
        return safe <= 1D ? safe * 100D : safe;
    }

    private double resolveFactor(Double value) {
        double safe = safeWeight(value);
        return safe <= 1D ? safe : safe / 100D;
    }

    private CompositeScoreSnapshot toSnapshotEntity(
            Map<String, Object> row,
            String academicYear,
            LocalDateTime generatedAt,
            String scopeType,
            Long scopeOwnerId,
            Long updatedByUserId,
            String updatedByName
    ) {
        CompositeScoreSnapshot snapshot = new CompositeScoreSnapshot();
        snapshot.setAcademicYear(academicYear);
        snapshot.setScopeType(scopeType);
        snapshot.setScopeOwnerId(scopeOwnerId);
        snapshot.setClassName(String.valueOf(row.getOrDefault("className", "")));
        snapshot.setStudentId(((Number) row.get("studentId")).longValue());
        snapshot.setStudentNo(String.valueOf(row.getOrDefault("studentNo", "")));
        snapshot.setStudentName(String.valueOf(row.getOrDefault("studentName", "")));
        snapshot.setMajorId(row.get("majorId") instanceof Number ? ((Number) row.get("majorId")).longValue() : null);
        snapshot.setMajorName(String.valueOf(row.getOrDefault("majorName", "")));
        snapshot.setGrade(String.valueOf(row.getOrDefault("grade", "")));
        snapshot.setRankNo(((Number) row.get("rank")).intValue());
        snapshot.setMoralBaseScore(asDouble(row.get("moralBaseScore")));
        snapshot.setMoralExtraScore(asDouble(row.get("moralExtraScore")));
        snapshot.setIntellectualBaseScore(asDouble(row.get("intellectualBaseScore")));
        snapshot.setIntellectualExtraScore(asDouble(row.get("intellectualExtraScore")));
        snapshot.setPhysicalBaseScore(asDouble(row.get("physicalBaseScore")));
        snapshot.setPhysicalCourseAverage(row.get("physicalCourseAverage") instanceof Number
                ? ((Number) row.get("physicalCourseAverage")).doubleValue()
                : null);
        snapshot.setPhysicalBaseFullScoreEnabled(Boolean.TRUE.equals(row.get("physicalBaseFullScoreEnabled")));
        snapshot.setPhysicalBaseWeight(asDouble(row.get("physicalBaseWeight")));
        snapshot.setPhysicalExtraScore(asDouble(row.get("physicalExtraScore")));
        snapshot.setWeightedMoralBaseScore(asDouble(row.get("weightedMoralBaseScore")));
        snapshot.setWeightedMoralExtraScore(asDouble(row.get("weightedMoralExtraScore")));
        snapshot.setWeightedIntellectualBaseScore(asDouble(row.get("weightedIntellectualBaseScore")));
        snapshot.setWeightedIntellectualExtraScore(asDouble(row.get("weightedIntellectualExtraScore")));
        snapshot.setWeightedPhysicalBaseScore(asDouble(row.get("weightedPhysicalBaseScore")));
        snapshot.setWeightedPhysicalExtraScore(asDouble(row.get("weightedPhysicalExtraScore")));
        snapshot.setIntellectualBaseDetailsJson(asString(row.get("intellectualBaseDetailsJson")));
        snapshot.setPhysicalBaseDetailsJson(asString(row.get("physicalBaseDetailsJson")));
        snapshot.setMoralBaseDetailsJson(asString(row.get("moralBaseDetailsJson")));
        snapshot.setTotalScore(asDouble(row.get("totalScore")));
        snapshot.setGeneratedAt(generatedAt);
        snapshot.setUpdatedByUserId(updatedByUserId);
        snapshot.setUpdatedByName(resolveUpdatedByName(updatedByName));
        return snapshot;
    }

    private Map<String, Object> toSnapshotRow(CompositeScoreSnapshot snapshot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("academicYear", snapshot.getAcademicYear());
        row.put("studentId", snapshot.getStudentId());
        row.put("studentNo", snapshot.getStudentNo());
        row.put("studentName", snapshot.getStudentName());
        row.put("className", snapshot.getClassName());
        row.put("majorId", snapshot.getMajorId());
        row.put("majorName", snapshot.getMajorName());
        row.put("grade", snapshot.getGrade());
        row.put("rank", snapshot.getRankNo());
        row.put("moralBaseScore", snapshot.getMoralBaseScore());
        row.put("moralExtraScore", snapshot.getMoralExtraScore());
        row.put("intellectualBaseScore", snapshot.getIntellectualBaseScore());
        row.put("intellectualExtraScore", snapshot.getIntellectualExtraScore());
        row.put("physicalBaseScore", snapshot.getPhysicalBaseScore());
        row.put("physicalCourseAverage", snapshot.getPhysicalCourseAverage());
        row.put("physicalBaseFullScoreEnabled", snapshot.getPhysicalBaseFullScoreEnabled());
        if (snapshot.getPhysicalBaseWeight() != null) {
            row.put("weight", Map.of("physicalBaseWeight", snapshot.getPhysicalBaseWeight()));
        }
        row.put("physicalExtraScore", snapshot.getPhysicalExtraScore());
        row.put("weightedMoralBaseScore", snapshot.getWeightedMoralBaseScore());
        row.put("weightedMoralExtraScore", snapshot.getWeightedMoralExtraScore());
        row.put("weightedIntellectualBaseScore", snapshot.getWeightedIntellectualBaseScore());
        row.put("weightedIntellectualExtraScore", snapshot.getWeightedIntellectualExtraScore());
        row.put("weightedPhysicalBaseScore", snapshot.getWeightedPhysicalBaseScore());
        row.put("weightedPhysicalExtraScore", snapshot.getWeightedPhysicalExtraScore());
        row.put("totalScore", snapshot.getTotalScore());
        row.put("generatedAt", snapshot.getGeneratedAt());
        row.put("updatedByName", snapshot.getUpdatedByName());
        return row;
    }

    private CompositeScoreRefreshLog buildRefreshLog(
            String academicYear,
            String scopeType,
            Long scopeOwnerId,
            Long updatedByUserId,
            String updatedByName,
            String updatedByRole,
            int refreshCount,
            LocalDateTime createdAt
    ) {
        CompositeScoreRefreshLog log = new CompositeScoreRefreshLog();
        log.setAcademicYear(academicYear);
        log.setScopeType(scopeType);
        log.setScopeOwnerId(scopeOwnerId);
        log.setUpdatedByUserId(updatedByUserId);
        log.setUpdatedByName(resolveUpdatedByName(updatedByName));
        log.setUpdatedByRole(updatedByRole == null ? "" : updatedByRole.trim().toUpperCase(Locale.ROOT));
        log.setRefreshCount(refreshCount);
        log.setCreatedAt(createdAt);
        return log;
    }

    private String resolveScopeType(Long teacherId) {
        return teacherId == null ? "ADMIN" : "TEACHER";
    }

    private Long resolveScopeOwnerId(Long teacherId) {
        return teacherId == null ? 0L : teacherId;
    }

    private String normalizeClassName(String className) {
        return className == null ? "" : className.trim();
    }

    private String resolveUpdatedByName(String updatedByName) {
        if (updatedByName == null || updatedByName.isBlank()) {
            return "当前用户";
        }
        try {
            return URLDecoder.decode(updatedByName.trim(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return updatedByName.trim();
        }
    }

    private String resolveScopeLabel(Long teacherId) {
        return teacherId == null ? "全校范围" : "当前老师范围";
    }

    private String resolveScopeLabel(Long teacherId, String className) {
        String base = resolveScopeLabel(teacherId);
        String normalizedClassName = normalizeClassName(className);
        return normalizedClassName.isBlank() ? base : base + " · " + normalizedClassName;
    }

    private String buildScopeLabel(String majorName, String grade) {
        return String.valueOf(majorName == null ? "" : majorName) + " " + String.valueOf(grade == null ? "" : grade).trim();
    }

    private CompositeScoreRefreshTaskDTO toRefreshTaskDto(RefreshTaskState task) {
        CompositeScoreRefreshTaskDTO dto = new CompositeScoreRefreshTaskDTO();
        dto.setTaskId(task.taskId);
        dto.setStatus(task.status);
        dto.setTotalCount(task.totalCount.get());
        dto.setProcessedCount(task.processedCount.get());
        dto.setSuccessCount(task.successCount.get());
        dto.setMessage(task.message);
        dto.setAcademicYear(task.academicYear);
        dto.setScopeLabel(task.scopeLabel);
        dto.setStartedAt(task.startedAt);
        dto.setFinishedAt(task.finishedAt);
        return dto;
    }

    private Double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isEmpty() ? null : text;
    }

    private String resolveCompositeCategory(SubjectScoreRAIN score, Map<Long, String> subjectCategoryMap) {
        if (score == null) {
            return "INTELLECTUAL";
        }
        Long subjectId = score.getSubjectId();
        if (subjectId != null && subjectCategoryMap.containsKey(subjectId)) {
            return subjectCategoryMap.get(subjectId);
        }
        String subjectName = score.getSubject() == null ? "" : score.getSubject().getSubjectName();
        throw new IllegalStateException("科目未配置综测分类: " + subjectName);
    }

    private boolean isExcludedFromComposite(SubjectScoreRAIN score, Map<Long, String> subjectCategoryMap) {
        if (score == null) {
            return false;
        }
        Long subjectId = score.getSubjectId();
        if (subjectId == null) {
            return false;
        }
        return "EXCLUDED".equals(subjectCategoryMap.get(subjectId));
    }

    private static class RefreshTaskState {
        private String taskId;
        private String status;
        private String message;
        private String academicYear;
        private String scopeLabel;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private final AtomicInteger totalCount = new AtomicInteger();
        private final AtomicInteger processedCount = new AtomicInteger();
        private final AtomicInteger successCount = new AtomicInteger();
    }
}
