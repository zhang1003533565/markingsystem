package com.markingsystem.service;

import com.markingsystem.dto.SubjectRAINImportResponseDTO;
import com.markingsystem.entity.ScoreImportBatch;
import com.markingsystem.entity.ScoreImportItem;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.exception.SubjectRAINException;
import com.markingsystem.repository.ScoreImportBatchRepository;
import com.markingsystem.repository.ScoreImportItemRepository;
import com.markingsystem.repository.ScoreImportResourceItemRepository;
import com.markingsystem.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubjectScoreImportServiceTest {

    @Test
    void listsImportHistoryByStudentRecord() throws Exception {
        ScoreImportBatch batch = batch("batch-1", "2025-2026", "软工2401", 2, 1);
        List<ScoreImportItem> items = List.of(
                item(11L, "batch-1", 1L, 101L, ScoreImportItem.ActionType.CREATED, "大学英语", null, 88D),
                item(12L, "batch-1", 1L, 102L, ScoreImportItem.ActionType.UPDATED, "大学物理", 70D, 80D),
                item(13L, "batch-1", 2L, 103L, ScoreImportItem.ActionType.CREATED, "体育C", null, 95D)
        );
        SubjectScoreImportService service = service(
                List.of(batch),
                new ArrayList<>(items),
                List.of(student(1L, "20240001", "张三", "软工2401"), student(2L, "20240002", "李四", "软工2401")),
                new RecordingScoreService()
        );

        List<Map<String, Object>> rows = service.listImportStudentRecords();

        assertEquals(2, rows.size());
        assertEquals("batch-1:1", rows.get(0).get("id"));
        assertEquals("20240001", rows.get(0).get("studentNo"));
        assertEquals("张三", rows.get(0).get("studentName"));
        assertEquals(1, rows.get(0).get("createdScoreCount"));
        assertEquals(1, rows.get(0).get("updatedScoreCount"));
        assertEquals("大学英语，大学物理", rows.get(0).get("subjectSummary"));
        assertEquals("batch-1:2", rows.get(1).get("id"));
    }

    @Test
    void listsImportStudentRecordsByBatchAndKeyword() throws Exception {
        ScoreImportBatch firstBatch = batch("batch-1", "2025-2026", "软工2401", 2, 1);
        ScoreImportBatch secondBatch = batch("batch-2", "2025-2026", "计科231", 1, 0);
        List<ScoreImportItem> items = List.of(
                item(11L, "batch-1", 1L, 101L, ScoreImportItem.ActionType.CREATED, "大学英语", null, 88D),
                item(12L, "batch-1", 2L, 102L, ScoreImportItem.ActionType.CREATED, "体育C", null, 95D),
                item(13L, "batch-2", 3L, 103L, ScoreImportItem.ActionType.CREATED, "大学物理", null, 91D)
        );
        SubjectScoreImportService service = service(
                List.of(firstBatch, secondBatch),
                new ArrayList<>(items),
                List.of(
                        student(1L, "20240001", "张三", "软工2401"),
                        student(2L, "20240002", "李四", "软工2401"),
                        student(3L, "20240003", "王五", "计科231")
                ),
                new RecordingScoreService()
        );

        List<Map<String, Object>> rows = service.listImportStudentRecords("batch-1", "体育");

        assertEquals(1, rows.size());
        assertEquals("20240002", rows.get(0).get("studentNo"));
        assertEquals("体育C", rows.get(0).get("subjectSummary"));
    }

    @Test
    void paginatesImportStudentRecordsByBatchAndKeyword() throws Exception {
        ScoreImportBatch batch = batch("batch-1", "2025-2026", "软工2401", 3, 0);
        List<ScoreImportItem> items = List.of(
                item(11L, "batch-1", 1L, 101L, ScoreImportItem.ActionType.CREATED, "体育C", null, 95D),
                item(12L, "batch-1", 2L, 102L, ScoreImportItem.ActionType.CREATED, "体育D", null, 96D),
                item(13L, "batch-1", 3L, 103L, ScoreImportItem.ActionType.CREATED, "体育E", null, 97D)
        );
        SubjectScoreImportService service = service(
                List.of(batch),
                new ArrayList<>(items),
                List.of(
                        student(1L, "20240001", "张三", "软工2401"),
                        student(2L, "20240002", "李四", "软工2401"),
                        student(3L, "20240003", "王五", "软工2401")
                ),
                new RecordingScoreService()
        );

        Map<String, Object> page = service.listImportStudentRecordsPage("batch-1", "体育", 1, 2);

        assertEquals(3, page.get("totalElements"));
        assertEquals(2, page.get("totalPages"));
        assertEquals(1, page.get("page"));
        assertEquals(2, page.get("size"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertEquals(1, content.size());
        assertEquals("20240003", content.get(0).get("studentNo"));
    }

    @Test
    void filtersImportBatchesByCreatedByUserForTeacher() throws Exception {
        ScoreImportBatch ownBatch = batch("batch-1", "2025-2026", "软工2401", 2, 1);
        ownBatch.setCreatedByUserId(10L);
        ownBatch.setCreatedByName("老师甲");
        ownBatch.setCreatedByRole("TEACHER");
        ScoreImportBatch otherBatch = batch("batch-2", "2025-2026", "计科231", 1, 0);
        otherBatch.setCreatedByUserId(20L);
        otherBatch.setCreatedByName("老师乙");
        otherBatch.setCreatedByRole("TEACHER");
        SubjectScoreImportService service = service(
                List.of(ownBatch, otherBatch),
                new ArrayList<>(),
                List.of(),
                new RecordingScoreService()
        );

        List<Map<String, Object>> teacherRows = service.listImportBatches("TEACHER", 10L);
        List<Map<String, Object>> adminRows = service.listImportBatches("ADMIN", null);

        assertEquals(1, teacherRows.size());
        assertEquals("batch-1", teacherRows.get(0).get("id"));
        assertEquals("老师甲", teacherRows.get(0).get("createdByName"));
        assertEquals(2, adminRows.size());
    }

    @Test
    void rejectsTeacherOperationsOnOtherTeacherImportBatch() throws Exception {
        ScoreImportBatch otherBatch = batch("batch-1", "2025-2026", "软工2401", 2, 1);
        otherBatch.setCreatedByUserId(20L);
        ArrayList<ScoreImportItem> items = new ArrayList<>(List.of(
                item(11L, "batch-1", 1L, 101L, ScoreImportItem.ActionType.CREATED, "大学英语", null, 88D)
        ));
        SubjectScoreImportService service = service(
                List.of(otherBatch),
                items,
                List.of(student(1L, "20240001", "张三", "软工2401")),
                new RecordingScoreService()
        );

        assertThrows(SubjectRAINException.class, () -> service.deleteImportStudentRecord("batch-1", 1L, "TEACHER", 10L));
        assertThrows(SubjectRAINException.class, () -> service.undoImportBatch("batch-1", "TEACHER", 10L));
    }

    @Test
    void deletesOnlySelectedStudentRecordFromImportHistory() throws Exception {
        ScoreImportBatch batch = batch("batch-1", "2025-2026", "软工2401", 2, 1);
        ArrayList<ScoreImportItem> items = new ArrayList<>(List.of(
                item(11L, "batch-1", 1L, 101L, ScoreImportItem.ActionType.CREATED, "大学英语", null, 88D),
                item(12L, "batch-1", 1L, 102L, ScoreImportItem.ActionType.UPDATED, "大学物理", 70D, 80D),
                item(13L, "batch-1", 2L, 103L, ScoreImportItem.ActionType.CREATED, "体育C", null, 95D)
        ));
        RecordingScoreService scoreService = new RecordingScoreService();
        scoreService.existingScore = score(102L, 1L, 80D);
        SubjectScoreImportService service = service(
                List.of(batch),
                items,
                List.of(student(1L, "20240001", "张三", "软工2401"), student(2L, "20240002", "李四", "软工2401")),
                scoreService
        );

        SubjectRAINImportResponseDTO response = service.deleteImportStudentRecord("batch-1", 1L);

        assertEquals(List.of(101L), scoreService.deletedScoreIds);
        assertEquals(70D, scoreService.savedScore.getScore());
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).getStudentId());
        assertEquals(1, batch.getCreatedScoreCount());
        assertEquals(0, batch.getUpdatedScoreCount());
        assertFalse(Boolean.TRUE.equals(batch.getUndone()));
        assertEquals(1, response.getUndoImportInfo().get("deletedCreatedScoreCount"));
        assertEquals(1, response.getUndoImportInfo().get("restoredUpdatedScoreCount"));
    }

    @Test
    void undoImportBatchUsesRemainingRecordsAfterStudentRecordDeleted() throws Exception {
        ScoreImportBatch batch = batch("batch-1", "2025-2026", "软工2401", 2, 1);
        ArrayList<ScoreImportItem> items = new ArrayList<>(List.of(
                item(11L, "batch-1", 1L, 101L, ScoreImportItem.ActionType.CREATED, "大学英语", null, 88D),
                item(12L, "batch-1", 1L, 102L, ScoreImportItem.ActionType.UPDATED, "大学物理", 70D, 80D),
                item(13L, "batch-1", 2L, 103L, ScoreImportItem.ActionType.CREATED, "体育C", null, 95D)
        ));
        RecordingScoreService scoreService = new RecordingScoreService();
        scoreService.existingScore = score(102L, 1L, 80D);
        SubjectScoreImportService service = service(
                List.of(batch),
                items,
                List.of(student(1L, "20240001", "张三", "软工2401"), student(2L, "20240002", "李四", "软工2401")),
                scoreService
        );

        service.deleteImportStudentRecord("batch-1", 1L);
        SubjectRAINImportResponseDTO response = service.undoImportBatch("batch-1");

        assertEquals(List.of(101L, 103L), scoreService.deletedScoreIds);
        assertEquals(1, response.getUndoImportInfo().get("deletedCreatedScoreCount"));
        assertEquals(0, response.getUndoImportInfo().get("restoredUpdatedScoreCount"));
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).getStudentId());
        assertEquals(true, batch.getUndone());
    }

    private SubjectScoreImportService service(
            List<ScoreImportBatch> batches,
            ArrayList<ScoreImportItem> items,
            List<Student> students,
            RecordingScoreService scoreService
    ) throws Exception {
        SubjectScoreImportService service = new SubjectScoreImportService();
        setField(service, "scoreImportBatchRepository", batchRepository(batches));
        setField(service, "scoreImportItemRepository", itemRepository(items));
        setField(service, "scoreImportResourceItemRepository", resourceRepository());
        setField(service, "studentRepository", studentRepository(students));
        setField(service, "subjectScoreRAINServices", scoreService);
        return service;
    }

    private ScoreImportBatchRepository batchRepository(List<ScoreImportBatch> batches) {
        return proxy(ScoreImportBatchRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Sort) {
                return batches;
            }
            if ("findById".equals(method.getName())) {
                return batches.stream().filter(batch -> batch.getId().equals(args[0])).findFirst();
            }
            if ("save".equals(method.getName())) {
                return args[0];
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        });
    }

    private ScoreImportItemRepository itemRepository(ArrayList<ScoreImportItem> items) {
        return proxy(ScoreImportItemRepository.class, (proxy, method, args) -> {
            if ("findByBatchIdOrderByIdAsc".equals(method.getName())) {
                return items.stream().filter(item -> item.getBatchId().equals(args[0])).toList();
            }
            if ("findByBatchIdAndStudentIdOrderByIdAsc".equals(method.getName())) {
                return items.stream()
                        .filter(item -> item.getBatchId().equals(args[0]) && item.getStudentId().equals(args[1]))
                        .toList();
            }
            if ("deleteAll".equals(method.getName())) {
                @SuppressWarnings("unchecked")
                Collection<ScoreImportItem> deleted = (Collection<ScoreImportItem>) args[0];
                items.removeAll(deleted);
                return null;
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        });
    }

    private ScoreImportResourceItemRepository resourceRepository() {
        return proxy(ScoreImportResourceItemRepository.class, (proxy, method, args) -> {
            if ("findByBatchIdOrderByIdAsc".equals(method.getName())) {
                return List.of();
            }
            if ("countByBatchId".equals(method.getName())) {
                return 0L;
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        });
    }

    private StudentRepository studentRepository(List<Student> students) {
        return proxy(StudentRepository.class, (proxy, method, args) -> {
            if ("findAllById".equals(method.getName())) {
                @SuppressWarnings("unchecked")
                Iterable<Long> ids = (Iterable<Long>) args[0];
                List<Long> idList = new ArrayList<>();
                ids.forEach(idList::add);
                return students.stream().filter(student -> idList.contains(student.getId())).toList();
            }
            if ("findById".equals(method.getName())) {
                return students.stream().filter(student -> student.getId().equals(args[0])).findFirst();
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, ThrowingInvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> handler.invoke(proxy, method, args));
    }

    private Object defaultProxyMethod(Object proxy, String methodName, Object[] args) {
        if ("toString".equals(methodName)) {
            return "SubjectScoreImportServiceTestProxy";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == args[0];
        }
        throw new UnsupportedOperationException(methodName);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SubjectScoreImportService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ScoreImportBatch batch(String id, String semester, String className, int created, int updated) {
        ScoreImportBatch batch = new ScoreImportBatch();
        batch.setId(id);
        batch.setSemester(semester);
        batch.setClassName(className);
        batch.setCreatedScoreCount(created);
        batch.setUpdatedScoreCount(updated);
        batch.setUnchangedScoreCount(0);
        batch.setErrorCount(0);
        batch.setUndone(false);
        batch.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        return batch;
    }

    private ScoreImportItem item(
            Long id,
            String batchId,
            Long studentId,
            Long scoreId,
            ScoreImportItem.ActionType actionType,
            String subjectName,
            Double previousScore,
            Double currentScore
    ) {
        ScoreImportItem item = new ScoreImportItem();
        item.setId(id);
        item.setBatchId(batchId);
        item.setStudentId(studentId);
        item.setScoreId(scoreId);
        item.setActionType(actionType);
        item.setSubjectName(subjectName);
        item.setPreviousScore(previousScore);
        item.setCurrentScore(currentScore);
        item.setSemester("2025-2026");
        return item;
    }

    private Student student(Long id, String studentNo, String name, String className) {
        Student student = new Student();
        student.setId(id);
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setClassName(className);
        return student;
    }

    private SubjectScoreRAIN score(Long id, Long studentId, Double value) {
        SubjectScoreRAIN score = new SubjectScoreRAIN();
        score.setId(id);
        score.setStudentId(studentId);
        score.setScore(value);
        return score;
    }

    private interface ThrowingInvocationHandler {
        Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    private static class RecordingScoreService extends SubjectScoreRAINServices {
        private final List<Long> deletedScoreIds = new ArrayList<>();
        private SubjectScoreRAIN existingScore;
        private SubjectScoreRAIN savedScore;

        @Override
        public void deleteById(Long id) {
            deletedScoreIds.add(id);
        }

        @Override
        public Optional<SubjectScoreRAIN> findPlainById(Long id) {
            return existingScore != null && existingScore.getId().equals(id)
                    ? Optional.of(existingScore)
                    : Optional.empty();
        }

        @Override
        public void saveSingle(SubjectScoreRAIN score) {
            savedScore = score;
        }

        @Override
        public List<SubjectScoreRAIN> getScoresByStudentId(Long studentId) {
            return List.of();
        }

        @Override
        public List<SubjectScoreRAIN> getScoresByUserId(Long userId) {
            return List.of();
        }

        @Override
        public List<SubjectScoreRAIN> getScoresBySubjectId(Long subjectId) {
            return List.of();
        }
    }
}
