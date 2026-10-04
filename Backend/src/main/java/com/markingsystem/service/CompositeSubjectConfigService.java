package com.markingsystem.service;

import com.markingsystem.dto.CompositeSubjectConfigRequest;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.CompositeSubjectConfig;
import com.markingsystem.entity.Major;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CompositeSubjectConfigRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompositeSubjectConfigService {

    @Autowired
    private CompositeSubjectConfigRepository compositeSubjectConfigRepository;

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    public List<Map<String, Object>> listByScope(String academicYear, Long majorId, String grade, String className, Long teacherId) {
        String normalizedYear = normalizeAcademicYear(academicYear);
        String normalizedGrade = normalizeGrade(grade);
        String normalizedClassName = normalizeClassName(className);
        if (normalizedYear.isBlank()) {
            return List.of();
        }
        if (!normalizedClassName.isBlank()) {
            ensureClassAccessible(normalizedClassName, teacherId);
            return listByClassScope(normalizedYear, normalizedClassName);
        }
        if (majorId == null || normalizedGrade.isBlank()) {
            return List.of();
        }
        ensureMajorGradeAccessible(majorId, normalizedGrade, teacherId);

        Map<Long, CompositeSubjectConfig> configMap = compositeSubjectConfigRepository
                .findByAcademicYearAndMajorIdAndGradeOrderBySubjectNameAsc(normalizedYear, majorId, normalizedGrade)
                .stream()
                .collect(Collectors.toMap(CompositeSubjectConfig::getSubjectId, Function.identity(), (left, right) -> right, LinkedHashMap::new));

        return subjectScoreRAINRepository.findDistinctSubjectsByAcademicYearAndMajorIdAndGrade(normalizedYear, majorId, normalizedGrade).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(subject -> String.valueOf(subject.getSubjectName()), String::compareToIgnoreCase))
                .map(subject -> {
                    CompositeSubjectConfig config = configMap.get(subject.getId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("subjectId", subject.getId());
                    item.put("subjectName", subject.getSubjectName());
                    item.put("category", config != null ? normalizeCategory(config.getCompositeCategory()) : guessCategory(subject.getSubjectName()));
                    return item;
                })
                .toList();
    }

    public Map<String, Object> listClassConfigStatus(String academicYear, Long teacherId) {
        String normalizedYear = normalizeAcademicYear(academicYear);
        if (normalizedYear.isBlank()) {
            return Map.of("summary", buildClassConfigStatusSummary(0, 0, 0, 0, 0), "classes", List.of());
        }

        Map<Long, String> majorNameMap = majorRepository.findAll().stream()
                .collect(Collectors.toMap(Major::getId, Major::getName, (left, right) -> left, LinkedHashMap::new));
        Set<String> managedClassNames = resolveManagedClassNames(teacherId);

        List<Map<String, Object>> classes = classInfoRepository.findAll().stream()
                .filter(item -> managedClassNames == null
                        || managedClassNames.contains(normalizeClassName(item.getName())))
                .sorted(this::compareClassForConfigStatus)
                .map(item -> buildClassConfigStatusRow(normalizedYear, item, majorNameMap))
                .toList();

        long configuredCount = classes.stream().filter(item -> "configured".equals(item.get("status"))).count();
        long partialCount = classes.stream().filter(item -> "partial".equals(item.get("status"))).count();
        long unconfiguredCount = classes.stream().filter(item -> "unconfigured".equals(item.get("status"))).count();
        long noSubjectCount = classes.stream().filter(item -> "no_subject".equals(item.get("status"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", buildClassConfigStatusSummary(classes.size(), configuredCount, partialCount, unconfiguredCount, noSubjectCount));
        result.put("classes", classes);
        return result;
    }

    private Map<String, Object> buildClassConfigStatusRow(String academicYear, ClassInfo classInfo, Map<Long, String> majorNameMap) {
        String className = normalizeClassName(classInfo.getName());
        List<SubjectRAIN> subjects = subjectScoreRAINRepository.findDistinctSubjectsByAcademicYearAndClassName(academicYear, className).stream()
                .filter(Objects::nonNull)
                .toList();
        List<CompositeSubjectConfig> configs = compositeSubjectConfigRepository.findByAcademicYearAndClassNameOrderBySubjectNameAsc(academicYear, className);
        long subjectCount = subjects.stream().map(SubjectRAIN::getId).filter(Objects::nonNull).distinct().count();
        long configuredCount = configs.stream().map(CompositeSubjectConfig::getSubjectId).filter(Objects::nonNull).distinct().count();
        String status = resolveClassConfigStatus(subjectCount, configuredCount);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("classId", classInfo.getId());
        row.put("className", className);
        row.put("majorId", classInfo.getMajorId());
        row.put("majorName", majorNameMap.getOrDefault(classInfo.getMajorId(), "-"));
        row.put("grade", normalizeGrade(classInfo.getGrade()));
        row.put("subjectCount", subjectCount);
        row.put("configuredCount", configuredCount);
        row.put("status", status);
        row.put("statusLabel", resolveClassConfigStatusLabel(status));
        return row;
    }

    private Map<String, Object> buildClassConfigStatusSummary(long totalCount, long configuredCount, long partialCount, long unconfiguredCount, long noSubjectCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", totalCount);
        summary.put("configuredCount", configuredCount);
        summary.put("partialCount", partialCount);
        summary.put("unconfiguredCount", unconfiguredCount);
        summary.put("noSubjectCount", noSubjectCount);
        return summary;
    }

    private String resolveClassConfigStatus(long subjectCount, long configuredCount) {
        if (subjectCount <= 0) {
            return "no_subject";
        }
        if (configuredCount <= 0) {
            return "unconfigured";
        }
        if (configuredCount < subjectCount) {
            return "partial";
        }
        return "configured";
    }

    private String resolveClassConfigStatusLabel(String status) {
        return switch (status) {
            case "configured" -> "已配置";
            case "partial" -> "部分配置";
            case "no_subject" -> "无成绩科目";
            default -> "未配置";
        };
    }

    private int compareClassForConfigStatus(ClassInfo left, ClassInfo right) {
        int gradeCompare = normalizeGrade(right.getGrade()).compareTo(normalizeGrade(left.getGrade()));
        if (gradeCompare != 0) {
            return gradeCompare;
        }
        return normalizeClassName(left.getName()).compareTo(normalizeClassName(right.getName()));
    }

    @Transactional
    public List<Map<String, Object>> save(String academicYear, Long majorId, String grade, String className, List<CompositeSubjectConfigRequest.Item> items, Long teacherId) {
        String normalizedYear = normalizeAcademicYear(academicYear);
        String normalizedGrade = normalizeGrade(grade);
        String normalizedClassName = normalizeClassName(className);
        if (normalizedYear.isBlank()) {
            throw new IllegalArgumentException("学年不能为空");
        }
        if (!normalizedClassName.isBlank()) {
            return saveByClassScope(normalizedYear, normalizedClassName, items, teacherId);
        }
        if (majorId == null || normalizedGrade.isBlank()) {
            throw new IllegalArgumentException("专业、年级不能为空");
        }
        ensureMajorGradeAccessible(majorId, normalizedGrade, teacherId);

        Major major = majorRepository.findById(majorId)
                .orElseThrow(() -> new IllegalArgumentException("专业不存在"));

        boolean gradeExists = classInfoRepository.findAll().stream()
                .filter(item -> managedClassFilter(teacherId, item))
                .anyMatch(item -> Objects.equals(item.getMajorId(), majorId) && normalizedGrade.equals(normalizeGrade(item.getGrade())));
        if (!gradeExists) {
            throw new IllegalArgumentException("该专业下不存在对应年级，或不在您的班级范围内");
        }

        List<CompositeSubjectConfig> existing = compositeSubjectConfigRepository
                .findByAcademicYearAndMajorIdAndGradeOrderBySubjectNameAsc(normalizedYear, majorId, normalizedGrade);
        compositeSubjectConfigRepository.deleteAll(existing);
        compositeSubjectConfigRepository.flush();

        Map<Long, SubjectRAIN> visibleSubjects = subjectScoreRAINRepository
                .findDistinctSubjectsByAcademicYearAndMajorIdAndGrade(normalizedYear, majorId, normalizedGrade).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SubjectRAIN::getId, Function.identity(), (left, right) -> right));

        List<CompositeSubjectConfig> toSave = items == null ? List.of() : items.stream()
                .filter(item -> item.getSubjectId() != null && visibleSubjects.containsKey(item.getSubjectId()))
                .map(item -> {
                    SubjectRAIN subject = visibleSubjects.get(item.getSubjectId());
                    CompositeSubjectConfig config = new CompositeSubjectConfig();
                    config.setAcademicYear(normalizedYear);
                    config.setMajorId(majorId);
                    config.setMajorName(major.getName());
                    config.setGrade(normalizedGrade);
                    config.setSubjectId(subject.getId());
                    config.setSubjectName(subject.getSubjectName());
                    config.setCompositeCategory(normalizeCategory(item.getCategory()));
                    return config;
                })
                .toList();

        compositeSubjectConfigRepository.saveAll(toSave);
        return listByScope(normalizedYear, majorId, normalizedGrade, null, teacherId);
    }

    private List<Map<String, Object>> listByClassScope(String academicYear, String className) {
        Map<Long, CompositeSubjectConfig> configMap = compositeSubjectConfigRepository
                .findByAcademicYearAndClassNameOrderBySubjectNameAsc(academicYear, className)
                .stream()
                .collect(Collectors.toMap(CompositeSubjectConfig::getSubjectId, Function.identity(), (left, right) -> right, LinkedHashMap::new));

        return subjectScoreRAINRepository.findDistinctSubjectsByAcademicYearAndClassName(academicYear, className).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(subject -> String.valueOf(subject.getSubjectName()), String::compareToIgnoreCase))
                .map(subject -> {
                    CompositeSubjectConfig config = configMap.get(subject.getId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("subjectId", subject.getId());
                    item.put("subjectName", subject.getSubjectName());
                    item.put("category", config != null ? normalizeCategory(config.getCompositeCategory()) : guessCategory(subject.getSubjectName()));
                    return item;
                })
                .toList();
    }

    @Transactional
    protected List<Map<String, Object>> saveByClassScope(String academicYear, String className, List<CompositeSubjectConfigRequest.Item> items, Long teacherId) {
        ensureClassAccessible(className, teacherId);
        ClassInfo classInfo = classInfoRepository.findByName(className)
                .orElseThrow(() -> new IllegalArgumentException("班级不存在"));
        if (classInfo.getMajorId() == null || normalizeGrade(classInfo.getGrade()).isBlank()) {
            throw new IllegalArgumentException("班级缺少专业或年级信息");
        }
        Major major = majorRepository.findById(classInfo.getMajorId())
                .orElseThrow(() -> new IllegalArgumentException("专业不存在"));

        List<CompositeSubjectConfig> existing = compositeSubjectConfigRepository
                .findByAcademicYearAndClassNameOrderBySubjectNameAsc(academicYear, className);
        compositeSubjectConfigRepository.deleteAll(existing);
        compositeSubjectConfigRepository.flush();

        Map<Long, SubjectRAIN> visibleSubjects = subjectScoreRAINRepository
                .findDistinctSubjectsByAcademicYearAndClassName(academicYear, className).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SubjectRAIN::getId, Function.identity(), (left, right) -> left));

        String normalizedGrade = normalizeGrade(classInfo.getGrade());
        List<CompositeSubjectConfig> toSave = items == null ? List.of() : items.stream()
                .filter(item -> item.getSubjectId() != null && visibleSubjects.containsKey(item.getSubjectId()))
                .map(item -> {
                    SubjectRAIN subject = visibleSubjects.get(item.getSubjectId());
                    CompositeSubjectConfig config = new CompositeSubjectConfig();
                    config.setAcademicYear(academicYear);
                    config.setMajorId(classInfo.getMajorId());
                    config.setMajorName(major.getName());
                    config.setGrade(normalizedGrade);
                    config.setClassName(className);
                    config.setSubjectId(subject.getId());
                    config.setSubjectName(subject.getSubjectName());
                    config.setCompositeCategory(normalizeCategory(item.getCategory()));
                    return config;
                })
                .toList();

        compositeSubjectConfigRepository.saveAll(toSave);
        return listByClassScope(academicYear, className);
    }

    public Map<Long, String> getCategoryMap(String academicYear, Long majorId, String grade) {
        String normalizedYear = normalizeAcademicYear(academicYear);
        String normalizedGrade = normalizeGrade(grade);
        if (normalizedYear.isBlank() || majorId == null || normalizedGrade.isBlank()) {
            return Map.of();
        }
        return compositeSubjectConfigRepository
                .findByAcademicYearAndMajorIdAndGradeOrderBySubjectNameAsc(normalizedYear, majorId, normalizedGrade).stream()
                .collect(Collectors.toMap(
                        CompositeSubjectConfig::getSubjectId,
                        item -> normalizeCategory(item.getCompositeCategory()),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    public Map<Long, String> getCategoryMapByClassName(String academicYear, String className) {
        String normalizedYear = normalizeAcademicYear(academicYear);
        String normalizedClassName = normalizeClassName(className);
        if (normalizedYear.isBlank() || normalizedClassName.isBlank()) {
            return Map.of();
        }
        return compositeSubjectConfigRepository
                .findByAcademicYearAndClassNameOrderBySubjectNameAsc(normalizedYear, normalizedClassName).stream()
                .collect(Collectors.toMap(
                        CompositeSubjectConfig::getSubjectId,
                        item -> normalizeCategory(item.getCompositeCategory()),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    public String resolveCategory(String academicYear, Long majorId, String grade, Long subjectId, String subjectName) {
        if (subjectId == null) {
            return guessCategory(subjectName);
        }
        String value = getCategoryMap(academicYear, majorId, grade).get(subjectId);
        return value == null ? guessCategory(subjectName) : value;
    }

    private void ensureClassAccessible(String className, Long teacherId) {
        if (teacherId == null) {
            return;
        }
        boolean accessible = classInfoRepository.findByTeacherId(teacherId).stream()
                .map(ClassInfo::getName)
                .filter(Objects::nonNull)
                .map(this::normalizeClassName)
                .anyMatch(name -> name.equals(normalizeClassName(className)));
        if (!accessible) {
            throw new IllegalArgumentException("无权配置该班级综测科目");
        }
    }

    private void ensureMajorGradeAccessible(Long majorId, String grade, Long teacherId) {
        if (teacherId == null) {
            return;
        }
        boolean accessible = classInfoRepository.findByTeacherId(teacherId).stream()
                .anyMatch(item -> Objects.equals(item.getMajorId(), majorId)
                        && normalizeGrade(grade).equals(normalizeGrade(item.getGrade())));
        if (!accessible) {
            throw new IllegalArgumentException("无权配置该专业/年级综测科目");
        }
    }

    private Set<String> resolveManagedClassNames(Long teacherId) {
        if (teacherId == null) {
            return null;
        }
        return classInfoRepository.findByTeacherId(teacherId).stream()
                .map(ClassInfo::getName)
                .filter(Objects::nonNull)
                .map(this::normalizeClassName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean managedClassFilter(Long teacherId, ClassInfo classInfo) {
        if (teacherId == null) {
            return true;
        }
        if (classInfo == null || classInfo.getName() == null) {
            return false;
        }
        Set<String> managed = resolveManagedClassNames(teacherId);
        return managed != null && managed.contains(normalizeClassName(classInfo.getName()));
    }

    private String normalizeAcademicYear(String academicYear) {
        return academicYear == null ? "" : academicYear.trim();
    }

    private String normalizeGrade(String grade) {
        return grade == null ? "" : grade.trim();
    }

    private String normalizeClassName(String className) {
        return className == null ? "" : className.trim();
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PHYSICAL", "体育" -> "PHYSICAL";
            case "EXCLUDED", "NONE", "不计入" -> "EXCLUDED";
            default -> "INTELLECTUAL";
        };
    }

    private String guessCategory(String subjectName) {
        String value = String.valueOf(subjectName == null ? "" : subjectName).toLowerCase(Locale.ROOT);
        if (value.contains("体育")) {
            return "PHYSICAL";
        }
        return "INTELLECTUAL";
    }
}