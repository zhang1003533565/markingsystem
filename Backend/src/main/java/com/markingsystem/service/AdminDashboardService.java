package com.markingsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.AdminDashboardOverviewDTO;
import com.markingsystem.dto.AdminDashboardPortraitOverviewDTO;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.StudentAnalysisRecord;
import com.markingsystem.repository.StudentAnalysisRecordRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private final StudentService studentService;
    private final StudentAnalysisRecordRepository studentAnalysisRecordRepository;
    private final ObjectMapper objectMapper;

    public AdminDashboardService(
            StudentService studentService,
            StudentAnalysisRecordRepository studentAnalysisRecordRepository,
            ObjectMapper objectMapper) {
        this.studentService = studentService;
        this.studentAnalysisRecordRepository = studentAnalysisRecordRepository;
        this.objectMapper = objectMapper;
    }

    public AdminDashboardOverviewDTO buildOverview(String role, Long teacherId) {
        List<Student> students = findScopedStudents(role, teacherId);
        Map<String, List<Student>> classStudentMap = groupStudentsByClass(students);

        AdminDashboardOverviewDTO dto = new AdminDashboardOverviewDTO();
        dto.setManagedClassCount(classStudentMap.size());
        dto.setManagedStudentCount(students.size());
        dto.setManagedClasses(buildClassSummaries(classStudentMap));
        return dto;
    }

    public AdminDashboardPortraitOverviewDTO buildPortraitOverview(String role, Long teacherId) {
        List<Student> students = findScopedStudents(role, teacherId);
        Map<String, StudentAnalysisRecord> latestRecordMap = findLatestAnalysisRecords(students);
        Map<String, List<Student>> classStudentMap = groupStudentsByClass(students);

        AdminDashboardPortraitOverviewDTO dto = new AdminDashboardPortraitOverviewDTO();
        dto.setAnalyzedStudentCount(latestRecordMap.size());
        dto.setClassPortraits(buildClassPortraitSummaries(classStudentMap, latestRecordMap));
        dto.setPortraitTrends(buildPortraitTrends(classStudentMap, latestRecordMap));
        dto.setStandoutStudents(buildStandoutStudents(students, latestRecordMap));
        dto.setTopPortraitType(resolveTopPortrait(latestRecordMap.values()));
        return dto;
    }

    private List<Student> findScopedStudents(String role, Long teacherId) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return studentService.findAll();
        }
        if ("TEACHER".equalsIgnoreCase(role) && teacherId != null) {
            return studentService.findByTeacherId(teacherId);
        }
        return List.of();
    }

    private Map<String, StudentAnalysisRecord> findLatestAnalysisRecords(List<Student> students) {
        List<String> studentNos = students.stream()
                .map(Student::getStudentNo)
                .filter(Objects::nonNull)
                .toList();
        if (studentNos.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return studentAnalysisRecordRepository
                .findByStudentNoInOrderByGeneratedAtDesc(studentNos).stream()
                .collect(Collectors.toMap(
                        StudentAnalysisRecord::getStudentNo,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, List<Student>> groupStudentsByClass(List<Student> students) {
        return students.stream()
                .collect(Collectors.groupingBy(
                        student -> student.getClassName() == null ? "未分班" : student.getClassName(),
                        TreeMap::new,
                        Collectors.toList()
                ));
    }

    private List<AdminDashboardOverviewDTO.ClassSummary> buildClassSummaries(Map<String, List<Student>> classStudentMap) {
        List<AdminDashboardOverviewDTO.ClassSummary> items = new ArrayList<>();
        for (Map.Entry<String, List<Student>> entry : classStudentMap.entrySet()) {
            AdminDashboardOverviewDTO.ClassSummary item = new AdminDashboardOverviewDTO.ClassSummary();
            item.setClassName(entry.getKey());
            item.setStudentCount(entry.getValue().size());
            items.add(item);
        }
        return items;
    }

    private List<AdminDashboardPortraitOverviewDTO.ClassPortraitSummary> buildClassPortraitSummaries(
            Map<String, List<Student>> classStudentMap,
            Map<String, StudentAnalysisRecord> latestRecordMap) {
        List<AdminDashboardPortraitOverviewDTO.ClassPortraitSummary> items = new ArrayList<>();
        for (Map.Entry<String, List<Student>> entry : classStudentMap.entrySet()) {
            List<StudentAnalysisRecord> records = entry.getValue().stream()
                    .map(Student::getStudentNo)
                    .map(latestRecordMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            AdminDashboardPortraitOverviewDTO.ClassPortraitSummary item = new AdminDashboardPortraitOverviewDTO.ClassPortraitSummary();
            item.setClassName(entry.getKey());
            item.setStudentCount(entry.getValue().size());
            item.setAnalyzedCount(records.size());
            item.setDominantPortrait(resolveTopPortrait(records));
            item.setCommitteeNames(entry.getValue().stream()
                    .filter(student -> Boolean.TRUE.equals(student.getClassCommittee()))
                    .map(Student::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .toList());
            items.add(item);
        }
        return items;
    }

    private List<AdminDashboardPortraitOverviewDTO.PortraitTrendItem> buildPortraitTrends(
            Map<String, List<Student>> classStudentMap,
            Map<String, StudentAnalysisRecord> latestRecordMap) {
        List<AdminDashboardPortraitOverviewDTO.PortraitTrendItem> items = new ArrayList<>();
        for (Map.Entry<String, List<Student>> entry : classStudentMap.entrySet()) {
            List<StudentAnalysisRecord> records = entry.getValue().stream()
                    .map(Student::getStudentNo)
                    .map(latestRecordMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            AdminDashboardPortraitOverviewDTO.PortraitTrendItem item = new AdminDashboardPortraitOverviewDTO.PortraitTrendItem();
            item.setClassName(entry.getKey());
            item.setGraduateCount((int) records.stream().filter(record -> "考研".equals(record.getPrimaryType())).count());
            item.setCivilCount((int) records.stream().filter(record -> "考公".equals(record.getPrimaryType())).count());
            item.setCompetitionCount((int) records.stream().filter(record -> "竞赛型人才".equals(record.getPrimaryType())).count());
            items.add(item);
        }
        return items;
    }

    private List<AdminDashboardPortraitOverviewDTO.OutstandingStudent> buildStandoutStudents(
            List<Student> students,
            Map<String, StudentAnalysisRecord> latestRecordMap) {
        return students.stream()
                .map(student -> toOutstandingStudent(student, latestRecordMap.get(student.getStudentNo())))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(AdminDashboardPortraitOverviewDTO.OutstandingStudent::getCourseAverage, Comparator.nullsLast(Double::compareTo)).reversed()
                        .thenComparing(AdminDashboardPortraitOverviewDTO.OutstandingStudent::getConfidence, Comparator.nullsLast(Integer::compareTo)).reversed())
                .limit(10)
                .toList();
    }

    private AdminDashboardPortraitOverviewDTO.OutstandingStudent toOutstandingStudent(Student student, StudentAnalysisRecord record) {
        if (record == null) {
            return null;
        }
        Map<String, Object> featureSnapshot = readMap(record.getFeatureSnapshot());
        Double courseAverage = readDouble(featureSnapshot.get("courseAverage"));
        AdminDashboardPortraitOverviewDTO.OutstandingStudent item = new AdminDashboardPortraitOverviewDTO.OutstandingStudent();
        item.setStudentNo(student.getStudentNo());
        item.setStudentName(student.getName());
        item.setClassName(student.getClassName());
        item.setPrimaryType(record.getPrimaryType());
        item.setConfidence(record.getConfidence());
        item.setCourseAverage(courseAverage);
        item.setHighlight(buildHighlight(record.getPrimaryType(), courseAverage, record.getConfidence()));
        return item;
    }

    private String buildHighlight(String primaryType, Double courseAverage, Integer confidence) {
        double avg = courseAverage == null ? 0D : courseAverage;
        int conf = confidence == null ? 0 : confidence;
        return String.format(Locale.US, "%s倾向明显，课程均分 %.1f，画像置信度 %d%%。", primaryType, avg, conf);
    }

    private String resolveTopPortrait(Iterable<StudentAnalysisRecord> records) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (StudentAnalysisRecord record : records) {
            counts.merge(record.getPrimaryType(), 1L, Long::sum);
        }
        if (counts.isEmpty()) {
            return "暂无";
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("暂无");
    }

    private Map<String, Object> readMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Double readDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }
}
