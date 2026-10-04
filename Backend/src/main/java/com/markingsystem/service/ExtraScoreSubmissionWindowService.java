package com.markingsystem.service;

import com.markingsystem.dto.ExtraScoreSubmissionWindowRequest;
import com.markingsystem.entity.AcademicYear;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreSubmissionPeriod;
import com.markingsystem.entity.ExtraScoreSubmissionWindow;
import com.markingsystem.entity.Student;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.ExtraScoreSubmissionPeriodRepository;
import com.markingsystem.repository.ExtraScoreSubmissionWindowRepository;
import com.markingsystem.util.RequestUserContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExtraScoreSubmissionWindowService {

    private final ExtraScoreSubmissionWindowRepository repository;
    private final ExtraScoreSubmissionPeriodRepository periodRepository;
    private final AcademicYearService academicYearService;
    private final ClassInfoRepository classInfoRepository;
    private final StudentService studentService;
    private final ExtraScoreApplicationRepository applicationRepository;
    private final AdminCompositeScoreService adminCompositeScoreService;

    public ExtraScoreSubmissionWindowService(
            ExtraScoreSubmissionWindowRepository repository,
            ExtraScoreSubmissionPeriodRepository periodRepository,
            AcademicYearService academicYearService,
            ClassInfoRepository classInfoRepository,
            StudentService studentService,
            ExtraScoreApplicationRepository applicationRepository,
            AdminCompositeScoreService adminCompositeScoreService
    ) {
        this.repository = repository;
        this.periodRepository = periodRepository;
        this.academicYearService = academicYearService;
        this.classInfoRepository = classInfoRepository;
        this.studentService = studentService;
        this.applicationRepository = applicationRepository;
        this.adminCompositeScoreService = adminCompositeScoreService;
    }

    public List<Map<String, Object>> listByTeacherAndAcademicYear(Long teacherId, String academicYear) {
        return listReviewPeriods(new RequestUserContext(null, "TEACHER", teacherId, null, null), academicYear);
    }

    public List<Map<String, Object>> listReviewPeriods(RequestUserContext context, String academicYear) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        String normalizedYear = resolveCurrentAcademicYear(academicYear);
        List<ExtraScoreSubmissionWindow> records = findVisibleWindows(context, normalizedYear);
        Map<String, List<ExtraScoreSubmissionWindow>> grouped = groupWindows(records, normalizedYear);
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.values().forEach(group -> result.add(toReviewPeriodMap(group, normalizedYear, null, context)));
        result.sort(Comparator.comparing((Map<String, Object> item) -> String.valueOf(item.get("createdAt")), Comparator.reverseOrder()));
        applyBatchPeriodLabels(result);
        return result;
    }

    @Transactional
    public Map<String, Object> closeReviewPeriod(RequestUserContext context, Long windowId) {
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以提前结束提交周期");
        }
        ExtraScoreSubmissionWindow target = repository.findById(windowId)
                .orElseThrow(() -> new IllegalArgumentException("提交周期不存在"));
        if (!context.getTeacherId().equals(target.getTeacherId())) {
            throw new IllegalArgumentException("无权结束该提交周期");
        }

        List<ExtraScoreSubmissionWindow> windows = findBatchWindows(target);
        PeriodSettlement settlement = settleReviewPeriod(windows, LocalDateTime.now(), true);
        Map<String, Object> refreshResult = refreshCompositeSnapshotsForSettledPeriod(
                windows,
                context.getUserId(),
                "提前结束附加分周期",
                context.getRole()
        );

        Map<String, Object> result = toReviewPeriodMap(windows, defaultString(target.getAcademicYear()), null, context);
        result.put("snapshotRefresh", refreshResult);
        result.put("settlement", settlement.toMap());
        result.put("message", buildSettlementMessage("周期已提前结束", settlement));
        return result;
    }

    @Transactional
    public Map<String, Object> completeReviewPeriod(RequestUserContext context, Long windowId) {
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以完成审核");
        }
        ExtraScoreSubmissionWindow target = repository.findById(windowId)
                .orElseThrow(() -> new IllegalArgumentException("提交周期不存在"));
        if (!context.getTeacherId().equals(target.getTeacherId())) {
            throw new IllegalArgumentException("无权完成该提交周期审核");
        }

        List<ExtraScoreSubmissionWindow> windows = findBatchWindows(target);
        List<Long> windowIds = windows.stream()
                .map(ExtraScoreSubmissionWindow::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<ExtraScoreApplication> applications = windowIds.isEmpty()
                ? List.of()
                : applicationRepository.findBySubmissionWindowIdIn(windowIds);
        long pending = applications.stream().filter(item -> "pending".equals(item.getStatus())).count();
        if (applications.isEmpty()) {
            throw new IllegalArgumentException("当前周期还没有学生提交附加分申请，不能完成审核");
        }
        if (pending > 0) {
            String details = buildPendingClassSummary(applications);
            String suffix = details.isBlank() ? "" : "：" + details;
            throw new IllegalArgumentException("当前批次仍有待审核申请" + suffix + "。请先完成这些班级的班委和老师审核");
        }
        PeriodSettlement settlement = settleReviewPeriod(windows, LocalDateTime.now(), true);
        Map<String, Object> refreshResult = refreshCompositeSnapshotsForSettledPeriod(
                windows,
                context.getUserId(),
                "完成附加分审核",
                context.getRole()
        );
        Map<String, Object> result = toReviewPeriodMap(windows, defaultString(target.getAcademicYear()), null, context);
        result.put("snapshotRefresh", refreshResult);
        result.put("settlement", settlement.toMap());
        result.put("message", buildSettlementMessage("审核已完成，综测快照已更新", settlement));
        return result;
    }

    public List<Long> findCurrentReviewWindowIds(RequestUserContext context) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        String academicYear = resolveCurrentAcademicYear();
        List<ExtraScoreSubmissionWindow> windows = findVisibleWindows(context, academicYear);
        Map<String, List<ExtraScoreSubmissionWindow>> grouped = groupWindows(windows, academicYear);
        LocalDateTime now = LocalDateTime.now();
        return grouped.values().stream()
                .sorted(Comparator.comparing(
                        group -> group.stream()
                                .map(ExtraScoreSubmissionWindow::getCreatedAt)
                                .filter(java.util.Objects::nonNull)
                                .max(Comparator.naturalOrder())
                                .orElse(LocalDateTime.MIN),
                        Comparator.reverseOrder()
                ))
                .filter(group -> group.stream().anyMatch(item -> Boolean.TRUE.equals(item.getEnabled()) && isWindowOpenAt(item, now)))
                .findFirst()
                .or(() -> grouped.values().stream()
                        .filter(group -> group.stream().anyMatch(item -> Boolean.TRUE.equals(item.getEnabled())))
                        .findFirst())
                .orElse(List.of())
                .stream()
                .map(ExtraScoreSubmissionWindow::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public Set<String> findEnabledReviewClassNames(RequestUserContext context) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        String academicYear = resolveCurrentAcademicYear();
        Set<String> classNames = new HashSet<>();
        for (ExtraScoreSubmissionWindow window : findVisibleWindows(context, academicYear)) {
            if (!Boolean.TRUE.equals(window.getEnabled())) {
                continue;
            }
            String className = defaultString(window.getClassName()).trim();
            if (!className.isBlank()) {
                classNames.add(className);
            }
        }
        return classNames;
    }

    public Set<Long> findEnabledReviewWindowIds(RequestUserContext context) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        String academicYear = resolveCurrentAcademicYear();
        Set<Long> windowIds = new HashSet<>();
        for (ExtraScoreSubmissionWindow window : findVisibleWindows(context, academicYear)) {
            if (Boolean.TRUE.equals(window.getEnabled()) && window.getId() != null) {
                windowIds.add(window.getId());
            }
        }
        return windowIds;
    }

    public List<ExtraScoreSubmissionWindow> findEnabledReviewWindows(RequestUserContext context) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        String academicYear = resolveCurrentAcademicYear();
        return findVisibleWindows(context, academicYear).stream()
                .filter(window -> Boolean.TRUE.equals(window.getEnabled()))
                .toList();
    }

    @Transactional
    public Map<String, Object> save(Long teacherId, ExtraScoreSubmissionWindowRequest request) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        String academicYear = resolveCurrentAcademicYear(request.getAcademicYear());
        LocalDateTime startTime = parseTime(request.getStartTime(), "开始时间");
        LocalDateTime endTime = parseTime(request.getEndTime(), "截止时间");
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("截止时间必须晚于开始时间");
        }
        List<String> rawClassNames = request.getClassNames() == null || request.getClassNames().isEmpty()
                ? (request.getClassName() == null ? List.of() : List.of(request.getClassName()))
                : request.getClassNames();
        List<String> classNames = rawClassNames.stream()
                .map(this::defaultString)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (classNames.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个班级");
        }

        List<ClassInfo> managedClasses = classInfoRepository.findByTeacherId(teacherId);
        Set<String> managedClassNames = new HashSet<>();
        managedClasses.forEach(item -> managedClassNames.add(defaultString(item.getName())));
        for (String className : classNames) {
            if (!managedClassNames.contains(className)) {
                throw new IllegalArgumentException("无权配置班级：" + className);
            }
        }

        String batchCode = buildBatchCode(teacherId, academicYear, startTime, endTime);
        Set<String> duplicateClassNames = repository
                .findByTeacherIdAndAcademicYearOrderByClassNameAsc(teacherId, academicYear)
                .stream()
                .filter(item -> batchCode.equals(defaultString(item.getBatchCode())))
                .map(ExtraScoreSubmissionWindow::getClassName)
                .map(this::defaultString)
                .filter(classNames::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!duplicateClassNames.isEmpty()) {
            throw new IllegalArgumentException("相同提交时间的周期已存在：" + String.join("、", duplicateClassNames));
        }
        ExtraScoreSubmissionPeriod period = createSubmissionPeriod(
                teacherId,
                academicYear,
                batchCode,
                startTime,
                endTime,
                Boolean.TRUE.equals(request.getEnabled())
        );
        List<ExtraScoreSubmissionWindow> saved = new ArrayList<>();
        for (String className : classNames) {
            ExtraScoreSubmissionWindow entity = new ExtraScoreSubmissionWindow();
            entity.setAcademicYear(academicYear);
            entity.setBatchCode(batchCode);
            entity.setPeriodId(period.getId());
            entity.setClassName(className);
            entity.setTeacherId(teacherId);
            entity.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
            entity.setStartTime(startTime);
            entity.setEndTime(endTime);
            saved.add(repository.save(entity));
        }
        return toBatchMap(saved, academicYear);
    }

    public void validateSubmissionWindow(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("学生信息不存在");
        }
        ExtraScoreSubmissionWindow window = resolveCurrentWindow(student);
        if (window == null || !Boolean.TRUE.equals(window.getEnabled())) {
            String academicYear = resolveCurrentAcademicYear();
            String className = student.getClassName() == null ? "" : student.getClassName().trim();
            throw new IllegalArgumentException(String.format("%s 学年 %s 班暂未开启附加分提交", academicYear, className));
        }
        LocalDateTime now = LocalDateTime.now();
        if (window.getStartTime() != null && now.isBefore(window.getStartTime())) {
            throw new IllegalArgumentException(String.format("%s 班附加分提交将于 %s 开始", student.getClassName(), formatTime(window.getStartTime())));
        }
        if (window.getEndTime() != null && now.isAfter(window.getEndTime())) {
            throw new IllegalArgumentException(String.format("%s 班附加分提交已于 %s 截止", student.getClassName(), formatTime(window.getEndTime())));
        }
    }

    public ExtraScoreSubmissionWindow getActiveSubmissionWindow(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("学生信息不存在");
        }
        validateSubmissionWindow(student);
        return resolveCurrentWindow(student);
    }

    public Map<String, Object> getCurrentWindow(Student student) {
        ExtraScoreSubmissionWindow window = resolveCurrentWindow(student);
        String academicYear = resolveCurrentAcademicYear();
        String className = student == null || student.getClassName() == null ? "" : student.getClassName().trim();
        if (student == null || className.isBlank()) {
            throw new IllegalArgumentException("当前学生未分配班级，暂不能查看提交周期");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicYear", academicYear);
        result.put("className", className);
        result.put("enabled", window != null && Boolean.TRUE.equals(window.getEnabled()));
        result.put("startTime", window == null ? null : window.getStartTime());
        result.put("endTime", window == null ? null : window.getEndTime());
        result.put("open", window != null && Boolean.TRUE.equals(window.getEnabled())
                && (window.getStartTime() == null || !LocalDateTime.now().isBefore(window.getStartTime()))
                && (window.getEndTime() == null || !LocalDateTime.now().isAfter(window.getEndTime())));
        return result;
    }

    public List<Map<String, Object>> getStudentWindowHistory(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("学生信息不存在");
        }
        String className = defaultString(student.getClassName()).trim();
        if (className.isBlank()) {
            throw new IllegalArgumentException("当前学生未分配班级，暂不能查看提交周期");
        }
        List<ExtraScoreSubmissionWindow> records = repository.findByClassNameOrderByAcademicYearDescCreatedAtDesc(className);
        Map<String, List<ExtraScoreSubmissionWindow>> grouped = new LinkedHashMap<>();
        for (ExtraScoreSubmissionWindow item : records) {
            String batchCode = defaultString(item.getBatchCode());
            String key = batchCode.isBlank()
                    ? String.format("%s__%s__%s", defaultString(item.getAcademicYear()), formatTime(item.getStartTime()), formatTime(item.getEndTime()))
                    : batchCode;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.values().forEach(group -> result.add(toBatchMap(group, defaultString(group.get(0).getAcademicYear()))));
        result.sort(Comparator.comparing((Map<String, Object> item) -> String.valueOf(item.get("academicYear")), Comparator.reverseOrder())
                .thenComparing(item -> String.valueOf(item.get("createdAt")), Comparator.reverseOrder()));
        return result;
    }

    private ExtraScoreSubmissionWindow resolveCurrentWindow(Student student) {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
        if (student == null) {
            return null;
        }
        String academicYear = resolveCurrentAcademicYear();
        String className = student.getClassName() == null ? "" : student.getClassName().trim();
        if (className.isBlank()) {
            return null;
        }
        List<ExtraScoreSubmissionWindow> records = repository.findByAcademicYearAndClassNameOrderByCreatedAtDesc(academicYear, className);
        LocalDateTime now = LocalDateTime.now();
        return records.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> isWindowOpenAt(item, now))
                .max(Comparator.comparing(ExtraScoreSubmissionWindow::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(records.stream()
                        .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                        .findFirst()
                        .orElse(records.stream().findFirst().orElse(null)));
    }

    private boolean isWindowOpenAt(ExtraScoreSubmissionWindow window, LocalDateTime now) {
        return window != null
                && (window.getStartTime() == null || !now.isBefore(window.getStartTime()))
                && (window.getEndTime() == null || !now.isAfter(window.getEndTime()));
    }

    private Map<String, Object> toBatchMap(List<ExtraScoreSubmissionWindow> windows, String academicYear) {
        Map<String, Object> result = new LinkedHashMap<>();
        ExtraScoreSubmissionWindow first = windows.isEmpty() ? null : windows.get(0);
        result.put("id", first == null ? null : first.getId());
        result.put("batchCode", first == null ? null : first.getBatchCode());
        result.put("periodId", first == null ? null : first.getPeriodId());
        result.put("academicYear", academicYear);
        result.put("enabled", first != null && Boolean.TRUE.equals(first.getEnabled()));
        result.put("startTime", first == null ? null : first.getStartTime());
        result.put("endTime", first == null ? null : first.getEndTime());
        result.put("teacherId", first == null ? null : first.getTeacherId());
        result.put("createdAt", first == null ? null : first.getCreatedAt());
        result.put("periodLabel", first == null ? null : resolvePeriodLabel(first));
        result.put("classNames", windows.stream().map(ExtraScoreSubmissionWindow::getClassName).distinct().sorted().toList());
        result.put("classCount", windows.stream().map(ExtraScoreSubmissionWindow::getClassName).distinct().count());
        return result;
    }

    private Map<String, Object> toReviewPeriodMap(List<ExtraScoreSubmissionWindow> windows, String academicYear, String scoreType, RequestUserContext context) {
        Map<String, Object> result = toBatchMap(windows, academicYear);
        List<Long> windowIds = windows.stream()
                .map(ExtraScoreSubmissionWindow::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        String normalizedScoreType = scoreType == null || scoreType.isBlank() || "all".equalsIgnoreCase(scoreType)
                ? null
                : scoreType.trim().toUpperCase();
        List<ExtraScoreApplication> applications = windowIds.isEmpty()
                ? List.of()
                : applicationRepository.findBySubmissionWindowIdIn(windowIds).stream()
                    .filter(item -> normalizedScoreType == null || normalizedScoreType.equalsIgnoreCase(defaultString(item.getScoreType())))
                    .toList();

        long pending = applications.stream().filter(item -> "pending".equals(item.getStatus())).count();
        long committeePending = applications.stream().filter(item -> "pending".equals(item.getStatus()) && "committee_pending".equals(item.getReviewStage())).count();
        long teacherPending = applications.stream().filter(item -> "pending".equals(item.getStatus()) && "teacher_pending".equals(item.getReviewStage())).count();
        long approved = applications.stream().filter(item -> "approved".equals(item.getStatus())).count();
        long returned = applications.stream().filter(item -> "returned".equals(item.getStatus()) || "rejected".equals(item.getStatus())).count();
        long total = applications.size();
        long studentCount = windows.stream()
                .map(ExtraScoreSubmissionWindow::getClassName)
                .distinct()
                .mapToLong(className -> studentService.findByClassName(className).size())
                .sum();

        result.put("windowIds", windowIds);
        result.put("studentCount", studentCount);
        result.put("totalApplications", total);
        result.put("pendingCount", pending);
        result.put("committeePendingCount", committeePending);
        result.put("teacherPendingCount", teacherPending);
        result.put("approvedCount", approved);
        result.put("returnedCount", returned);
        result.put("completedCount", approved + returned);
        result.put("completionRate", total == 0 ? 0 : Math.round((approved + returned) * 1000D / total) / 10D);
        String periodStatus = resolvePeriodStatus(windows, total, pending, committeePending, teacherPending);
        result.put("periodStatus", periodStatus);
        result.put("periodStatusLabel", resolvePeriodStatusLabel(periodStatus));
        result.put("currentStage", periodStatus);
        result.put("currentStageLabel", resolvePeriodStatusLabel(periodStatus));
        boolean canComplete = context.isTeacher()
                && Boolean.TRUE.equals(result.get("enabled"))
                && total > 0
                && pending == 0;
        boolean canClose = context.isTeacher()
                && Boolean.TRUE.equals(result.get("enabled"))
                && !canComplete;
        result.put("readOnly", !context.isTeacher());
        result.put("canClose", canClose);
        result.put("canComplete", canComplete);
        result.put("primaryAction", resolvePrimaryAction(context, canClose, canComplete));
        result.put("primaryActionLabel", resolvePrimaryActionLabel(context, canClose, canComplete));
        result.put("primaryActionReason", resolvePrimaryActionReason(context, periodStatus, total, pending, applications));
        return result;
    }

    private List<ExtraScoreSubmissionWindow> findVisibleWindows(RequestUserContext context, String academicYear) {
        if (context.isAdmin()) {
            return repository.findByAcademicYearOrderByCreatedAtDescClassNameAsc(academicYear);
        }
        if (context.isTeacher()) {
            return repository.findByTeacherIdAndAcademicYearOrderByCreatedAtDescClassNameAsc(context.getTeacherId(), academicYear);
        }
        if (context.isClassCommittee()) {
            String className = defaultString(context.getClassName());
            return repository.findByAcademicYearAndClassNameOrderByCreatedAtDesc(academicYear, className);
        }
        throw new IllegalArgumentException("当前角色无权查看周期进度");
    }

    private Map<String, List<ExtraScoreSubmissionWindow>> groupWindows(List<ExtraScoreSubmissionWindow> windows, String academicYear) {
        Map<String, List<ExtraScoreSubmissionWindow>> grouped = new LinkedHashMap<>();
        for (ExtraScoreSubmissionWindow item : windows) {
            String batchCode = defaultString(item.getBatchCode());
            String key = batchCode.isBlank()
                    ? String.format("%s__%s__%s", academicYear, formatTime(item.getStartTime()), formatTime(item.getEndTime()))
                    : batchCode;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private List<ExtraScoreSubmissionWindow> findBatchWindows(ExtraScoreSubmissionWindow target) {
        String batchCode = defaultString(target.getBatchCode());
        if (batchCode.isBlank()) {
            return List.of(target);
        }
        return repository.findByTeacherIdAndAcademicYearOrderByCreatedAtDescClassNameAsc(target.getTeacherId(), target.getAcademicYear())
                .stream()
                .filter(item -> batchCode.equals(defaultString(item.getBatchCode())))
                .toList();
    }

    @Scheduled(fixedDelayString = "${extra-score.review-period.settle-delay-ms:60000}")
    @Transactional
    public void settleExpiredReviewPeriods() {
        settleExpiredReviewPeriodsInternal(LocalDateTime.now());
    }

    private void settleExpiredReviewPeriodsInternal(LocalDateTime now) {
        List<ExtraScoreSubmissionWindow> expiredWindows = repository.findByEnabledTrueAndEndTimeBefore(now);
        Set<String> settledKeys = new HashSet<>();
        for (ExtraScoreSubmissionWindow expiredWindow : expiredWindows) {
            String key = buildWindowGroupKey(expiredWindow);
            if (settledKeys.contains(key)) {
                continue;
            }
            settledKeys.add(key);
            List<ExtraScoreSubmissionWindow> windows = findBatchWindows(expiredWindow);
            settleReviewPeriod(windows, now, false);
            refreshCompositeSnapshotsForSettledPeriod(windows, 0L, "系统自动结束附加分周期", "SYSTEM");
        }
    }

    private PeriodSettlement settleReviewPeriod(List<ExtraScoreSubmissionWindow> windows, LocalDateTime settledAt, boolean overrideEndTime) {
        List<Long> windowIds = windows.stream()
                .map(ExtraScoreSubmissionWindow::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<ExtraScoreApplication> applications = windowIds.isEmpty()
                ? List.of()
                : applicationRepository.findBySubmissionWindowIdIn(windowIds);
        PeriodSettlement settlement = resetIncompleteStudentPackages(applications);

        for (ExtraScoreSubmissionWindow window : windows) {
            window.setEnabled(false);
            if (overrideEndTime || window.getEndTime() == null) {
                window.setEndTime(settledAt);
            }
        }
        repository.saveAll(windows);
        settleSubmissionPeriods(windows, settledAt);
        detachDraftLikeApplications(windowIds);
        return settlement;
    }

    private ExtraScoreSubmissionPeriod createSubmissionPeriod(
            Long teacherId,
            String academicYear,
            String batchCode,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean enabled
    ) {
        return periodRepository.findByBatchCode(batchCode).orElseGet(() -> {
            int periodNo = periodRepository
                    .findByTeacherIdAndAcademicYearOrderByPeriodNoAsc(teacherId, academicYear)
                    .stream()
                    .map(ExtraScoreSubmissionPeriod::getPeriodNo)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(-1) + 1;
            ExtraScoreSubmissionPeriod period = new ExtraScoreSubmissionPeriod();
            period.setBatchCode(batchCode);
            period.setAcademicYear(academicYear);
            period.setTeacherId(teacherId);
            period.setPeriodNo(periodNo);
            period.setPeriodType(periodNo == 0 ? "annual" : "supplemental");
            period.setDisplayName(periodNo == 0 ? "年度主周期" : "补充周期 " + periodNo);
            period.setStartTime(startTime);
            period.setEndTime(endTime);
            period.setStatus(resolveInitialPeriodStatus(enabled, startTime, endTime));
            period.setCreatedBy(teacherId);
            return periodRepository.save(period);
        });
    }

    private String resolveInitialPeriodStatus(
            boolean enabled,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        LocalDateTime now = LocalDateTime.now();
        if (!enabled || (endTime != null && now.isAfter(endTime))) {
            return "settled";
        }
        if (startTime != null && now.isBefore(startTime)) {
            return "scheduled";
        }
        return "open";
    }

    private void settleSubmissionPeriods(List<ExtraScoreSubmissionWindow> windows, LocalDateTime settledAt) {
        Set<Long> periodIds = windows.stream()
                .map(ExtraScoreSubmissionWindow::getPeriodId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (periodIds.isEmpty()) {
            return;
        }
        List<ExtraScoreSubmissionPeriod> periods = periodRepository.findAllById(periodIds);
        for (ExtraScoreSubmissionPeriod period : periods) {
            period.setStatus("settled");
            period.setSettledAt(settledAt);
            if (period.getEndTime() == null || period.getEndTime().isAfter(settledAt)) {
                period.setEndTime(settledAt);
            }
        }
        periodRepository.saveAll(periods);
    }

    private PeriodSettlement resetIncompleteStudentPackages(List<ExtraScoreApplication> applications) {
        Map<String, List<ExtraScoreApplication>> grouped = new LinkedHashMap<>();
        for (ExtraScoreApplication application : applications) {
            String key = defaultString(application.getStudentNo());
            if (key.isBlank()) {
                key = defaultString(application.getStudentName()) + "-" + defaultString(application.getClassName());
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(application);
        }

        List<ExtraScoreApplication> resetApplications = new ArrayList<>();
        int resetStudentCount = 0;
        for (List<ExtraScoreApplication> packageRecords : grouped.values()) {
            boolean studentReviewIncomplete = packageRecords.stream()
                    .anyMatch(item -> "pending".equals(defaultString(item.getStatus()).toLowerCase()));
            if (!studentReviewIncomplete) {
                continue;
            }
            resetStudentCount += 1;
            for (ExtraScoreApplication application : packageRecords) {
                resetAsDraftOutsidePeriod(application);
                resetApplications.add(application);
            }
        }

        if (!resetApplications.isEmpty()) {
            applicationRepository.saveAll(resetApplications);
        }
        return new PeriodSettlement(grouped.size(), resetStudentCount, resetApplications.size());
    }

    private void resetAsDraftOutsidePeriod(ExtraScoreApplication application) {
        application.setStatus("draft");
        application.setReviewStage("draft");
        application.setCommitteeStatus("draft");
        application.setCommitteeComment("");
        application.setCommitteeReviewerId(null);
        application.setCommitteeReviewerName(null);
        application.setCommitteeReviewedAt(null);
        application.setTeacherStatus("draft");
        application.setTeacherComment("");
        application.setTeacherReviewerId(null);
        application.setTeacherReviewerName(null);
        application.setTeacherReviewedAt(null);
        application.setReturnReason(null);
        application.setWithdrawalStatus(null);
        application.setWithdrawalRequestedAt(null);
        application.setWithdrawalReviewedAt(null);
        application.setWithdrawalReviewerId(null);
        application.setWithdrawalReviewerName(null);
        application.setWithdrawalComment(null);
        application.setSubmissionWindowId(null);
        application.setSubmissionBatchCode(null);
        application.setSubmissionPeriodLabel(null);
        application.setSubmissionStartTime(null);
        application.setSubmissionEndTime(null);
    }

    private String buildWindowGroupKey(ExtraScoreSubmissionWindow window) {
        String batchCode = defaultString(window.getBatchCode());
        if (!batchCode.isBlank()) {
            return window.getTeacherId() + "|" + defaultString(window.getAcademicYear()) + "|" + batchCode;
        }
        return "single|" + window.getId();
    }

    private String buildSettlementMessage(String prefix, PeriodSettlement settlement) {
        if (settlement.resetStudentCount() <= 0) {
            return prefix + "；所有已提交学生均已完成审核";
        }
        return "%s；%d 名未完成审核学生的 %d 条申请已退回草稿并移出本周期，下一周期开启后会自动进入下一周期"
                .formatted(prefix, settlement.resetStudentCount(), settlement.resetApplicationCount());
    }

    private Map<String, Object> refreshCompositeSnapshotsForSettledPeriod(
            List<ExtraScoreSubmissionWindow> windows,
            Long updaterId,
            String updaterName,
            String updaterRole
    ) {
        ExtraScoreSubmissionWindow first = windows.isEmpty() ? null : windows.get(0);
        if (first == null || first.getTeacherId() == null) {
            return Map.of();
        }
        String academicYear = defaultString(first.getAcademicYear());
        List<String> classNames = windows.stream()
                .map(ExtraScoreSubmissionWindow::getClassName)
                .map(this::defaultString)
                .map(String::trim)
                .filter(className -> !className.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (classNames.isEmpty()) {
            return Map.of();
        }

        List<Map<String, Object>> refreshResults = new ArrayList<>();
        int totalCount = 0;
        for (String className : classNames) {
            Map<String, Object> refreshResult = adminCompositeScoreService.refreshCompositeScores(
                    academicYear,
                    className,
                    first.getTeacherId(),
                    updaterId,
                    updaterName,
                    updaterRole
            );
            Map<String, Object> classResult = new LinkedHashMap<>(refreshResult);
            classResult.put("className", className);
            refreshResults.add(classResult);
            Object count = refreshResult.get("count");
            if (count instanceof Number number) {
                totalCount += number.intValue();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicYear", academicYear);
        result.put("classNames", classNames);
        result.put("count", totalCount);
        result.put("results", refreshResults);
        return result;
    }

    private void detachDraftLikeApplications(List<Long> windowIds) {
        if (windowIds == null || windowIds.isEmpty()) {
            return;
        }
        List<ExtraScoreApplication> draftLikeApplications = applicationRepository.findBySubmissionWindowIdIn(windowIds).stream()
                .filter(item -> isDraftLikeStatus(item.getStatus()))
                .toList();
        if (draftLikeApplications.isEmpty()) {
            return;
        }
        for (ExtraScoreApplication application : draftLikeApplications) {
            application.setSubmissionWindowId(null);
            application.setSubmissionBatchCode(null);
            application.setSubmissionPeriodLabel(null);
            application.setSubmissionStartTime(null);
            application.setSubmissionEndTime(null);
        }
        applicationRepository.saveAll(draftLikeApplications);
    }

    private boolean isDraftLikeStatus(String status) {
        String normalized = defaultString(status).toLowerCase();
        return "draft".equals(normalized) || "returned".equals(normalized) || "rejected".equals(normalized);
    }

    private String resolvePeriodStatus(List<ExtraScoreSubmissionWindow> windows, long total, long pending, long committeePending, long teacherPending) {
        ExtraScoreSubmissionWindow first = windows.isEmpty() ? null : windows.get(0);
        LocalDateTime now = LocalDateTime.now();
        boolean enabled = first != null && Boolean.TRUE.equals(first.getEnabled());
        if (!enabled) {
            return total > 0 && pending == 0 ? "completed" : "closed";
        }
        if (first != null && first.getStartTime() != null && now.isBefore(first.getStartTime())) {
            return "scheduled";
        }
        if (first != null && first.getEndTime() != null && now.isAfter(first.getEndTime())) {
            return total > 0 && pending == 0 ? "completed" : "closed";
        }
        if (committeePending > 0) {
            return "committee_review";
        }
        if (teacherPending > 0) {
            return "teacher_review";
        }
        if (pending > 0) {
            return "teacher_review";
        }
        if (total > 0) {
            return "ready_to_complete";
        }
        return "collecting";
    }

    private String resolvePeriodStatusLabel(String status) {
        return switch (status) {
            case "scheduled" -> "周期未开始";
            case "collecting" -> "学生提交中";
            case "committee_review" -> "班委审核阶段";
            case "teacher_review" -> "老师处理阶段";
            case "ready_to_complete" -> "待完成审核";
            case "completed" -> "审核完成";
            case "closed" -> "周期已结束";
            default -> "提交周期";
        };
    }

    private String resolvePrimaryAction(RequestUserContext context, boolean canClose, boolean canComplete) {
        if (!context.isTeacher()) {
            return "view";
        }
        if (canComplete) {
            return "complete";
        }
        if (canClose) {
            return "close";
        }
        return "view";
    }

    private String resolvePrimaryActionLabel(RequestUserContext context, boolean canClose, boolean canComplete) {
        if (!context.isTeacher()) {
            return "查看详情";
        }
        if (canComplete) {
            return "完成审核";
        }
        if (canClose) {
            return "提前结束";
        }
        return "查看详情";
    }

    private String resolvePrimaryActionReason(
            RequestUserContext context,
            String periodStatus,
            long total,
            long pending,
            List<ExtraScoreApplication> applications
    ) {
        if (!context.isTeacher()) {
            return "管理员和班委仅可查看周期进度";
        }
        if ("completed".equals(periodStatus)) {
            return "当前周期已完成审核";
        }
        if ("closed".equals(periodStatus)) {
            return "当前周期已结束";
        }
        if (total <= 0) {
            return "当前周期暂无学生提交记录";
        }
        if (pending > 0) {
            String details = buildPendingClassSummary(applications);
            return details.isBlank()
                    ? "仍有待审核申请，不能完成审核"
                    : "仍有待审核申请：" + details;
        }
        return "";
    }

    private String buildPendingClassSummary(List<ExtraScoreApplication> applications) {
        if (applications == null || applications.isEmpty()) {
            return "";
        }
        Map<String, PendingClassCounter> grouped = new LinkedHashMap<>();
        applications.stream()
                .filter(item -> "pending".equals(defaultString(item.getStatus()).toLowerCase()))
                .sorted(Comparator.comparing(item -> defaultString(item.getClassName())))
                .forEach(item -> {
                    String className = defaultString(item.getClassName()).trim();
                    if (className.isBlank()) {
                        className = "未分班";
                    }
                    PendingClassCounter counter = grouped.computeIfAbsent(className, ignored -> new PendingClassCounter());
                    String reviewStage = defaultString(item.getReviewStage()).toLowerCase();
                    if ("committee_pending".equals(reviewStage)) {
                        counter.committeePending += 1;
                    } else if ("teacher_pending".equals(reviewStage)) {
                        counter.teacherPending += 1;
                    } else {
                        counter.otherPending += 1;
                    }
                });
        List<String> classSummaries = new ArrayList<>();
        for (Map.Entry<String, PendingClassCounter> entry : grouped.entrySet()) {
            PendingClassCounter counter = entry.getValue();
            List<String> parts = new ArrayList<>();
            if (counter.committeePending > 0) {
                parts.add("班委待审 " + counter.committeePending + " 条");
            }
            if (counter.teacherPending > 0) {
                parts.add("老师待审 " + counter.teacherPending + " 条");
            }
            if (counter.otherPending > 0) {
                parts.add("待审 " + counter.otherPending + " 条");
            }
            if (!parts.isEmpty()) {
                classSummaries.add(entry.getKey() + "（" + String.join("，", parts) + "）");
            }
        }
        return String.join("；", classSummaries);
    }

    private static class PendingClassCounter {
        private long committeePending;
        private long teacherPending;
        private long otherPending;
    }

    private void applyBatchPeriodLabels(List<Map<String, Object>> periods) {
        Map<String, List<Map<String, Object>>> groupedByYear = new LinkedHashMap<>();
        for (Map<String, Object> period : periods) {
            String year = String.valueOf(period.getOrDefault("academicYear", ""));
            groupedByYear.computeIfAbsent(year, ignored -> new ArrayList<>()).add(period);
        }
        for (List<Map<String, Object>> yearPeriods : groupedByYear.values()) {
            yearPeriods.sort(Comparator.comparing(
                    item -> String.valueOf(item.getOrDefault("startTime", item.getOrDefault("createdAt", "")))
            ));
            for (int index = 0; index < yearPeriods.size(); index++) {
                yearPeriods.get(index).put("periodLabel", index == 0 ? "年度主周期" : "补充周期 " + index);
            }
        }
    }

    public String resolvePeriodLabel(ExtraScoreSubmissionWindow window) {
        if (window == null) {
            return "";
        }
        List<ExtraScoreSubmissionWindow> records = window.getTeacherId() == null
                ? repository.findByAcademicYearAndClassNameOrderByCreatedAtDesc(
                        window.getAcademicYear(),
                        window.getClassName()
                )
                : repository.findByTeacherIdAndAcademicYearOrderByCreatedAtDescClassNameAsc(
                        window.getTeacherId(),
                        window.getAcademicYear()
                );
        return buildPeriodLabelsByBatch(records).getOrDefault(resolvePeriodBatchKey(window), "提交周期");
    }

    public Map<Long, Map<String, Object>> getStudentProcessWindowStates(Collection<Long> windowIds) {
        if (windowIds == null || windowIds.isEmpty()) {
            return Map.of();
        }
        List<ExtraScoreSubmissionWindow> windows = repository.findAllById(windowIds).stream().toList();
        Map<String, List<ExtraScoreSubmissionWindow>> windowsByScope = new LinkedHashMap<>();
        for (ExtraScoreSubmissionWindow window : windows) {
            String scopeKey = window.getTeacherId() + "|" + defaultString(window.getAcademicYear());
            windowsByScope.computeIfAbsent(scopeKey, ignored -> repository
                    .findByTeacherIdAndAcademicYearOrderByCreatedAtDescClassNameAsc(
                            window.getTeacherId(),
                            window.getAcademicYear()
                    ));
        }

        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (ExtraScoreSubmissionWindow window : windows) {
            String scopeKey = window.getTeacherId() + "|" + defaultString(window.getAcademicYear());
            Map<String, String> labels = buildPeriodLabelsByBatch(windowsByScope.getOrDefault(scopeKey, List.of()));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("periodLabel", labels.getOrDefault(resolvePeriodBatchKey(window), "提交周期"));
            state.put("periodSettled", !Boolean.TRUE.equals(window.getEnabled()));
            state.put("scoreSnapshotUpdated", !Boolean.TRUE.equals(window.getEnabled()));
            result.put(window.getId(), state);
        }
        return result;
    }

    private Map<String, String> buildPeriodLabelsByBatch(List<ExtraScoreSubmissionWindow> windows) {
        Comparator<ExtraScoreSubmissionWindow> periodOrder = Comparator.comparing(
                ExtraScoreSubmissionWindow::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).thenComparing(
                ExtraScoreSubmissionWindow::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).thenComparing(
                ExtraScoreSubmissionWindow::getId,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
        Map<String, ExtraScoreSubmissionWindow> representativeByBatch = new LinkedHashMap<>();
        for (ExtraScoreSubmissionWindow window : windows) {
            representativeByBatch.merge(
                    resolvePeriodBatchKey(window),
                    window,
                    (left, right) -> periodOrder.compare(left, right) <= 0 ? left : right
            );
        }
        List<Map.Entry<String, ExtraScoreSubmissionWindow>> batches = representativeByBatch.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(periodOrder))
                .toList();
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < batches.size(); index++) {
            labels.put(batches.get(index).getKey(), index == 0 ? "年度主周期" : "补充周期 " + index);
        }
        return labels;
    }

    private String resolvePeriodBatchKey(ExtraScoreSubmissionWindow window) {
        String batchCode = defaultString(window.getBatchCode()).trim();
        if (!batchCode.isBlank()) {
            return batchCode;
        }
        return String.format(
                "%s__%s__%s",
                defaultString(window.getAcademicYear()),
                formatTime(window.getStartTime()),
                formatTime(window.getEndTime())
        );
    }

    public String resolveCurrentAcademicYear() {
        return resolveCurrentAcademicYear(null);
    }

    public String resolveCurrentAcademicYear(String academicYear) {
        if (academicYear != null && !academicYear.isBlank()) {
            return academicYear.trim();
        }
        AcademicYear current = academicYearService.findCurrent()
                .orElseThrow(() -> new IllegalArgumentException("当前学年未配置"));
        return current.getName();
    }

    private LocalDateTime parseTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + "格式错误");
        }
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "-" : value.toString().replace('T', ' ').substring(0, 16);
    }

    private String buildBatchCode(Long teacherId, String academicYear, LocalDateTime startTime, LocalDateTime endTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String start = startTime == null ? "nostart" : startTime.format(formatter);
        String end = endTime == null ? "noend" : endTime.format(formatter);
        return String.format("%s-%s-%s-%s", teacherId, academicYear, start, end);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record PeriodSettlement(int studentCount, int resetStudentCount, int resetApplicationCount) {
        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("studentCount", studentCount);
            result.put("resetStudentCount", resetStudentCount);
            result.put("resetApplicationCount", resetApplicationCount);
            return result;
        }
    }
}
