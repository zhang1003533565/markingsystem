package com.markingsystem.service;

import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreSubmissionWindow;
import com.markingsystem.entity.Student;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.ExtraScoreSubmissionWindowRepository;
import com.markingsystem.util.RequestUserContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraScoreSubmissionWindowServiceTest {

    @Test
    void periodLabelCountsUniqueBatchesInsteadOfDuplicateWindowRows() {
        ExtraScoreSubmissionWindow mainWindow = window(1L, "计科231");
        mainWindow.setBatchCode("batch-main");
        mainWindow.setStartTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        mainWindow.setCreatedAt(LocalDateTime.of(2026, 6, 30, 10, 0));

        ExtraScoreSubmissionWindow duplicatedMainWindow = window(2L, "计科231");
        duplicatedMainWindow.setBatchCode("batch-main");
        duplicatedMainWindow.setStartTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        duplicatedMainWindow.setCreatedAt(LocalDateTime.of(2026, 6, 30, 11, 0));

        ExtraScoreSubmissionWindow supplementalWindow = window(3L, "计科231");
        supplementalWindow.setBatchCode("batch-supplemental");
        supplementalWindow.setStartTime(LocalDateTime.of(2026, 7, 10, 0, 0));
        supplementalWindow.setCreatedAt(LocalDateTime.of(2026, 7, 9, 10, 0));

        ExtraScoreSubmissionWindowService service = service(
                List.of(mainWindow, duplicatedMainWindow, supplementalWindow),
                List.of()
        );

        assertEquals("补充周期 1", service.resolvePeriodLabel(supplementalWindow));
    }

    @Test
    void completeReviewPeriodReportsPendingClassesInSameBatch() {
        ExtraScoreSubmissionWindow completedClassWindow = window(5L, "计科231");
        ExtraScoreSubmissionWindow pendingClassWindow = window(6L, "软工2401");
        List<ExtraScoreApplication> applications = List.of(
                application(5L, "计科231", "approved", "approved"),
                application(6L, "软工2401", "pending", "teacher_pending")
        );
        ExtraScoreSubmissionWindowService service = service(
                List.of(completedClassWindow, pendingClassWindow),
                applications
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.completeReviewPeriod(teacherContext(), 5L)
        );

        assertTrue(exception.getMessage().contains("软工2401"));
        assertTrue(exception.getMessage().contains("老师待审 1 条"));
    }

    @Test
    void completeReviewPeriodRefreshesOnlyClassesInTheWindowBatch() {
        ExtraScoreSubmissionWindow firstClassWindow = window(5L, "计科231");
        ExtraScoreSubmissionWindow secondClassWindow = window(6L, "软工2401");
        List<ExtraScoreApplication> applications = List.of(
                application(5L, "计科231", "approved", "approved"),
                application(6L, "软工2401", "approved", "approved")
        );
        RecordingCompositeScoreService compositeScoreService = new RecordingCompositeScoreService();
        ExtraScoreSubmissionWindowService service = service(
                List.of(firstClassWindow, secondClassWindow),
                applications,
                compositeScoreService
        );

        service.completeReviewPeriod(teacherContext(), 5L);

        assertEquals(List.of("计科231", "软工2401"), compositeScoreService.refreshedClassNames);
    }

    private ExtraScoreSubmissionWindowService service(
            List<ExtraScoreSubmissionWindow> windows,
            List<ExtraScoreApplication> applications
    ) {
        return service(windows, applications, null);
    }

    private ExtraScoreSubmissionWindowService service(
            List<ExtraScoreSubmissionWindow> windows,
            List<ExtraScoreApplication> applications,
            AdminCompositeScoreService compositeScoreService
    ) {
        ExtraScoreSubmissionWindowRepository windowRepository = proxy(
                ExtraScoreSubmissionWindowRepository.class,
                Map.of(
                        "findById", (MethodHandler) args -> windows.stream()
                                .filter(window -> window.getId().equals(args[0]))
                                .findFirst(),
                        "findAllById", (MethodHandler) args -> {
                            Iterable<Long> ids = (Iterable<Long>) args[0];
                            List<Long> windowIds = new java.util.ArrayList<>();
                            ids.forEach(windowIds::add);
                            return windows.stream().filter(window -> windowIds.contains(window.getId())).toList();
                        },
                        "saveAll", (MethodHandler) args -> args[0],
                        "findByTeacherIdAndAcademicYearOrderByCreatedAtDescClassNameAsc",
                        (MethodHandler) args -> windows.stream()
                                .filter(window -> window.getTeacherId().equals(args[0]))
                                .filter(window -> window.getAcademicYear().equals(args[1]))
                                .toList(),
                        "findByAcademicYearAndClassNameOrderByCreatedAtDesc",
                        (MethodHandler) args -> windows.stream()
                                .filter(window -> window.getAcademicYear().equals(args[0]))
                                .filter(window -> window.getClassName().equals(args[1]))
                                .toList()
                )
        );
        ExtraScoreApplicationRepository applicationRepository = proxy(
                ExtraScoreApplicationRepository.class,
                Map.of(
                        "findBySubmissionWindowIdIn", (MethodHandler) args -> {
                            List<Long> windowIds = (List<Long>) args[0];
                            return applications.stream()
                                    .filter(application -> windowIds.contains(application.getSubmissionWindowId()))
                                    .toList();
                        },
                        "saveAll", (MethodHandler) args -> args[0]
                )
        );
        return new ExtraScoreSubmissionWindowService(
                windowRepository,
                null,
                null,
                null,
                new EmptyStudentService(),
                applicationRepository,
                compositeScoreService
        );
    }

    private static ExtraScoreSubmissionWindow window(Long id, String className) {
        ExtraScoreSubmissionWindow window = new ExtraScoreSubmissionWindow();
        window.setId(id);
        window.setTeacherId(2L);
        window.setAcademicYear("2025-2026");
        window.setBatchCode("batch-1");
        window.setClassName(className);
        window.setEnabled(true);
        return window;
    }

    private static ExtraScoreApplication application(
            Long windowId,
            String className,
            String status,
            String reviewStage
    ) {
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setSubmissionWindowId(windowId);
        application.setClassName(className);
        application.setStatus(status);
        application.setReviewStage(reviewStage);
        return application;
    }

    private static RequestUserContext teacherContext() {
        return new RequestUserContext(2L, "TEACHER", 2L, null, null);
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

    private static class RecordingCompositeScoreService extends AdminCompositeScoreService {
        private final List<String> refreshedClassNames = new java.util.ArrayList<>();

        @Override
        public Map<String, Object> refreshCompositeScores(
                String academicYear,
                String className,
                Long teacherId,
                Long updatedByUserId,
                String updatedByName,
                String updatedByRole
        ) {
            refreshedClassNames.add(className);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", 1);
            result.put("className", className);
            return result;
        }
    }

    private static class EmptyStudentService extends StudentService {
        @Override
        public List<Student> findByClassName(String className) {
            return List.of();
        }
    }
}
