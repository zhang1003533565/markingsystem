package com.markingsystem.service;

import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.CompositeSubjectConfig;
import com.markingsystem.entity.Major;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CompositeSubjectConfigRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeSubjectConfigServiceTest {

    @Test
    void classConfigStatusIncludesEveryClassForEveryAcademicYear() throws Exception {
        CompositeSubjectConfigService service = new CompositeSubjectConfigService();
        setField(service, "classInfoRepository", proxy(
                ClassInfoRepository.class,
                Map.of("findAll", (MethodHandler) args -> List.of(
                        classInfo("计科231", 1L, "2023", "active"),
                        classInfo("计科232", 1L, "2023", "active"),
                        classInfo("计科233", 1L, "2023", "active"),
                        classInfo("数据241", 2L, "2024", "active"),
                        classInfo("数据242", 2L, "2024", "inactive"),
                        classInfo("停用班", 1L, "2023", "inactive")
                ))
        ));
        setField(service, "majorRepository", proxy(
                MajorRepository.class,
                Map.of("findAll", (MethodHandler) args -> List.of(
                        major(1L, "计算机科学与技术"),
                        major(2L, "数据科学与大数据技术")
                ))
        ));
        setField(service, "subjectScoreRAINRepository", proxy(
                SubjectScoreRAINRepository.class,
                Map.of("findDistinctSubjectsByAcademicYearAndClassName", (MethodHandler) args -> switch (String.valueOf(args[1])) {
                    case "计科231", "计科232", "计科233" -> List.of(subject(10L, "高等数学"), subject(11L, "大学英语"));
                    case "数据242" -> List.of(subject(12L, "体育"));
                    default -> List.of();
                })
        ));
        setField(service, "compositeSubjectConfigRepository", proxy(
                CompositeSubjectConfigRepository.class,
                Map.of("findByAcademicYearAndClassNameOrderBySubjectNameAsc", (MethodHandler) args -> switch (String.valueOf(args[1])) {
                    case "计科231" -> List.of(config("计科231", 10L), config("计科231", 11L));
                    case "计科232" -> List.of(config("计科232", 10L));
                    default -> List.of();
                })
        ));

        Map<String, Object> result = service.listClassConfigStatus("2025-2026", null);
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        List<Map<String, Object>> classes = (List<Map<String, Object>>) result.get("classes");

        assertEquals(6L, summary.get("totalCount"));
        assertEquals(1L, summary.get("configuredCount"));
        assertEquals(1L, summary.get("partialCount"));
        assertEquals(2L, summary.get("unconfiguredCount"));
        assertEquals(2L, summary.get("noSubjectCount"));

        Map<String, String> statusByClassName = classes.stream().collect(Collectors.toMap(
                item -> String.valueOf(item.get("className")),
                item -> String.valueOf(item.get("status"))
        ));
        assertEquals("no_subject", statusByClassName.get("数据241"));
        assertEquals("unconfigured", statusByClassName.get("数据242"));
        assertEquals("configured", statusByClassName.get("计科231"));
        assertEquals("partial", statusByClassName.get("计科232"));
        assertEquals("unconfigured", statusByClassName.get("计科233"));
        assertEquals("no_subject", statusByClassName.get("停用班"));
    }

    private static ClassInfo classInfo(String name, Long majorId, String grade, String status) {
        ClassInfo row = new ClassInfo();
        row.setName(name);
        row.setMajorId(majorId);
        row.setGrade(grade);
        row.setStatus(status);
        return row;
    }

    private static Major major(Long id, String name) {
        Major row = new Major();
        row.setId(id);
        row.setName(name);
        return row;
    }

    private static SubjectRAIN subject(Long id, String name) {
        SubjectRAIN row = new SubjectRAIN();
        row.setId(id);
        row.setSubjectName(name);
        return row;
    }

    private static CompositeSubjectConfig config(String className, Long subjectId) {
        CompositeSubjectConfig row = new CompositeSubjectConfig();
        row.setClassName(className);
        row.setSubjectId(subjectId);
        return row;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> responses) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (responses.containsKey(method.getName())) {
                        Object response = responses.get(method.getName());
                        if (response instanceof MethodHandler handler) {
                            return handler.invoke(args == null ? new Object[0] : args);
                        }
                        return response;
                    }
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "TestProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private interface MethodHandler {
        Object invoke(Object[] args);
    }
}
