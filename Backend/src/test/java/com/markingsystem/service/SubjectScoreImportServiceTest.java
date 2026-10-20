package com.markingsystem.service;

import com.markingsystem.dto.SubjectRAINImportResponseDTO;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.ScoreImportBatch;
import com.markingsystem.entity.ScoreImportItem;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.entity.User;
import com.markingsystem.exception.SubjectRAINException;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.ScoreImportBatchRepository;
import com.markingsystem.repository.ScoreImportItemRepository;
import com.markingsystem.repository.ScoreImportResourceItemRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.SubjectRAINRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.FileParseHelper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void validateImportRunsDryRunWithoutWritesAndSummarizesChanges() throws Exception {
        FileParseHelper.ParsedFileData fileData = new FileParseHelper.ParsedFileData(
                List.of("学号", "姓名", "班级", "大学英语", "大学数学"),
                List.of(
                        List.of("20240001", "张三", "软工2401", "88", "90"),
                        List.of("20240099", "王五", "软工2401", "60", "")
                ),
                "scores.xlsx");

        SubjectRAIN english = subject(1L, "大学英语");
        Student boundStudent = student(1L, "20240001", "张三", "软工2401");
        boundStudent.setUserId(11L);
        User boundUser = user(11L, "20240001");
        SubjectScoreRAIN existingScore = score(101L, 1L, 70D);
        existingScore.setUserId(11L);
        existingScore.setSubjectId(1L);
        existingScore.setSemester("2025-2026");

        RecordingScoreService scoreService = new RecordingScoreService();
        scoreService.existingScoresBySemester = Map.of("U_11_1", existingScore);

        SubjectScoreImportService service = dryRunService(
                fileData,
                List.of(english),
                List.of(boundStudent),
                List.of(boundUser),
                scoreService
        );

        SubjectRAINImportResponseDTO response = service.validateImport(
                multipartFile("scores.xlsx"),
                "excel",
                "2025-2026",
                List.of("大学英语", "大学数学"),
                "学号",
                "学号",
                "姓名",
                "班级",
                List.of("软工2401"),
                List.of("软工2401")
        );

        Map<String, Object> info = response.getScoreImportInfo();
        assertEquals(Boolean.TRUE, info.get("dryRun"));
        assertNull(info.get("importBatchId"));
        assertEquals(Boolean.FALSE, info.get("undoAvailable"));
        assertEquals(2, info.get("totalRowCount"));
        assertEquals(2, info.get("successRowCount"));
        assertEquals(0, info.get("errorCount"));
        assertEquals(2L, info.get("createdScoreCount"));
        assertEquals(1L, info.get("updatedScoreCount"));
        assertEquals(0L, info.get("unchangedScoreCount"));
        assertEquals(1L, info.get("createdSubjectCount"));
        assertEquals(1L, info.get("createdStudentCount"));
        assertEquals(1L, info.get("createdUserCount"));
        assertEquals(List.of("大学数学"), response.getNewSubjects());

        // 试算用副本承载新分数，受管实体不能被修改（避免脏数据刷库）
        assertEquals(70D, existingScore.getScore());
        // 全程不写库：不保存成绩、不删除成绩
        assertTrue(scoreService.batchSavedScores.isEmpty());
        assertNull(scoreService.savedScore);
        assertTrue(scoreService.deletedScoreIds.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) info.get("importDetails");
        assertEquals(2, details.size());

        Map<String, Object> firstRow = details.get(0);
        assertEquals(2, firstRow.get("rowNumber"));
        assertEquals("success", firstRow.get("status"));
        assertEquals(false, firstRow.get("newStudent"));
        assertEquals(false, firstRow.get("newUser"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstDeltas = (List<Map<String, Object>>) firstRow.get("scoreDeltas");
        assertEquals(2, firstDeltas.size());
        Map<String, Object> englishDelta = findScoreDelta(firstDeltas, "大学英语");
        assertEquals("updated", englishDelta.get("action"));
        assertEquals(70D, englishDelta.get("previousScore"));
        assertEquals(88D, englishDelta.get("newScore"));
        Map<String, Object> mathDelta = findScoreDelta(firstDeltas, "大学数学");
        assertEquals("created", mathDelta.get("action"));
        assertNull(mathDelta.get("previousScore"));
        assertEquals(90D, mathDelta.get("newScore"));

        Map<String, Object> secondRow = details.get(1);
        assertEquals(3, secondRow.get("rowNumber"));
        assertEquals("success", secondRow.get("status"));
        assertEquals(true, secondRow.get("newStudent"));
        assertEquals(true, secondRow.get("newUser"));
    }

    @Test
    void validateImportLocatesFailedRowsWithoutThrowing() throws Exception {
        FileParseHelper.ParsedFileData fileData = new FileParseHelper.ParsedFileData(
                List.of("学号", "姓名", "班级", "大学英语"),
                List.of(
                        List.of("", "李四", "软工2401", "70"),
                        List.of("20240002", "李四", "未知班", "70"),
                        List.of("20240001", "张三", "软工2401", "88")
                ),
                "scores.xlsx");

        Student boundStudent = student(1L, "20240001", "张三", "软工2401");
        boundStudent.setUserId(11L);
        RecordingScoreService scoreService = new RecordingScoreService();

        SubjectScoreImportService service = dryRunService(
                fileData,
                List.of(subject(1L, "大学英语")),
                List.of(boundStudent),
                List.of(user(11L, "20240001")),
                scoreService
        );

        SubjectRAINImportResponseDTO response = service.validateImport(
                multipartFile("scores.xlsx"),
                "excel",
                "2025-2026",
                List.of("大学英语"),
                "学号",
                "学号",
                "姓名",
                "班级",
                List.of("软工2401"),
                List.of("软工2401")
        );

        Map<String, Object> info = response.getScoreImportInfo();
        assertEquals(3, info.get("totalRowCount"));
        assertEquals(2, info.get("errorCount"));
        @SuppressWarnings("unchecked")
        List<String> scoreErrors = (List<String>) info.get("scoreErrors");
        // 错误行完整返回（不再截断），行号定位到 Excel 实际行（表头为第1行）
        assertEquals(List.of("第2行：学号不能为空", "第3行：未知班 不在本次导入范围内"), scoreErrors);
        assertEquals(1L, info.get("createdScoreCount"));
        assertTrue(scoreService.batchSavedScores.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) info.get("importDetails");
        assertEquals(3, details.size());
        assertEquals("failed", details.get(0).get("status"));
        assertEquals(2, details.get(0).get("rowNumber"));
        assertEquals("failed", details.get(1).get("status"));
        assertEquals(3, details.get(1).get("rowNumber"));
        assertEquals("success", details.get(2).get("status"));
        assertEquals("20240001", details.get(2).get("studentNo"));
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

    /** 组装试算（dry-run）专用服务：所有写库入口一旦触发即断言失败。 */
    private SubjectScoreImportService dryRunService(
            FileParseHelper.ParsedFileData fileData,
            List<SubjectRAIN> subjects,
            List<Student> students,
            List<User> users,
            RecordingScoreService scoreService
    ) throws Exception {
        SubjectScoreImportService service = new SubjectScoreImportService();
        setField(service, "fileParseHelper", new FileParseHelper() {
            @Override
            public ParsedFileData parse(MultipartFile file) {
                return fileData;
            }
        });
        setField(service, "subjectRAINRepository", proxy(SubjectRAINRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                throw new AssertionError("试算不应新建科目");
            }
            if ("findAll".equals(method.getName())) {
                return subjects;
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        }));
        setField(service, "classInfoRepository", proxy(ClassInfoRepository.class, (proxy, method, args) -> {
            if ("findByName".equals(method.getName())) {
                return "软工2401".equals(args[0])
                        ? Optional.of(classInfo(1L, "软工2401", 9L))
                        : Optional.empty();
            }
            if ("save".equals(method.getName())) {
                throw new AssertionError("试算不应写班级");
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        }));
        setField(service, "studentRepository", proxy(StudentRepository.class, (proxy, method, args) -> {
            if ("findByStudentNo".equals(method.getName())) {
                return students.stream().filter(student -> student.getStudentNo().equals(args[0])).findFirst();
            }
            if ("save".equals(method.getName())) {
                throw new AssertionError("试算不应新建学生");
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        }));
        setField(service, "userRepository", proxy(UserRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return users.stream().filter(user -> user.getId().equals(args[0])).findFirst();
            }
            if ("findByUsername".equals(method.getName())) {
                return users.stream().filter(user -> user.getUsername().equals(args[0])).findFirst();
            }
            if ("save".equals(method.getName())) {
                throw new AssertionError("试算不应新建账号");
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        }));
        setField(service, "scoreImportBatchRepository", proxy(ScoreImportBatchRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                throw new AssertionError("试算不应创建导入批次");
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        }));
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

    private SubjectRAIN subject(Long id, String name) {
        SubjectRAIN subject = new SubjectRAIN();
        subject.setId(id);
        subject.setSubjectName(name);
        return subject;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStudentNo(username);
        return user;
    }

    private ClassInfo classInfo(Long id, String name, Long teacherId) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(id);
        classInfo.setName(name);
        classInfo.setTeacherId(teacherId);
        return classInfo;
    }

    private MultipartFile multipartFile(String fileName) {
        return proxy(MultipartFile.class, (proxy, method, args) -> {
            if ("getOriginalFilename".equals(method.getName())) {
                return fileName;
            }
            return defaultProxyMethod(proxy, method.getName(), args);
        });
    }

    private Map<String, Object> findScoreDelta(List<Map<String, Object>> deltas, String subjectName) {
        return deltas.stream()
                .filter(delta -> subjectName.equals(delta.get("subjectName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少科目变更明细: " + subjectName));
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
        private final List<SubjectScoreRAIN> batchSavedScores = new ArrayList<>();
        private Map<String, SubjectScoreRAIN> existingScoresBySemester = Map.of();
        private SubjectScoreRAIN existingScore;
        private SubjectScoreRAIN savedScore;

        @Override
        public Map<String, SubjectScoreRAIN> loadExistingScores(String semester) {
            // 与真实实现一致：返回可修改副本（导入过程会往里回填新成绩）
            return new HashMap<>(existingScoresBySemester);
        }

        @Override
        public void batchSave(List<SubjectScoreRAIN> scores) {
            batchSavedScores.addAll(scores);
        }

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
