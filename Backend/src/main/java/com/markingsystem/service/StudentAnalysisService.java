package com.markingsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.AdminAnalysisOverviewDTO;
import com.markingsystem.dto.BatchAnalysisTaskDTO;
import com.markingsystem.dto.StudentAnalysisPayloadDTO;
import com.markingsystem.dto.StudentAnalysisResultDTO;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.IntellectualEducation;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.PhysicalEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.StudentAnalysisRecord;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.StudentAnalysisRecordRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import com.markingsystem.service.analysis.DeepSeekAnalysisClient;
import com.markingsystem.service.analysis.StudentAnalysisGraphState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StudentAnalysisService {

    private final StudentService studentService;
    private final MoralEducationService moralEducationService;
    private final IntellectualEducationService intellectualEducationService;
    private final PhysicalEducationService physicalEducationService;
    private final SubjectScoreRAINRepository subjectScoreRAINRepository;
    private final ExtraScoreApplicationRepository extraScoreApplicationRepository;
    private final StudentAnalysisRecordRepository studentAnalysisRecordRepository;
    private final DeepSeekAnalysisClient deepSeekAnalysisClient;
    private final ObjectMapper objectMapper;
    private final ExtraScoreEffectiveScoreService effectiveScoreService;
    private final ConcurrentMap<String, BatchTaskState> batchTasks = new ConcurrentHashMap<>();

    public StudentAnalysisService(
            StudentService studentService,
            MoralEducationService moralEducationService,
            IntellectualEducationService intellectualEducationService,
            PhysicalEducationService physicalEducationService,
            SubjectScoreRAINRepository subjectScoreRAINRepository,
            ExtraScoreApplicationRepository extraScoreApplicationRepository,
            StudentAnalysisRecordRepository studentAnalysisRecordRepository,
            ExtraScoreEffectiveScoreService effectiveScoreService,
            ObjectMapper objectMapper,
            @Value("${ai.deepseek.api-key:}") String deepSeekApiKey,
            @Value("${ai.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
            @Value("${ai.deepseek.model:deepseek-chat}") String deepSeekModel,
            @Value("${ai.analysis.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.analysis.read-timeout-ms:20000}") int readTimeoutMs) {
        this.studentService = studentService;
        this.moralEducationService = moralEducationService;
        this.intellectualEducationService = intellectualEducationService;
        this.physicalEducationService = physicalEducationService;
        this.subjectScoreRAINRepository = subjectScoreRAINRepository;
        this.extraScoreApplicationRepository = extraScoreApplicationRepository;
        this.studentAnalysisRecordRepository = studentAnalysisRecordRepository;
        this.effectiveScoreService = effectiveScoreService;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        this.deepSeekAnalysisClient = new DeepSeekAnalysisClient(
                restTemplate,
                objectMapper,
                deepSeekApiKey,
                deepSeekBaseUrl,
                deepSeekModel
        );
    }

    public Optional<StudentAnalysisPayloadDTO> buildPayload(String studentNo, Long userId) {
        Optional<Student> studentOpt = resolveStudent(studentNo, userId);
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }

        Student student = studentOpt.get();
        List<MoralEducation> morals = moralEducationService.findByStudentId(student.getId());
        List<IntellectualEducation> intellectuals = intellectualEducationService.findByStudentId(student.getId());
        List<PhysicalEducation> physicals = physicalEducationService.findByStudentId(student.getId());
        List<SubjectScoreRAIN> subjectScores = subjectScoreRAINRepository.findByStudentId(student.getId());
        List<ExtraScoreApplication> applications = extraScoreApplicationRepository.findByStudentNoAndStatus(student.getStudentNo(), "approved");

        StudentAnalysisPayloadDTO payload = new StudentAnalysisPayloadDTO();
        payload.setStudentId(student.getId());
        payload.setStudentNo(student.getStudentNo());
        payload.setStudentName(student.getName());
        payload.setClassName(student.getClassName());
        payload.setTeacherId(student.getTeacherId());

        // 不采集手机号 / 邮箱等个人隐私信息：画像分析仅依赖学业维度数据，
        // 避免把 PII 发送给第三方 LLM 并落库到 payload_snapshot。
        StudentAnalysisPayloadDTO.BasicProfile basicProfile = new StudentAnalysisPayloadDTO.BasicProfile();
        payload.setBasicProfile(basicProfile);

        StudentAnalysisPayloadDTO.DimensionScores dimensionScores = new StudentAnalysisPayloadDTO.DimensionScores();
        dimensionScores.setMoralAverage(round(average(morals.stream().map(MoralEducation::getTotalScore).toList())));
        dimensionScores.setIntellectualAverage(round(average(intellectuals.stream().map(IntellectualEducation::getTotalScore).toList())));
        dimensionScores.setPhysicalAverage(round(average(physicals.stream().map(PhysicalEducation::getTotalScore).toList())));
        payload.setDimensionScores(dimensionScores);

        payload.setCourseStatistics(buildCourseStatistics(subjectScores));
        payload.setExtraScoreStatistics(buildExtraScoreStatistics(applications));
        payload.setSemesterSummaries(buildSemesterSummaries(morals, intellectuals, physicals, subjectScores, applications));
        return Optional.of(payload);
    }

    public Optional<StudentAnalysisResultDTO> analyzeStudent(String studentNo, Long userId) {
        return analyzeAndSaveStudent(studentNo, userId);
    }

    public Optional<StudentAnalysisResultDTO> getLatestAnalysis(String studentNo, Long userId) {
        Optional<Student> studentOpt = resolveStudent(studentNo, userId);
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        return studentAnalysisRecordRepository
                .findTopByStudentNoOrderByGeneratedAtDesc(studentOpt.get().getStudentNo())
                .map(this::toResultDto);
    }

    public List<StudentAnalysisResultDTO> getAnalysisHistory(String studentNo) {
        return studentAnalysisRecordRepository.findByStudentNoOrderByGeneratedAtDesc(studentNo)
                .stream()
                .map(this::toResultDto)
                .toList();
    }

    /**
     * 教工端（管理员 / 教师）访问学生画像前的权限解析。
     * 管理员可访问任意学生；教师仅可访问自己班级（teacherId 匹配）的学生。
     * 无权访问或学生不存在时返回 Optional.empty()，由调用方统一返回 404，避免泄露学生是否存在。
     */
    public Optional<Student> resolveStudentForStaff(String studentNo, String role, Long teacherId) {
        if (studentNo == null || studentNo.isBlank()) {
            return Optional.empty();
        }
        Optional<Student> studentOpt = studentService.findByStudentNo(studentNo.trim());
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        if ("TEACHER".equalsIgnoreCase(role)) {
            Student student = studentOpt.get();
            if (teacherId == null || !teacherId.equals(student.getTeacherId())) {
                return Optional.empty();
            }
        }
        return studentOpt;
    }

    public Optional<StudentAnalysisResultDTO> analyzeAndSaveStudent(String studentNo, Long userId) {
        Optional<StudentAnalysisPayloadDTO> payloadOpt = buildPayload(studentNo, userId);
        if (payloadOpt.isEmpty()) {
            return Optional.empty();
        }

        StudentAnalysisPayloadDTO payload = payloadOpt.get();
        StudentAnalysisResultDTO result;
        try {
            if (deepSeekAnalysisClient.isEnabled()) {
                StudentAnalysisGraphState state = new StudentAnalysisGraphState();
                state.setPayload(payload);
                state.setFeatureSnapshot(buildFeatureSnapshot(payload));
                state.setLlmEnabled(true);
                result = deepSeekAnalysisClient.analyze(state);
            } else {
                result = buildFallbackResult(payload);
            }
        } catch (Exception ex) {
            result = buildFallbackResult(payload);
            addFallbackReason(result.getFeatureSnapshot(), ex);
        }

        if (result == null) {
            return Optional.empty();
        }

        saveAnalysisRecord(payload, result);
        return Optional.of(result);
    }

    public BatchAnalysisTaskDTO startBatchAnalyzeAndSave(String keyword, String className, String role, Long teacherId, List<String> studentNos) {
        List<Student> students = filterStudents(keyword, className, role, teacherId, studentNos);
        BatchTaskState task = new BatchTaskState();
        task.taskId = UUID.randomUUID().toString();
        task.totalCount.set(students.size());
        task.startedAt = LocalDateTime.now();
        task.status = "running";
        task.message = "Batch analysis running";
        batchTasks.put(task.taskId, task);

        CompletableFuture.runAsync(() -> {
            try {
                for (Student student : students) {
                    if (task.cancelRequested.get()) {
                        task.status = "cancelled";
                        task.message = "Task cancelled";
                        task.finishedAt = LocalDateTime.now();
                        return;
                    }
                    if (analyzeAndSaveStudent(student.getStudentNo(), null).isPresent()) {
                        task.successCount.incrementAndGet();
                    }
                    task.processedCount.incrementAndGet();
                }
                task.status = "completed";
                task.message = "Batch analysis completed";
                task.finishedAt = LocalDateTime.now();
            } catch (Exception ex) {
                task.status = "failed";
                task.message = "Batch analysis failed: " + ex.getMessage();
                task.finishedAt = LocalDateTime.now();
            }
        });

        return toBatchTaskDto(task);
    }

    public Optional<BatchAnalysisTaskDTO> getBatchTask(String taskId) {
        BatchTaskState task = batchTasks.get(taskId);
        return task == null ? Optional.empty() : Optional.of(toBatchTaskDto(task));
    }

    public Optional<BatchAnalysisTaskDTO> cancelBatchTask(String taskId) {
        BatchTaskState task = batchTasks.get(taskId);
        if (task == null) {
            return Optional.empty();
        }
        task.cancelRequested.set(true);
        task.message = "Cancelling task";
        return Optional.of(toBatchTaskDto(task));
    }

    public AdminAnalysisOverviewDTO buildOverview(String keyword, String className, String role, Long teacherId) {
        List<Student> students = filterStudents(keyword, className, role, teacherId);
        List<String> studentNos = students.stream().map(Student::getStudentNo).filter(Objects::nonNull).toList();

        Map<String, StudentAnalysisRecord> latestRecordMap = studentAnalysisRecordRepository
                .findByStudentNoInOrderByGeneratedAtDesc(studentNos)
                .stream()
                .collect(Collectors.toMap(
                        StudentAnalysisRecord::getStudentNo,
                        Function.identity(),
                        (left, right) -> left
                ));

        AdminAnalysisOverviewDTO overview = new AdminAnalysisOverviewDTO();
        overview.setTotalStudents(students.size());
        overview.setAnalyzedStudents(latestRecordMap.size());
        overview.setClassCount((int) students.stream().map(Student::getClassName).filter(Objects::nonNull).distinct().count());
        overview.setTypeDistribution(buildTypeDistribution(latestRecordMap.values(), students.size()));
        overview.setClassDistributions(buildClassDistribution(students, latestRecordMap));
        return overview;
    }

    private void saveAnalysisRecord(StudentAnalysisPayloadDTO payload, StudentAnalysisResultDTO result) {
        try {
            if (payload == null || result == null) {
                return;
            }
            StudentAnalysisRecord record = new StudentAnalysisRecord();
            record.setStudentId(payload.getStudentId());
            record.setStudentNo(payload.getStudentNo());
            record.setStudentName(payload.getStudentName());
            record.setClassName(payload.getClassName());
            record.setTeacherId(payload.getTeacherId());
            record.setPrimaryType(result.getPrimaryType());
            record.setSecondaryTypes(objectMapper.writeValueAsString(result.getSecondaryTypes()));
            record.setConfidence(result.getConfidence());
            record.setSummary(result.getSummary());
            record.setReasons(objectMapper.writeValueAsString(result.getReasons()));
            record.setSuggestions(objectMapper.writeValueAsString(result.getSuggestions()));
            Map<String, Object> snapshotForSave = new LinkedHashMap<>(result.getFeatureSnapshot());
            if (result.getRadarDimensions() != null && !result.getRadarDimensions().isEmpty()) {
                snapshotForSave.put("radarDimensions", result.getRadarDimensions());
            }
            record.setFeatureSnapshot(objectMapper.writeValueAsString(snapshotForSave));
            record.setSource(result.getSource());
            record.setModelName(result.getModel());
            record.setPayloadSnapshot(objectMapper.writeValueAsString(payload));
            record.setGeneratedAt(result.getGeneratedAt() != null ? result.getGeneratedAt() : LocalDateTime.now());
            studentAnalysisRecordRepository.save(record);
        } catch (Exception ignored) {
        }
    }

    private StudentAnalysisResultDTO toResultDto(StudentAnalysisRecord record) {
        StudentAnalysisResultDTO result = new StudentAnalysisResultDTO();
        result.setPrimaryType(record.getPrimaryType());
        result.setConfidence(record.getConfidence());
        result.setSummary(record.getSummary());
        result.setSource(record.getSource());
        result.setModel(record.getModelName());
        result.setGeneratedAt(record.getGeneratedAt());
        try {
            result.setSecondaryTypes(readStringList(record.getSecondaryTypes()));
            result.setReasons(readStringList(record.getReasons()));
            result.setSuggestions(readStringList(record.getSuggestions()));
            Map<String, Object> snapshotMap = readMap(record.getFeatureSnapshot());
            result.setFeatureSnapshot(snapshotMap);
            result.setRadarDimensions(extractRadarDimensions(snapshotMap));
        } catch (Exception ignored) {
            result.setSecondaryTypes(new ArrayList<>());
            result.setReasons(new ArrayList<>());
            result.setSuggestions(new ArrayList<>());
            result.setFeatureSnapshot(new LinkedHashMap<>());
            result.setRadarDimensions(new LinkedHashMap<>());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractRadarDimensions(Map<String, Object> snapshotMap) {
        Map<String, Double> dimensions = new LinkedHashMap<>();
        if (snapshotMap == null || snapshotMap.isEmpty()) {
            return dimensions;
        }
        Object raw = snapshotMap.get("radarDimensions");
        if (raw == null) {
            return dimensions;
        }
        if (raw instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) raw).entrySet()) {
                double value = 0D;
                if (entry.getValue() instanceof Number num) {
                    value = num.doubleValue();
                }
                dimensions.put(entry.getKey(), round(value));
            }
        }
        return dimensions;
    }

    private StudentAnalysisPayloadDTO.CourseStatistics buildCourseStatistics(List<SubjectScoreRAIN> subjectScores) {
        StudentAnalysisPayloadDTO.CourseStatistics courseStatistics = new StudentAnalysisPayloadDTO.CourseStatistics();
        List<Double> values = subjectScores.stream()
                .map(SubjectScoreRAIN::getScore)
                .filter(Objects::nonNull)
                .toList();

        courseStatistics.setOverallAverage(round(average(values)));
        courseStatistics.setScoreCount(values.size());
        courseStatistics.setExcellentCount((int) values.stream().filter(score -> score >= 85).count());
        courseStatistics.setPassCount((int) values.stream().filter(score -> score >= 60).count());

        Map<String, List<Double>> subjectScoreMap = new LinkedHashMap<>();
        for (SubjectScoreRAIN score : subjectScores) {
            if (score.getSubject() == null || score.getSubject().getSubjectName() == null || score.getScore() == null) {
                continue;
            }
            subjectScoreMap.computeIfAbsent(score.getSubject().getSubjectName(), key -> new ArrayList<>()).add(score.getScore());
        }

        List<StudentAnalysisPayloadDTO.SubjectStat> stats = subjectScoreMap.entrySet().stream()
                .map(entry -> {
                    StudentAnalysisPayloadDTO.SubjectStat stat = new StudentAnalysisPayloadDTO.SubjectStat();
                    stat.setSubjectName(entry.getKey());
                    stat.setScoreCount(entry.getValue().size());
                    stat.setAverageScore(round(average(entry.getValue())));
                    return stat;
                })
                .sorted(Comparator.comparing(StudentAnalysisPayloadDTO.SubjectStat::getAverageScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .toList();

        courseStatistics.setTopSubjects(new ArrayList<>(stats.stream().limit(3).toList()));
        courseStatistics.setWeakSubjects(new ArrayList<>(stats.stream()
                .sorted(Comparator.comparing(StudentAnalysisPayloadDTO.SubjectStat::getAverageScore, Comparator.nullsLast(Double::compareTo)))
                .limit(3)
                .toList()));
        return courseStatistics;
    }

    private StudentAnalysisPayloadDTO.ExtraScoreStatistics buildExtraScoreStatistics(List<ExtraScoreApplication> applications) {
        StudentAnalysisPayloadDTO.ExtraScoreStatistics extraScoreStatistics = new StudentAnalysisPayloadDTO.ExtraScoreStatistics();
        extraScoreStatistics.setApprovedCount(applications.size());
        extraScoreStatistics.setApprovedTotalScore(round(effectiveScoreService.sumEffectiveSignedScores(applications)));
        extraScoreStatistics.setCompetitionCount((int) applications.stream().filter(this::isCompetitionRelated).count());
        extraScoreStatistics.setResearchCount((int) applications.stream().filter(this::isResearchRelated).count());
        extraScoreStatistics.setMoralCount((int) applications.stream().filter(app -> "moral".equalsIgnoreCase(app.getScoreType())).count());
        extraScoreStatistics.setPhysicalCount((int) applications.stream().filter(app -> "physical".equalsIgnoreCase(app.getScoreType())).count());
        return extraScoreStatistics;
    }

    private List<StudentAnalysisPayloadDTO.SemesterSummary> buildSemesterSummaries(
            List<MoralEducation> morals,
            List<IntellectualEducation> intellectuals,
            List<PhysicalEducation> physicals,
            List<SubjectScoreRAIN> subjectScores,
            List<ExtraScoreApplication> applications) {
        Map<String, StudentAnalysisPayloadDTO.SemesterSummary> summaryMap = new TreeMap<>();

        morals.stream()
                .filter(item -> item.getSemester() != null && !item.getSemester().isBlank())
                .forEach(item -> summaryMap.computeIfAbsent(item.getSemester(), this::newSemesterSummary)
                        .setMoralScore(round(item.getTotalScore())));
        intellectuals.stream()
                .filter(item -> item.getSemester() != null && !item.getSemester().isBlank())
                .forEach(item -> summaryMap.computeIfAbsent(item.getSemester(), this::newSemesterSummary)
                        .setIntellectualScore(round(item.getTotalScore())));
        physicals.stream()
                .filter(item -> item.getSemester() != null && !item.getSemester().isBlank())
                .forEach(item -> summaryMap.computeIfAbsent(item.getSemester(), this::newSemesterSummary)
                        .setPhysicalScore(round(item.getTotalScore())));

        subjectScores.stream()
                .filter(score -> score.getSemester() != null && score.getScore() != null)
                .collect(Collectors.groupingBy(SubjectScoreRAIN::getSemester,
                        TreeMap::new,
                        Collectors.mapping(SubjectScoreRAIN::getScore, Collectors.toList())))
                .forEach((semester, values) -> summaryMap.computeIfAbsent(semester, this::newSemesterSummary)
                        .setCourseAverage(round(average(values))));

        applications.stream()
                .filter(app -> app.getAwardTime() != null)
                .collect(Collectors.groupingBy(app -> String.valueOf(app.getAwardTime().getYear()), TreeMap::new, Collectors.toList()))
                .forEach((year, values) -> summaryMap.computeIfAbsent(year, this::newSemesterSummary)
                        .setApprovedExtraScore(round(effectiveScoreService.sumEffectiveSignedScores(values))));

        return new ArrayList<>(summaryMap.values());
    }

    private StudentAnalysisPayloadDTO.SemesterSummary newSemesterSummary(String semester) {
        StudentAnalysisPayloadDTO.SemesterSummary summary = new StudentAnalysisPayloadDTO.SemesterSummary();
        summary.setSemester(semester);
        summary.setApprovedExtraScore(0D);
        return summary;
    }

    private StudentAnalysisResultDTO buildFallbackResult(StudentAnalysisPayloadDTO payload) {
        if (payload == null) {
            return null;
        }
        double intellectual = safe(payload.getDimensionScores() != null ? payload.getDimensionScores().getIntellectualAverage() : null);
        double moral = safe(payload.getDimensionScores() != null ? payload.getDimensionScores().getMoralAverage() : null);
        double physical = safe(payload.getDimensionScores() != null ? payload.getDimensionScores().getPhysicalAverage() : null);
        double courseAverage = safe(payload.getCourseStatistics() != null ? payload.getCourseStatistics().getOverallAverage() : null);
        int competitionCount = payload.getExtraScoreStatistics() != null ? safeInt(payload.getExtraScoreStatistics().getCompetitionCount()) : 0;
        int researchCount = payload.getExtraScoreStatistics() != null ? safeInt(payload.getExtraScoreStatistics().getResearchCount()) : 0;
        int moralCount = payload.getExtraScoreStatistics() != null ? safeInt(payload.getExtraScoreStatistics().getMoralCount()) : 0;

        int graduateSignal = score(intellectual >= 82, 32)
                + score(courseAverage >= 80, 28)
                + score(researchCount >= 1, 20)
                + score(payload.getCourseStatistics() != null && safeInt(payload.getCourseStatistics().getExcellentCount()) >= 3, 20);
        int competitionSignal = score(competitionCount >= 2, 40)
                + score(intellectual >= 78, 20)
                + score(courseAverage >= 78, 20)
                + score(payload.getCourseStatistics() != null && safeInt(payload.getCourseStatistics().getExcellentCount()) >= 2, 20);
        int civilSignal = score(moral >= 82, 36)
                + score(physical >= 70, 18)
                + score(moralCount >= 2, 24)
                + score(courseAverage >= 72, 22);

        String primaryType = "考研";
        int confidence = graduateSignal;
        if (competitionSignal > confidence) {
            primaryType = "竞赛型人才";
            confidence = competitionSignal;
        }
        if (civilSignal > confidence) {
            primaryType = "考公";
            confidence = civilSignal;
        }

        List<String> secondaryTypes = new ArrayList<>();
        if (!"考研".equals(primaryType)) {
            secondaryTypes.add("考研");
        }
        if (!"考公".equals(primaryType)) {
            secondaryTypes.add("考公");
        }
        if (!"竞赛型人才".equals(primaryType)) {
            secondaryTypes.add("竞赛型人才");
        }

        StudentAnalysisResultDTO result = new StudentAnalysisResultDTO();
        result.setPrimaryType(primaryType);
        result.setSecondaryTypes(secondaryTypes.stream().limit(2).toList());
        result.setConfidence(Math.min(95, Math.max(confidence, 60)));
        result.setSummary(buildSummary(primaryType));
        result.setReasons(buildReasons(primaryType, payload));
        result.setSuggestions(buildSuggestions(primaryType));
        result.setFeatureSnapshot(buildFeatureSnapshot(payload));
        result.setRadarDimensions(buildRadarDimensions(payload));
        result.setSource("java-graph+rules");
        result.setModel("rule-engine");
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }

    private Map<String, Double> buildRadarDimensions(StudentAnalysisPayloadDTO payload) {
        StudentAnalysisPayloadDTO.DimensionScores scores = payload.getDimensionScores();
        StudentAnalysisPayloadDTO.CourseStatistics course = payload.getCourseStatistics();
        StudentAnalysisPayloadDTO.ExtraScoreStatistics extra = payload.getExtraScoreStatistics();

        double moralAverage = scores != null ? safe(scores.getMoralAverage()) : 0;
        double intellectualAverage = scores != null ? safe(scores.getIntellectualAverage()) : 0;
        double physicalAverage = scores != null ? safe(scores.getPhysicalAverage()) : 0;
        double courseAverage = course != null ? safe(course.getOverallAverage()) : 0;
        int excellentCount = course != null ? safeInt(course.getExcellentCount()) : 0;
        int competitionCount = extra != null ? safeInt(extra.getCompetitionCount()) : 0;
        int researchCount = extra != null ? safeInt(extra.getResearchCount()) : 0;
        int moralCount = extra != null ? safeInt(extra.getMoralCount()) : 0;
        int physicalCount = extra != null ? safeInt(extra.getPhysicalCount()) : 0;
        double approvedExtraScore = extra != null ? safe(extra.getApprovedTotalScore()) : 0;

        double moral = clamp(moralAverage * 0.7 + Math.min(moralCount * 10, 30));
        double academic = clamp(courseAverage * 0.5 + intellectualAverage * 0.3 + Math.min(excellentCount * 5, 20));
        double research = clamp(Math.min(researchCount * 25, 50) + Math.min(competitionCount * 10, 50));
        double practice = clamp(Math.min(moralCount * 15, 45) + Math.min(approvedExtraScore * 2, 55));
        double artsSports = clamp(physicalAverage * 0.6 + Math.min(physicalCount * 15, 40));
        double skills = clamp(Math.min(competitionCount * 15, 50) + Math.min(excellentCount * 5, 50));
        double comprehensive = clamp((moral + academic + research + practice + artsSports + skills) / 6);
        double potential = clamp(courseAverage * 0.3 + Math.min(approvedExtraScore * 1.5, 40) + Math.min(excellentCount * 3, 30));

        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put("思想品德", round(moral));
        dimensions.put("学业成绩", round(academic));
        dimensions.put("科研创新", round(research));
        dimensions.put("实践服务", round(practice));
        dimensions.put("文体素养", round(artsSports));
        dimensions.put("技能素养", round(skills));
        dimensions.put("综合素质", round(comprehensive));
        dimensions.put("发展潜力", round(potential));
        return dimensions;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private String buildSummary(String primaryType) {
        return switch (primaryType) {
            case "考公" -> "当前画像更偏向稳定执行、综合素质和长期规划，适合往考公方向准备。";
            case "竞赛型人才" -> "当前画像更偏向项目实践和高强度挑战，适合往竞赛、科研和技术突破方向发展。";
            default -> "当前画像更偏向学术积累和课程能力，适合优先准备考研路线。";
        };
    }

    private List<String> buildReasons(String primaryType, StudentAnalysisPayloadDTO payload) {
        List<String> reasons = new ArrayList<>();
        StudentAnalysisPayloadDTO.DimensionScores scores = payload.getDimensionScores();
        StudentAnalysisPayloadDTO.CourseStatistics course = payload.getCourseStatistics();
        StudentAnalysisPayloadDTO.ExtraScoreStatistics extra = payload.getExtraScoreStatistics();

        if (scores != null) {
            reasons.add(String.format(Locale.US,
                    "德育 %.1f、智育 %.1f、体育 %.1f，整体学业绩效稳定，智育与课程表现匹配。",
                    safe(scores.getMoralAverage()),
                    safe(scores.getIntellectualAverage()),
                    safe(scores.getPhysicalAverage())));
        }
        if (course != null) {
            int scoreCount = safeInt(course.getScoreCount());
            int excellentCount = safeInt(course.getExcellentCount());
            double excellentRatio = scoreCount == 0 ? 0D : round(excellentCount * 100D / scoreCount);
            reasons.add(String.format(Locale.US,
                    "课程总体平均分 %.1f，共采集 %d 门课程成绩，其中优秀课程 %d 门（占比 %.1f%%），学业基础较为扎实。",
                    safe(course.getOverallAverage()),
                    scoreCount,
                    excellentCount,
                    excellentRatio));
        }
        if (course != null && course.getTopSubjects() != null && !course.getTopSubjects().isEmpty()) {
            String topNames = course.getTopSubjects().stream()
                    .map(StudentAnalysisPayloadDTO.SubjectStat::getSubjectName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("、"));
            if (!topNames.isBlank()) {
                reasons.add(String.format("优势科目集中在 %s，理论型与体系化课程表现较好。", topNames));
            }
        }
        if (extra != null) {
            if (safeInt(extra.getApprovedCount()) == 0) {
                reasons.add("未检索到已通过的竞赛、科研或实践类附加分记录，目前不具备竞赛型人才或考公的明显特质。");
            } else {
                reasons.add(String.format(Locale.US,
                        "已通过附加分 %d 条，总分 %.1f，其中竞赛相关 %d 条、科研相关 %d 条、道德实践类 %d 条，课外活动较为有限。",
                        safeInt(extra.getApprovedCount()),
                        safe(extra.getApprovedTotalScore()),
                        safeInt(extra.getCompetitionCount()),
                        safeInt(extra.getResearchCount()),
                        safeInt(extra.getMoralCount())));
            }
        }
        reasons.add(switch (primaryType) {
            case "考公" -> "德育与综合素养表现稳定，建议补充志愿服务、基层实践和班委经历以贴合考公方向。";
            case "竞赛型人才" -> "竞赛与实践维度有一定积累，建议围绕优势方向深化作品与成果。";
            default -> "高分科目多偏理论与记忆型，与考研理论型硕士培养方向较为契合。";
        });
        reasons.add("当前结论为规则引擎结果，后续补充更多成绩和活动数据后可再次分析。");
        return reasons;
    }

    private List<String> buildSuggestions(String primaryType) {
        if ("考公".equals(primaryType)) {
            return List.of(
                    "继续保持德育、纪律性和组织能力。",
                    "补充志愿服务、基层实践和班委经历。",
                    "同步关注专业岗位与稳定职业规划。"
            );
        }
        if ("竞赛型人才".equals(primaryType)) {
            return List.of(
                    "继续参加高质量竞赛或科研项目。",
                    "围绕一到两个优势方向深化作品与成果。",
                    "把竞赛成果整理进简历和作品集。"
            );
        }
        return List.of(
                "保持课程均分和智育优势。",
                "尽早明确目标院校与专业方向。",
                "有计划地积累英语、数学和专业课复习材料。"
        );
    }

    private Map<String, Object> buildFeatureSnapshot(StudentAnalysisPayloadDTO payload) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (payload.getDimensionScores() != null) {
            snapshot.put("moralAverage", safe(payload.getDimensionScores().getMoralAverage()));
            snapshot.put("intellectualAverage", safe(payload.getDimensionScores().getIntellectualAverage()));
            snapshot.put("physicalAverage", safe(payload.getDimensionScores().getPhysicalAverage()));
        }
        if (payload.getCourseStatistics() != null) {
            snapshot.put("courseAverage", safe(payload.getCourseStatistics().getOverallAverage()));
            snapshot.put("excellentCourseCount", safeInt(payload.getCourseStatistics().getExcellentCount()));
            snapshot.put("scoreCount", safeInt(payload.getCourseStatistics().getScoreCount()));
            snapshot.put("topSubjects", payload.getCourseStatistics().getTopSubjects());
            snapshot.put("weakSubjects", payload.getCourseStatistics().getWeakSubjects());
        }
        if (payload.getExtraScoreStatistics() != null) {
            snapshot.put("approvedExtraScore", safe(payload.getExtraScoreStatistics().getApprovedTotalScore()));
            snapshot.put("competitionCount", safeInt(payload.getExtraScoreStatistics().getCompetitionCount()));
            snapshot.put("researchCount", safeInt(payload.getExtraScoreStatistics().getResearchCount()));
        }
        return snapshot;
    }

    static void addFallbackReason(Map<String, Object> snapshot, Exception failure) {
        if (snapshot == null) {
            return;
        }
        snapshot.put("fallbackReasonCode", "deepseek_failed");
        snapshot.put("fallbackReason", describeDeepSeekFailure(failure));
        snapshot.put("fallbackDetail", sanitizeFailureDetail(failure));
    }

    private static String describeDeepSeekFailure(Exception failure) {
        Throwable root = rootCause(failure);
        if (failure instanceof HttpStatusCodeException statusException) {
            int status = statusException.getStatusCode().value();
            if (status == 401 || status == 403) {
                return "DeepSeek 鉴权失败，请检查 API Key。";
            }
            if (status == 429) {
                return "DeepSeek 请求被限流，请稍后重试。";
            }
            if (status >= 500) {
                return "DeepSeek 服务暂时不可用，请稍后重试。";
            }
            return "DeepSeek 请求失败，HTTP " + status + "。";
        }
        String detail = String.valueOf(root == null ? failure : root).toLowerCase(Locale.ROOT);
        if (root instanceof SocketTimeoutException || detail.contains("timeout") || detail.contains("timed out")) {
            return "DeepSeek 请求超时，请检查网络或稍后重试。";
        }
        if (detail.contains("json") || detail.contains("parse") || detail.contains("content empty") || detail.contains("empty response")) {
            return "DeepSeek 返回内容无法解析，已使用规则兜底。";
        }
        return "DeepSeek 调用失败，已使用规则兜底。";
    }

    private static String sanitizeFailureDetail(Exception failure) {
        String detail = String.valueOf(failure == null ? "" : failure.getMessage());
        if (failure instanceof HttpStatusCodeException statusException) {
            String body = statusException.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                detail = detail + " " + body;
            }
        }
        return detail
                .replaceAll("sk-[A-Za-z0-9_-]+", "sk-***")
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***")
                .trim();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isCompetitionRelated(ExtraScoreApplication application) {
        return containsKeywords(
                Stream.of(
                                application.getTemplateName(),
                                application.getGroupName(),
                                application.getItemName(),
                                application.getLevelName(),
                                application.getDetailDesc(),
                                application.getProofText()
                        )
                        .filter(Objects::nonNull)
                        .toList(),
                List.of("竞赛", "比赛", "大赛", "挑战杯", "互联网+", "蓝桥杯", "数学建模", "程序设计", "算法", "创新"));
    }

    private boolean isResearchRelated(ExtraScoreApplication application) {
        return containsKeywords(
                Stream.of(
                                application.getTemplateName(),
                                application.getGroupName(),
                                application.getItemName(),
                                application.getLevelName(),
                                application.getDetailDesc(),
                                application.getProofText()
                        )
                        .filter(Objects::nonNull)
                        .toList(),
                List.of("科研", "论文", "专利", "课题", "研究", "实验室", "学术", "创新"));
    }

    private boolean containsKeywords(Collection<String> fields, List<String> keywords) {
        String merged = fields.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(keyword -> merged.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private List<Student> filterStudents(String keyword, String className, String role, Long teacherId) {
        return filterStudents(keyword, className, role, teacherId, null);
    }

    private List<Student> filterStudents(String keyword, String className, String role, Long teacherId, List<String> studentNos) {
        List<Student> students;
        if ("TEACHER".equalsIgnoreCase(role) && teacherId != null) {
            students = studentService.findByTeacherId(teacherId);
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            students = studentService.findAll();
        } else {
            students = List.of();
        }
        if (className != null && !className.isBlank()) {
            students = students.stream()
                    .filter(student -> className.trim().equals(student.getClassName()))
                    .toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            students = students.stream()
                    .filter(student -> {
                        String studentNo = student.getStudentNo() == null ? "" : student.getStudentNo().toLowerCase(Locale.ROOT);
                        String name = student.getName() == null ? "" : student.getName().toLowerCase(Locale.ROOT);
                        return studentNo.contains(lowerKeyword) || name.contains(lowerKeyword);
                    })
                    .toList();
        }
        if (studentNos != null && !studentNos.isEmpty()) {
            students = students.stream()
                    .filter(student -> studentNos.contains(student.getStudentNo()))
                    .toList();
        }
        return students;
    }

    private List<AdminAnalysisOverviewDTO.TypeCount> buildTypeDistribution(Collection<StudentAnalysisRecord> records, int totalStudents) {
        Map<String, Long> counts = records.stream()
                .collect(Collectors.groupingBy(StudentAnalysisRecord::getPrimaryType, LinkedHashMap::new, Collectors.counting()));

        List<String> typeOrder = List.of("考研", "考公", "竞赛型人才");
        List<AdminAnalysisOverviewDTO.TypeCount> distribution = new ArrayList<>();
        for (String type : typeOrder) {
            AdminAnalysisOverviewDTO.TypeCount item = new AdminAnalysisOverviewDTO.TypeCount();
            int count = counts.getOrDefault(type, 0L).intValue();
            item.setType(type);
            item.setCount(count);
            item.setRatio(totalStudents == 0 ? 0D : round(count * 100D / totalStudents));
            distribution.add(item);
        }
        return distribution;
    }

    private List<AdminAnalysisOverviewDTO.ClassDistribution> buildClassDistribution(List<Student> students, Map<String, StudentAnalysisRecord> latestRecordMap) {
        Map<String, List<Student>> classMap = students.stream()
                .collect(Collectors.groupingBy(student -> student.getClassName() == null ? "未分班" : student.getClassName(), TreeMap::new, Collectors.toList()));

        List<AdminAnalysisOverviewDTO.ClassDistribution> result = new ArrayList<>();
        for (Map.Entry<String, List<Student>> entry : classMap.entrySet()) {
            List<Student> classStudents = entry.getValue();
            List<StudentAnalysisRecord> records = classStudents.stream()
                    .map(Student::getStudentNo)
                    .map(latestRecordMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            AdminAnalysisOverviewDTO.ClassDistribution item = new AdminAnalysisOverviewDTO.ClassDistribution();
            item.setClassName(entry.getKey());
            item.setTotalStudents(classStudents.size());
            item.setAnalyzedStudents(records.size());
            item.setGraduateCount((int) records.stream().filter(record -> "考研".equals(record.getPrimaryType())).count());
            item.setCivilCount((int) records.stream().filter(record -> "考公".equals(record.getPrimaryType())).count());
            item.setCompetitionCount((int) records.stream().filter(record -> "竞赛型人才".equals(record.getPrimaryType())).count());
            item.setDominantType(resolveDominantType(item));
            result.add(item);
        }
        return result;
    }

    private String resolveDominantType(AdminAnalysisOverviewDTO.ClassDistribution item) {
        int graduate = safeInt(item.getGraduateCount());
        int civil = safeInt(item.getCivilCount());
        int competition = safeInt(item.getCompetitionCount());
        if (graduate >= civil && graduate >= competition) {
            return "考研";
        }
        if (civil >= graduate && civil >= competition) {
            return "考公";
        }
        return "竞赛型人才";
    }

    private List<String> readStringList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private Map<String, Object> readMap(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    private Optional<Student> resolveStudent(String studentNo, Long userId) {
        if (userId != null) {
            Optional<Student> currentStudent = studentService.findByUserId(userId);
            if (currentStudent.isPresent()) {
                return currentStudent;
            }
        }
        if (studentNo != null && !studentNo.isBlank()) {
            return studentService.findByStudentNo(studentNo.trim());
        }
        return Optional.empty();
    }

    private double average(List<Double> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);
    }

    private double safe(Double value) {
        return value == null ? 0D : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Double round(Double value) {
        if (value == null) {
            return 0D;
        }
        return Math.round(value * 10D) / 10D;
    }

    private int score(boolean matched, int value) {
        return matched ? value : 0;
    }

    private BatchAnalysisTaskDTO toBatchTaskDto(BatchTaskState task) {
        BatchAnalysisTaskDTO dto = new BatchAnalysisTaskDTO();
        dto.setTaskId(task.taskId);
        dto.setStatus(task.status);
        dto.setTotalCount(task.totalCount.get());
        dto.setProcessedCount(task.processedCount.get());
        dto.setSuccessCount(task.successCount.get());
        dto.setMessage(task.message);
        dto.setStartedAt(task.startedAt);
        dto.setFinishedAt(task.finishedAt);
        return dto;
    }

    private static class BatchTaskState {
        private String taskId;
        private String status;
        private String message;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private final AtomicInteger totalCount = new AtomicInteger();
        private final AtomicInteger processedCount = new AtomicInteger();
        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    }
}
