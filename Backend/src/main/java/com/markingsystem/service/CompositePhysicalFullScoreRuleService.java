package com.markingsystem.service;

import com.markingsystem.dto.CompositePhysicalFullScoreRuleRequest;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.CompositePhysicalFullScoreRule;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CompositePhysicalFullScoreRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompositePhysicalFullScoreRuleService {

    @Autowired
    private CompositePhysicalFullScoreRuleRepository ruleRepository;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    public Map<String, Object> list(String academicYear, Long teacherId) {
        String normalizedYear = requireAcademicYear(academicYear);
        List<CompositePhysicalFullScoreRule> rules =
                ruleRepository.findByAcademicYearOrderByClassNameAsc(normalizedYear);
        Map<String, CompositePhysicalFullScoreRule> ruleMap = rules.stream()
                .filter(item -> teacherId == null || isTeacherManagedClass(item.getClassName(), teacherId))
                .collect(Collectors.toMap(
                        item -> normalizeClassName(item.getClassName()),
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        Map<String, ClassInfo> classMap = classInfoRepository.findAll().stream()
                .filter(item -> item.getStatus() == null || !"inactive".equalsIgnoreCase(item.getStatus()))
                .filter(item -> !normalizeClassName(item.getName()).isBlank())
                .filter(item -> teacherId == null || Objects.equals(item.getTeacherId(), teacherId))
                .collect(Collectors.toMap(
                        item -> normalizeClassName(item.getName()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<String> classNames = new LinkedHashSet<>(classMap.keySet());
        classNames.addAll(ruleMap.keySet());
        List<String> sortedClassNames = classNames.stream()
                .sorted(Comparator
                        .comparing((String className) -> gradeSortValue(
                                classMap.get(className) == null ? null : classMap.get(className).getGrade()
                        ))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();

        List<Map<String, Object>> items = new ArrayList<>();
        for (String className : sortedClassNames) {
            CompositePhysicalFullScoreRule rule = ruleMap.get(className);
            ClassInfo classInfo = classMap.get(className);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("className", className);
            item.put("grade", classInfo == null ? "" : normalizeGrade(classInfo.getGrade()));
            item.put("majorId", classInfo == null ? null : classInfo.getMajorId());
            item.put("fullScoreEnabled", rule != null && Boolean.TRUE.equals(rule.getFullScoreEnabled()));
            item.put("configured", rule != null);
            item.put("updatedAt", rule == null ? null : rule.getUpdatedAt());
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicYear", normalizedYear);
        result.put("items", items);
        result.put("classCount", items.size());
        result.put("fullScoreClassCount", items.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("fullScoreEnabled")))
                .count());
        return result;
    }

    @Transactional
    public Map<String, Object> save(
            String academicYear,
            List<CompositePhysicalFullScoreRuleRequest.Item> items,
            Long updatedByUserId,
            Long teacherId
    ) {
        String normalizedYear = requireAcademicYear(academicYear);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("班级体育规则不能为空");
        }

        Map<String, CompositePhysicalFullScoreRule> existingMap =
                ruleRepository.findByAcademicYearOrderByClassNameAsc(normalizedYear).stream()
                        .collect(Collectors.toMap(
                                item -> normalizeClassName(item.getClassName()),
                                Function.identity(),
                                (left, right) -> right,
                                LinkedHashMap::new
                        ));
        Set<String> seenClassNames = new LinkedHashSet<>();
        List<CompositePhysicalFullScoreRule> toSave = new ArrayList<>();

        for (CompositePhysicalFullScoreRuleRequest.Item source : items) {
            if (source == null) {
                continue;
            }
            String className = normalizeClassName(source.getClassName());
            if (className.isBlank()) {
                throw new IllegalArgumentException("班级不能为空");
            }
            if (!isTeacherManagedClass(className, teacherId)) {
                throw new IllegalArgumentException("无权配置该班级体育满分规则");
            }
            if (!classInfoRepository.existsByName(className)) {
                throw new IllegalArgumentException("班级不存在：" + className);
            }
            if (!seenClassNames.add(className)) {
                throw new IllegalArgumentException("班级体育规则不能重复：" + className);
            }
            CompositePhysicalFullScoreRule rule = existingMap.getOrDefault(
                    className,
                    new CompositePhysicalFullScoreRule()
            );
            rule.setAcademicYear(normalizedYear);
            rule.setClassName(className);
            rule.setFullScoreEnabled(Boolean.TRUE.equals(source.getFullScoreEnabled()));
            rule.setUpdatedByUserId(updatedByUserId);
            toSave.add(rule);
        }

        if (toSave.isEmpty()) {
            throw new IllegalArgumentException("班级体育规则不能为空");
        }
        ruleRepository.saveAll(toSave);
        return list(normalizedYear, teacherId);
    }

    public Map<String, Boolean> getFullScoreRuleMap(String academicYear) {
        if (academicYear == null || academicYear.isBlank()) {
            return Map.of();
        }
        return ruleRepository.findByAcademicYearOrderByClassNameAsc(academicYear.trim()).stream()
                .collect(Collectors.toMap(
                        item -> normalizeClassName(item.getClassName()),
                        item -> Boolean.TRUE.equals(item.getFullScoreEnabled()),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    public boolean isFullScoreEnabled(String academicYear, String className) {
        if (academicYear == null || academicYear.isBlank()) {
            return false;
        }
        String normalizedClassName = normalizeClassName(className);
        if (normalizedClassName.isBlank()) {
            return false;
        }
        return ruleRepository.findByAcademicYearAndClassName(academicYear.trim(), normalizedClassName)
                .map(CompositePhysicalFullScoreRule::getFullScoreEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    public boolean isFullScoreEnabled(Map<String, Boolean> ruleMap, String className) {
        return Boolean.TRUE.equals(ruleMap.get(normalizeClassName(className)));
    }

    private boolean isTeacherManagedClass(String className, Long teacherId) {
        if (teacherId == null) {
            return true;
        }
        String normalized = normalizeClassName(className);
        if (normalized.isBlank()) {
            return false;
        }
        return classInfoRepository.findByTeacherId(teacherId).stream()
                .map(ClassInfo::getName)
                .filter(Objects::nonNull)
                .anyMatch(name -> normalizeClassName(name).equals(normalized));
    }

    private String requireAcademicYear(String academicYear) {
        if (academicYear == null || academicYear.isBlank()) {
            throw new IllegalArgumentException("学年不能为空");
        }
        return academicYear.trim();
    }

    private String normalizeClassName(String className) {
        return className == null ? "" : className.trim();
    }

    private String normalizeGrade(String grade) {
        return grade == null ? "" : grade.trim().replaceFirst("级$", "");
    }

    private Integer gradeSortValue(String grade) {
        String digits = grade == null ? "" : grade.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }
}
