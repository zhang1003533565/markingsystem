package com.markingsystem.service;

import com.markingsystem.dto.SubjectRAINImportResponseDTO;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.ScoreImportBatch;
import com.markingsystem.entity.ScoreImportItem;
import com.markingsystem.entity.ScoreImportResourceItem;
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
import com.markingsystem.repository.UserRoleRepository;
import com.markingsystem.util.FileParseHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class SubjectScoreImportService {
    private static final Logger log = LoggerFactory.getLogger(SubjectScoreImportService.class);
    private static final int DEFAULT_STUDENT_RECORD_PAGE_SIZE = 20;
    private static final int MAX_STUDENT_RECORD_PAGE_SIZE = 100;

    @Autowired
    private SubjectRAINRepository subjectRAINRepository;

    @Autowired
    private SubjectScoreRAINServices subjectScoreRAINServices;

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentAccountSyncService studentAccountSyncService;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private ScoreImportBatchRepository scoreImportBatchRepository;

    @Autowired
    private ScoreImportItemRepository scoreImportItemRepository;

    @Autowired
    private ScoreImportResourceItemRepository scoreImportResourceItemRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private FileParseHelper fileParseHelper;

    public List<Map<String, Object>> listImportBatches() {
        return listImportBatches("ADMIN", null);
    }

    public List<Map<String, Object>> listImportBatches(String role, Long userId) {
        return scoreImportBatchRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(batch -> canAccessBatch(batch, role, userId))
                .map(batch -> {
                    List<ScoreImportItem> scoreItems = scoreImportItemRepository.findByBatchIdOrderByIdAsc(batch.getId());
                    long resourceChangeCount = scoreImportResourceItemRepository.countByBatchId(batch.getId());
                    long studentRecordCount = scoreItems.stream()
                            .map(ScoreImportItem::getStudentId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", batch.getId());
                    row.put("semester", batch.getSemester());
                    row.put("className", batch.getClassName());
                    row.put("createdScoreCount", batch.getCreatedScoreCount());
                    row.put("updatedScoreCount", batch.getUpdatedScoreCount());
                    row.put("unchangedScoreCount", batch.getUnchangedScoreCount());
                    row.put("errorCount", batch.getErrorCount());
                    row.put("undone", Boolean.TRUE.equals(batch.getUndone()));
                    row.put("undoAvailable", !Boolean.TRUE.equals(batch.getUndone())
                            && ((batch.getCreatedScoreCount() != null && batch.getCreatedScoreCount() > 0)
                            || (batch.getUpdatedScoreCount() != null && batch.getUpdatedScoreCount() > 0)
                            || resourceChangeCount > 0));
                    row.put("resourceChangeCount", resourceChangeCount);
                    row.put("createdByUserId", batch.getCreatedByUserId());
                    row.put("createdByName", batch.getCreatedByName());
                    row.put("createdByRole", batch.getCreatedByRole());
                    row.put("studentRecordCount", studentRecordCount);
                    row.put("createdAt", resolveBatchCreatedAt(batch, scoreItems));
                    return row;
                })
                .toList();
    }

    public List<Map<String, Object>> listImportStudentRecords() {
        return listImportStudentRecords(null, null, "ADMIN", null);
    }

    public List<Map<String, Object>> listImportStudentRecords(String batchId, String keyword) {
        return listImportStudentRecords(batchId, keyword, "ADMIN", null);
    }

    public List<Map<String, Object>> listImportStudentRecords(String batchId, String keyword, String role, Long userId) {
        String normalizedBatchId = batchId == null ? "" : batchId.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        List<ScoreImportBatch> batches = scoreImportBatchRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoreImportBatch batch : batches) {
            if (!canAccessBatch(batch, role, userId)) {
                continue;
            }
            if (!normalizedBatchId.isBlank() && !normalizedBatchId.equals(batch.getId())) {
                continue;
            }
            List<ScoreImportItem> items = scoreImportItemRepository.findByBatchIdOrderByIdAsc(batch.getId());
            if (items.isEmpty()) {
                continue;
            }
            Map<Long, List<ScoreImportItem>> itemsByStudentId = items.stream()
                    .filter(item -> item.getStudentId() != null)
                    .collect(Collectors.groupingBy(
                            ScoreImportItem::getStudentId,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            Map<Long, Student> studentMap = studentRepository.findAllById(itemsByStudentId.keySet()).stream()
                    .collect(Collectors.toMap(Student::getId, student -> student, (left, right) -> left));

            for (Map.Entry<Long, List<ScoreImportItem>> entry : itemsByStudentId.entrySet()) {
                Map<String, Object> row = toImportStudentRecordRow(batch, entry.getKey(), studentMap.get(entry.getKey()), entry.getValue());
                if (matchesImportStudentRecordKeyword(row, normalizedKeyword)) {
                    rows.add(row);
                }
            }
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.getOrDefault("createdAt", ""))).reversed()
                .thenComparing(row -> String.valueOf(row.getOrDefault("studentNo", ""))));
        return rows;
    }

    public Map<String, Object> listImportStudentRecordsPage(String batchId, String keyword, int page, int size, String role, Long userId) {
        List<Map<String, Object>> rows = listImportStudentRecords(batchId, keyword, role, userId);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0
                ? DEFAULT_STUDENT_RECORD_PAGE_SIZE
                : Math.min(size, MAX_STUDENT_RECORD_PAGE_SIZE);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        long offset = (long) safePage * safeSize;
        int fromIndex = offset >= totalElements ? totalElements : (int) offset;
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("content", rows.subList(fromIndex, toIndex));
        pageData.put("totalElements", totalElements);
        pageData.put("totalPages", totalPages);
        pageData.put("page", safePage);
        pageData.put("size", safeSize);
        return pageData;
    }

    public Map<String, Object> listImportStudentRecordsPage(String batchId, String keyword, int page, int size) {
        return listImportStudentRecordsPage(batchId, keyword, page, size, "ADMIN", null);
    }

    @Transactional
    public SubjectRAINImportResponseDTO importSubjectsAndScores(
            MultipartFile file,
            String fileType,
            String semester,
            List<String> selectedSubjectNames,
            String accountColumn,
            String passwordColumn,
            String nameColumn,
            String classColumn,
            List<String> classValues,
            List<String> classNames,
            Long createdByUserId,
            String createdByRole) throws IOException {
        return doImportSubjectsAndScores(file, fileType, semester, selectedSubjectNames,
                accountColumn, passwordColumn, nameColumn, classColumn, classValues, classNames,
                createdByUserId, createdByRole, false);
    }

    /**
     * 导入前试算校验（dry-run）：执行完整的导入校验与变更推演，但全程不写库。
     * 返回与真实导入一致的 scoreImportInfo/importDetails 结构，供前端「数据预览」展示。
     */
    @Transactional(readOnly = true)
    public SubjectRAINImportResponseDTO validateImport(
            MultipartFile file,
            String fileType,
            String semester,
            List<String> selectedSubjectNames,
            String accountColumn,
            String passwordColumn,
            String nameColumn,
            String classColumn,
            List<String> classValues,
            List<String> classNames) throws IOException {
        return doImportSubjectsAndScores(file, fileType, semester, selectedSubjectNames,
                accountColumn, passwordColumn, nameColumn, classColumn, classValues, classNames,
                null, null, true);
    }

    private SubjectRAINImportResponseDTO doImportSubjectsAndScores(
            MultipartFile file,
            String fileType,
            String semester,
            List<String> selectedSubjectNames,
            String accountColumn,
            String passwordColumn,
            String nameColumn,
            String classColumn,
            List<String> classValues,
            List<String> classNames,
            Long createdByUserId,
            String createdByRole,
            boolean dryRun) throws IOException {

        log.info("{}: subjectCount={}, fileType={}, semester={}",
                dryRun ? "开始试算校验科目和成绩" : "开始导入科目和成绩",
                selectedSubjectNames.size(), fileType, semester);

        SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> importDetails = new ArrayList<>();

        try {
            FileParseHelper.ParsedFileData fileData = fileParseHelper.parse(file);
            int totalRowCount = fileData.getRows().size();
            log.info("文件解析完成: rowCount={}", totalRowCount);

            validateHeaders(fileData, accountColumn, passwordColumn, nameColumn, classColumn, classValues, classNames);

            List<String> normalizedClassValues = classValues.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
            List<String> normalizedClassNames = classNames.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .toList();
            if (normalizedClassValues.isEmpty()) {
                throw new SubjectRAINException("必须至少选择一个分类值");
            }
            if (normalizedClassNames.isEmpty()) {
                throw new SubjectRAINException("必须至少选择一个系统班级");
            }
            if (normalizedClassValues.size() != normalizedClassNames.size()) {
                throw new SubjectRAINException("分类值与系统班级数量不一致");
            }

            Map<String, ClassInfo> selectedClassInfoMap = new HashMap<>();
            for (String className : normalizedClassNames) {
                ClassInfo classInfo = classInfoRepository.findByName(className)
                        .orElseThrow(() -> new SubjectRAINException("指定班级不存在: " + className));
                if (classInfo.getTeacherId() == null) {
                    throw new SubjectRAINException("指定班级未绑定所属老师: " + className);
                }
                selectedClassInfoMap.put(className, classInfo);
            }

            Map<String, String> classValueToNameMap = new HashMap<>();
            for (int i = 0; i < normalizedClassValues.size(); i++) {
                classValueToNameMap.put(normalizedClassValues.get(i), normalizedClassNames.get(i));
            }

            ScoreImportBatch importBatch = null;
            if (!dryRun) {
                importBatch = new ScoreImportBatch();
                importBatch.setId(UUID.randomUUID().toString());
                importBatch.setSemester(semester);
                importBatch.setClassName(String.join("，", normalizedClassNames));
                importBatch.setCreatedByUserId(createdByUserId);
                importBatch.setCreatedByRole(normalizeRole(createdByRole));
                importBatch.setCreatedByName(resolveUserDisplayName(createdByUserId));
                scoreImportBatchRepository.save(importBatch);
            }

            List<ImportResourceChange> resourceChanges = new ArrayList<>();
            Set<String> resourceKeys = new HashSet<>();
            List<String> newSubjectNames = new ArrayList<>();
            AtomicLong fakeIdSequence = new AtomicLong(-1L);

            Map<String, SubjectRAIN> subjectMap = syncSubjects(selectedSubjectNames, resourceChanges, resourceKeys,
                    dryRun, fakeIdSequence, newSubjectNames);
            log.info("科目同步完成: subjectCount={}", subjectMap.size());

            Map<String, SubjectScoreRAIN> existingScores = subjectScoreRAINServices.loadExistingScores(semester);
            log.info("已加载已有成绩记录: count={}", existingScores.size());

            List<ImportScoreChange> scoreChanges = processDataRows(
                    fileData,
                    subjectMap,
                    existingScores,
                    semester,
                    accountColumn,
                    passwordColumn,
                    nameColumn,
                    classColumn,
                    classValueToNameMap,
                    selectedClassInfoMap,
                    resourceChanges,
                    resourceKeys,
                    errors,
                    importDetails,
                    dryRun,
                    fakeIdSequence
            );

            List<SubjectScoreRAIN> scoresToSave = scoreChanges.stream()
                    .filter(change -> change.getActionType() != null)
                    .map(ImportScoreChange::getScore)
                    .toList();

            if (!dryRun && !scoresToSave.isEmpty()) {
                subjectScoreRAINServices.batchSave(scoresToSave);
                persistImportItems(importBatch.getId(), semester, scoreChanges);
                log.info("保存成绩记录: count={}", scoresToSave.size());
            }

            if (!dryRun && !resourceChanges.isEmpty()) {
                persistResourceItems(importBatch.getId(), resourceChanges);
            }

            int successRowCount = calculateSuccessRowCount(fileData, errors);
            long createdCount = scoreChanges.stream().filter(change -> change.getActionType() == ScoreImportItem.ActionType.CREATED).count();
            long updatedCount = scoreChanges.stream().filter(change -> change.getActionType() == ScoreImportItem.ActionType.UPDATED).count();
            long unchangedCount = scoreChanges.stream().filter(change -> change.getActionType() == null).count();
            long createdSubjectCount = dryRun
                    ? newSubjectNames.size()
                    : resourceChanges.stream().filter(change -> change.getResourceType() == ScoreImportResourceItem.ResourceType.SUBJECT).count();
            long createdStudentCount = dryRun
                    ? countFlaggedDetails(importDetails, "newStudent")
                    : resourceChanges.stream().filter(change -> change.getResourceType() == ScoreImportResourceItem.ResourceType.STUDENT).count();
            long createdUserCount = dryRun
                    ? countFlaggedDetails(importDetails, "newUser")
                    : resourceChanges.stream().filter(change -> change.getResourceType() == ScoreImportResourceItem.ResourceType.USER).count();

            List<String> updateMessages = scoreChanges.stream()
                    .filter(change -> change.getActionType() == ScoreImportItem.ActionType.UPDATED)
                    .map(change -> String.format("%s：%.1f -> %.1f", change.getSubjectName(), change.getPreviousScore(), change.getScore().getScore()))
                    .distinct()
                    .limit(10)
                    .toList();
            List<String> unchangedMessages = scoreChanges.stream()
                    .filter(change -> change.getActionType() == null)
                    .map(ImportScoreChange::getSubjectName)
                    .distinct()
                    .limit(10)
                    .toList();

            Map<String, Object> scoreImportInfo = new HashMap<>();
            scoreImportInfo.put("totalRowCount", totalRowCount);
            scoreImportInfo.put("successRowCount", successRowCount);
            scoreImportInfo.put("totalScoreCount", createdCount + updatedCount);
            scoreImportInfo.put("errorCount", errors.size());
            scoreImportInfo.put("scoreErrors", new ArrayList<>(errors));
            scoreImportInfo.put("createdScoreCount", createdCount);
            scoreImportInfo.put("updatedScoreCount", updatedCount);
            scoreImportInfo.put("unchangedScoreCount", unchangedCount);
            scoreImportInfo.put("createdSubjectCount", createdSubjectCount);
            scoreImportInfo.put("createdStudentCount", createdStudentCount);
            scoreImportInfo.put("createdUserCount", createdUserCount);
            scoreImportInfo.put("updatedScoreSubjects", updateMessages);
            scoreImportInfo.put("unchangedScoreSubjects", unchangedMessages);
            scoreImportInfo.put("dryRun", dryRun);
            scoreImportInfo.put("importBatchId", importBatch == null ? null : importBatch.getId());
            scoreImportInfo.put(
                    "undoAvailable",
                    !dryRun && (createdCount > 0 || updatedCount > 0 || createdSubjectCount > 0 || createdStudentCount > 0 || createdUserCount > 0)
            );
            scoreImportInfo.put("importDetails", importDetails);

            if (!dryRun) {
                importBatch.setCreatedScoreCount((int) createdCount);
                importBatch.setUpdatedScoreCount((int) updatedCount);
                importBatch.setUnchangedScoreCount((int) unchangedCount);
                importBatch.setErrorCount(errors.size());
                scoreImportBatchRepository.save(importBatch);
            }

            response.setNewSubjects(dryRun ? newSubjectNames : selectedSubjectNames);
            response.setFailedSubjects(errors);
            response.setScoreImportInfo(scoreImportInfo);

            log.info("导入完成: totalRows={}, successRows={}, created={}, updated={}, unchanged={}",
                    totalRowCount, successRowCount, createdCount, updatedCount, unchangedCount);
        } catch (Exception e) {
            log.error("导入失败", e);
            errors.add("导入失败: " + e.getMessage());
            throw e;
        }

        return response;
    }

    private void validateHeaders(
            FileParseHelper.ParsedFileData fileData,
            String accountColumn,
            String passwordColumn,
            String nameColumn,
            String classColumn,
            List<String> classValues,
            List<String> classNames) {
        if (!fileData.hasHeader("学号")) {
            throw new SubjectRAINException("表格中缺少'学号'列");
        }
        if (accountColumn == null || accountColumn.isBlank()
                || passwordColumn == null || passwordColumn.isBlank()
                || nameColumn == null || nameColumn.isBlank()) {
            throw new SubjectRAINException("账号字段、密码字段、姓名字段必须明确指定");
        }
        if (classColumn == null || classColumn.isBlank()) {
            throw new SubjectRAINException("必须指定班级字段");
        }
        validateOptionalHeader(fileData, accountColumn, "账号字段");
        validateOptionalHeader(fileData, passwordColumn, "初始密码字段");
        validateOptionalHeader(fileData, nameColumn, "姓名字段");
        validateOptionalHeader(fileData, classColumn, "班级字段");
        if (classValues == null || classValues.isEmpty()) {
            throw new SubjectRAINException("必须至少选择一个分类值");
        }
        if (classNames == null || classNames.isEmpty()) {
            throw new SubjectRAINException("必须至少选择一个系统班级");
        }
    }

    private void validateOptionalHeader(FileParseHelper.ParsedFileData fileData, String headerName, String label) {
        if (headerName != null && !headerName.isBlank() && !fileData.hasHeader(headerName)) {
            throw new SubjectRAINException(label + "不存在: " + headerName);
        }
    }

    private Map<String, SubjectRAIN> syncSubjects(
            List<String> selectedSubjectNames,
            List<ImportResourceChange> resourceChanges,
            Set<String> resourceKeys,
            boolean dryRun,
            AtomicLong fakeIdSequence,
            List<String> newSubjectNames) {
        Map<String, SubjectRAIN> subjectMap = new HashMap<>();
        List<SubjectRAIN> allSubjects = subjectRAINRepository.findAll();
        Map<String, SubjectRAIN> existingSubjectMap = allSubjects.stream()
                .collect(Collectors.toMap(
                        subject -> subject.getSubjectName().toLowerCase(),
                        subject -> subject,
                        (left, right) -> left
                ));

        for (String subjectName : selectedSubjectNames) {
            if (subjectName == null || subjectName.trim().isEmpty()) {
                continue;
            }

            String normalizedName = subjectName.trim();
            String key = normalizedName.toLowerCase();
            SubjectRAIN subject = existingSubjectMap.get(key);
            if (subject == null) {
                if (dryRun) {
                    // 试算模式：仅在内存中模拟新建科目（负数 id），不落库
                    subject = new SubjectRAIN();
                    subject.setId(fakeIdSequence.decrementAndGet());
                    subject.setSubjectName(normalizedName);
                    subject.setCategory(SubjectRAIN.Category.SUBJECT);
                    existingSubjectMap.put(key, subject);
                    newSubjectNames.add(normalizedName);
                } else {
                    subject = new SubjectRAIN();
                    subject.setSubjectName(normalizedName);
                    subject.setCategory(SubjectRAIN.Category.SUBJECT);
                    subject = subjectRAINRepository.save(subject);
                    existingSubjectMap.put(key, subject);
                    recordCreatedResource(
                            resourceChanges,
                            resourceKeys,
                            ScoreImportResourceItem.ResourceType.SUBJECT,
                            subject.getId(),
                            normalizedName
                    );
                    log.debug("新建科目: {}", normalizedName);
                }
            }

            subjectMap.put(normalizedName, subject);
        }

        return subjectMap;
    }

    private List<ImportScoreChange> processDataRows(
            FileParseHelper.ParsedFileData fileData,
            Map<String, SubjectRAIN> subjectMap,
            Map<String, SubjectScoreRAIN> existingScores,
            String semester,
            String accountColumn,
            String passwordColumn,
            String nameColumn,
            String classColumn,
            Map<String, String> classValueToNameMap,
            Map<String, ClassInfo> selectedClassInfoMap,
            List<ImportResourceChange> resourceChanges,
            Set<String> resourceKeys,
            List<String> errors,
            List<Map<String, Object>> importDetails,
            boolean dryRun,
            AtomicLong fakeIdSequence) {

        List<ImportScoreChange> scoreChanges = new ArrayList<>();
        Map<String, Student> studentCache = new HashMap<>();
        Map<String, User> fakeUserCache = new HashMap<>();

        int studentNoIndex = fileData.getHeaderIndex("学号");
        int nameIndex = fileData.getHeaderIndex(nameColumn);
        int accountIndex = fileData.getHeaderIndex(accountColumn);
        int passwordIndex = fileData.getHeaderIndex(passwordColumn);
        int classIndex = fileData.getHeaderIndex(classColumn);

        for (int rowIndex = 0; rowIndex < fileData.getRows().size(); rowIndex++) {
            List<Object> row = fileData.getRows().get(rowIndex);
            int rowNumber = rowIndex + 2;
                String importClassName = "";

            try {
                String studentNo = FileParseHelper.getStringValue(row.get(studentNoIndex));
                if (studentNo.isEmpty()) {
                    errors.add("第" + rowNumber + "行：学号不能为空");
                    importDetails.add(buildImportDetail(rowNumber, "", "", "", null, null, "failed", "学号不能为空"));
                    continue;
                }

                String studentName = nameIndex >= 0 ? FileParseHelper.getStringValue(row.get(nameIndex)) : "";
                String classValue = classIndex >= 0 ? FileParseHelper.getStringValue(row.get(classIndex)) : "";
                if (classValue.isBlank()) {
                    errors.add("第" + rowNumber + "行：班级字段不能为空");
                    importDetails.add(buildImportDetail(rowNumber, studentNo, studentName, "", null, null, "failed", "班级字段不能为空"));
                    continue;
                }

                importClassName = classValueToNameMap.get(classValue);
                if (importClassName == null || importClassName.isBlank()) {
                    errors.add("第" + rowNumber + "行：" + classValue + " 不在本次导入范围内");
                    importDetails.add(buildImportDetail(rowNumber, studentNo, studentName, classValue, null, null, "failed", "分类值不在本次导入范围内"));
                    continue;
                }

                ClassInfo classInfo = selectedClassInfoMap.get(importClassName);
                if (classInfo == null) {
                    errors.add("第" + rowNumber + "行：" + importClassName + " 不在本次导入班级范围内");
                    importDetails.add(buildImportDetail(rowNumber, studentNo, studentName, classValue, null, null, "failed", "系统班级不在本次导入范围内"));
                    continue;
                }

                boolean rowNewStudent = false;
                Student student;
                if (dryRun) {
                    student = resolveStudentForPreview(studentNo, studentName, importClassName,
                            classInfo.getTeacherId(), studentCache, fakeIdSequence);
                    rowNewStudent = student.getId() != null && student.getId() < 0;
                } else {
                    boolean studentExisted = studentRepository.findByStudentNo(studentNo).isPresent();
                    student = studentService.getOrCreateStudent(
                            studentNo,
                            studentName,
                            importClassName,
                            classInfo.getTeacherId(),
                            studentCache
                    );
                    if (!studentExisted) {
                        recordCreatedResource(
                                resourceChanges,
                                resourceKeys,
                                ScoreImportResourceItem.ResourceType.STUDENT,
                                student.getId(),
                                studentNo
                        );
                    }
                }

                String username = accountIndex >= 0 ? FileParseHelper.getStringValue(row.get(accountIndex)) : "";
                String initialPassword = passwordIndex >= 0 ? FileParseHelper.getStringValue(row.get(passwordIndex)) : "";
                String nickname = nameIndex >= 0 ? FileParseHelper.getStringValue(row.get(nameIndex)) : "";

                if (username.isEmpty()) {
                    errors.add("第" + rowNumber + "行：账号字段不能为空");
                    importDetails.add(buildImportDetail(rowNumber, studentNo, studentName, importClassName, null, null, "failed", "账号字段不能为空"));
                    continue;
                }
                if (initialPassword.isEmpty()) {
                    errors.add("第" + rowNumber + "行：密码字段不能为空");
                    importDetails.add(buildImportDetail(rowNumber, studentNo, studentName, importClassName, null, null, "failed", "密码字段不能为空"));
                    continue;
                }
                if (nickname.isEmpty()) {
                    errors.add("第" + rowNumber + "行：姓名字段不能为空");
                    importDetails.add(buildImportDetail(rowNumber, studentNo, studentName, importClassName, null, null, "failed", "姓名字段不能为空"));
                    continue;
                }

                boolean rowNewUser = false;
                User user;
                if (dryRun) {
                    user = resolveUserForPreview(student, username, fakeUserCache, fakeIdSequence);
                    rowNewUser = user.getId() != null && user.getId() < 0;
                } else {
                    boolean userExisted = (student.getUserId() != null && userRepository.findById(student.getUserId()).isPresent())
                            || userRepository.findByUsername(username).isPresent();
                    user = studentAccountSyncService.ensureStudentAccount(student, username, initialPassword, nickname);
                    if (!userExisted) {
                        recordCreatedResource(
                                resourceChanges,
                                resourceKeys,
                                ScoreImportResourceItem.ResourceType.USER,
                                user.getId(),
                                user.getUsername()
                        );
                    }
                }

                processSubjectScores(row, fileData, student, user, subjectMap, existingScores, semester, scoreChanges, rowNumber, importClassName, importDetails,
                        dryRun, rowNewStudent, rowNewUser);
            } catch (Exception e) {
                errors.add("第" + rowNumber + "行导入失败: " + e.getMessage());
                importDetails.add(buildImportDetail(rowNumber, "", "", importClassName, null, null, "failed", e.getMessage()));
            }
        }

        return scoreChanges;
    }

    private void processSubjectScores(
            List<Object> row,
            FileParseHelper.ParsedFileData fileData,
            Student student,
            User user,
            Map<String, SubjectRAIN> subjectMap,
            Map<String, SubjectScoreRAIN> existingScores,
            String semester,
            List<ImportScoreChange> scoreChanges,
            int rowNumber,
            String className,
            List<Map<String, Object>> importDetails,
            boolean dryRun,
            boolean rowNewStudent,
            boolean rowNewUser) {

        String studentNo = student == null ? "" : student.getStudentNo();
        String studentName = student == null ? "" : student.getName();
        int rowCreatedCount = 0;
        int rowUpdatedCount = 0;
        int rowUnchangedCount = 0;
        List<String> createdSubjects = new ArrayList<>();
        List<String> updatedSubjects = new ArrayList<>();
        List<String> unchangedSubjects = new ArrayList<>();
        List<Map<String, Object>> scoreDeltas = new ArrayList<>();

        for (Map.Entry<String, SubjectRAIN> entry : subjectMap.entrySet()) {
            String subjectName = entry.getKey();
            SubjectRAIN subject = entry.getValue();
            int subjectIndex = fileData.getHeaderIndex(subjectName);
            if (subjectIndex < 0 || subjectIndex >= row.size()) {
                continue;
            }

            Double score = FileParseHelper.parseScore(row.get(subjectIndex));
            if (score == null) {
                continue;
            }

            String scoreKey = user != null && user.getId() != null
                    ? "U_" + user.getId() + "_" + subject.getId()
                    : "S_" + student.getId() + "_" + subject.getId();
            SubjectScoreRAIN subjectScore = existingScores.get(scoreKey);

            if (subjectScore != null) {
                Double previousScore = subjectScore.getScore();
                if (Objects.equals(previousScore, score)) {
                    scoreChanges.add(ImportScoreChange.unchanged(subjectName, subjectScore));
                    rowUnchangedCount++;
                    unchangedSubjects.add(subjectName);
                    if (dryRun) {
                        scoreDeltas.add(buildScoreDelta(subjectName, "unchanged", previousScore, score));
                    }
                    continue;
                }
                // 试算模式不修改受管实体，用副本承载新分数，避免只读事务外的脏数据刷库
                SubjectScoreRAIN target = dryRun ? copyScoreForPreview(subjectScore) : subjectScore;
                target.setScore(score);
                scoreChanges.add(ImportScoreChange.updated(subjectName, previousScore, target));
                rowUpdatedCount++;
                updatedSubjects.add(subjectName);
                if (dryRun) {
                    scoreDeltas.add(buildScoreDelta(subjectName, "updated", previousScore, score));
                }
                subjectScore = target;
            } else {
                subjectScore = new SubjectScoreRAIN();
                subjectScore.setStudentId(student.getId());
                subjectScore.setSubjectId(subject.getId());
                subjectScore.setScore(score);
                subjectScore.setSemester(semester);
                scoreChanges.add(ImportScoreChange.created(subjectName, subjectScore));
                rowCreatedCount++;
                createdSubjects.add(subjectName);
                if (dryRun) {
                    scoreDeltas.add(buildScoreDelta(subjectName, "created", null, score));
                }
            }

            subjectScore.setStudentId(student.getId());
            subjectScore.setUserId(user != null ? user.getId() : null);
            existingScores.put(scoreKey, subjectScore);
        }

        Map<String, Object> detail = buildImportDetail(
                rowNumber,
                studentNo,
                studentName,
                className,
                rowCreatedCount + rowUpdatedCount + rowUnchangedCount,
                buildSubjectSummary(createdSubjects, updatedSubjects, unchangedSubjects),
                rowCreatedCount + rowUpdatedCount > 0 ? "success" : "unchanged",
                rowCreatedCount + rowUpdatedCount > 0
                        ? String.format("新增 %d 门，更新 %d 门，未变更 %d 门", rowCreatedCount, rowUpdatedCount, rowUnchangedCount)
                        : String.format("未变更 %d 门", rowUnchangedCount)
        );
        if (dryRun) {
            detail.put("newStudent", rowNewStudent);
            detail.put("newUser", rowNewUser);
            detail.put("scoreDeltas", scoreDeltas);
        }
        importDetails.add(detail);
    }

    @Transactional
    public SubjectRAINImportResponseDTO undoImportBatch(String batchId, String role, Long userId) {
        ScoreImportBatch batch = scoreImportBatchRepository.findById(batchId)
                .orElseThrow(() -> new SubjectRAINException("导入批次不存在"));
        requireBatchAccess(batch, role, userId);
        if (Boolean.TRUE.equals(batch.getUndone())) {
            throw new SubjectRAINException("该导入批次已经撤销过");
        }

        List<ScoreImportItem> scoreItems = scoreImportItemRepository.findByBatchIdOrderByIdAsc(batchId);
        int deletedCreatedScoreCount = 0;
        int restoredUpdatedScoreCount = 0;

        ListIterator<ScoreImportItem> scoreIterator = scoreItems.listIterator(scoreItems.size());
        while (scoreIterator.hasPrevious()) {
            ScoreImportItem item = scoreIterator.previous();
            if (item.getActionType() == ScoreImportItem.ActionType.CREATED) {
                subjectScoreRAINServices.deleteById(item.getScoreId());
                deletedCreatedScoreCount++;
            } else if (item.getActionType() == ScoreImportItem.ActionType.UPDATED) {
                SubjectScoreRAIN score = subjectScoreRAINServices.findPlainById(item.getScoreId()).orElse(null);
                if (score != null) {
                    score.setScore(item.getPreviousScore());
                    subjectScoreRAINServices.saveSingle(score);
                    restoredUpdatedScoreCount++;
                }
            }
        }

        List<ScoreImportResourceItem> resourceItems = scoreImportResourceItemRepository.findByBatchIdOrderByIdAsc(batchId);
        int deletedCreatedStudentCount = 0;
        int deletedCreatedUserCount = 0;
        int deletedCreatedSubjectCount = 0;

        for (ScoreImportResourceItem item : resourceItems) {
            if (item.getResourceType() == ScoreImportResourceItem.ResourceType.STUDENT
                    && subjectScoreRAINServices.getScoresByStudentId(item.getResourceId()).isEmpty()
                    && studentRepository.findById(item.getResourceId()).isPresent()) {
                studentRepository.deleteById(item.getResourceId());
                deletedCreatedStudentCount++;
            }
        }

        for (ScoreImportResourceItem item : resourceItems) {
            if (item.getResourceType() == ScoreImportResourceItem.ResourceType.USER
                    && subjectScoreRAINServices.getScoresByUserId(item.getResourceId()).isEmpty()
                    && studentRepository.findByUserId(item.getResourceId()).isEmpty()
                    && userRepository.findById(item.getResourceId()).isPresent()) {
                userRoleRepository.deleteByUserId(item.getResourceId());
                userRepository.deleteById(item.getResourceId());
                deletedCreatedUserCount++;
            }
        }

        for (ScoreImportResourceItem item : resourceItems) {
            if (item.getResourceType() == ScoreImportResourceItem.ResourceType.SUBJECT
                    && subjectScoreRAINServices.getScoresBySubjectId(item.getResourceId()).isEmpty()
                    && subjectRAINRepository.findById(item.getResourceId()).isPresent()) {
                subjectRAINRepository.deleteById(item.getResourceId());
                deletedCreatedSubjectCount++;
            }
        }

        batch.setUndone(true);
        scoreImportBatchRepository.save(batch);

        SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
        Map<String, Object> undoImportInfo = new HashMap<>();
        undoImportInfo.put("batchId", batchId);
        undoImportInfo.put("deletedCreatedScoreCount", deletedCreatedScoreCount);
        undoImportInfo.put("restoredUpdatedScoreCount", restoredUpdatedScoreCount);
        undoImportInfo.put("deletedCreatedStudentCount", deletedCreatedStudentCount);
        undoImportInfo.put("deletedCreatedUserCount", deletedCreatedUserCount);
        undoImportInfo.put("deletedCreatedSubjectCount", deletedCreatedSubjectCount);
        response.setUndoImportInfo(undoImportInfo);
        return response;
    }

    @Transactional
    public SubjectRAINImportResponseDTO undoImportBatch(String batchId) {
        return undoImportBatch(batchId, "ADMIN", null);
    }

    @Transactional
    public SubjectRAINImportResponseDTO deleteImportStudentRecord(String batchId, Long studentId, String role, Long userId) {
        if (batchId == null || batchId.isBlank() || studentId == null) {
            throw new SubjectRAINException("学生导入记录参数不完整");
        }
        ScoreImportBatch batch = scoreImportBatchRepository.findById(batchId)
                .orElseThrow(() -> new SubjectRAINException("导入批次不存在"));
        requireBatchAccess(batch, role, userId);
        if (Boolean.TRUE.equals(batch.getUndone())) {
            throw new SubjectRAINException("该导入批次已经撤销过");
        }

        List<ScoreImportItem> scoreItems = scoreImportItemRepository.findByBatchIdAndStudentIdOrderByIdAsc(batchId, studentId);
        if (scoreItems.isEmpty()) {
            throw new SubjectRAINException("该学生没有可删除的导入记录");
        }

        int deletedCreatedScoreCount = 0;
        int restoredUpdatedScoreCount = 0;
        ListIterator<ScoreImportItem> scoreIterator = scoreItems.listIterator(scoreItems.size());
        while (scoreIterator.hasPrevious()) {
            ScoreImportItem item = scoreIterator.previous();
            if (item.getActionType() == ScoreImportItem.ActionType.CREATED) {
                subjectScoreRAINServices.deleteById(item.getScoreId());
                deletedCreatedScoreCount++;
            } else if (item.getActionType() == ScoreImportItem.ActionType.UPDATED) {
                SubjectScoreRAIN score = subjectScoreRAINServices.findPlainById(item.getScoreId()).orElse(null);
                if (score != null) {
                    score.setScore(item.getPreviousScore());
                    subjectScoreRAINServices.saveSingle(score);
                    restoredUpdatedScoreCount++;
                }
            }
        }

        List<ScoreImportResourceItem> resourceItems = scoreImportResourceItemRepository.findByBatchIdOrderByIdAsc(batchId);
        int deletedCreatedStudentCount = deleteCreatedStudentIfUnused(resourceItems, studentId);
        int deletedCreatedUserCount = deleteUnusedCreatedUsers(resourceItems);
        int deletedCreatedSubjectCount = deleteUnusedCreatedSubjects(resourceItems);

        scoreImportItemRepository.deleteAll(scoreItems);
        batch.setCreatedScoreCount(Math.max(0, safeCount(batch.getCreatedScoreCount()) - deletedCreatedScoreCount));
        batch.setUpdatedScoreCount(Math.max(0, safeCount(batch.getUpdatedScoreCount()) - restoredUpdatedScoreCount));
        scoreImportBatchRepository.save(batch);

        SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
        Map<String, Object> undoImportInfo = new HashMap<>();
        undoImportInfo.put("batchId", batchId);
        undoImportInfo.put("studentId", studentId);
        undoImportInfo.put("deletedCreatedScoreCount", deletedCreatedScoreCount);
        undoImportInfo.put("restoredUpdatedScoreCount", restoredUpdatedScoreCount);
        undoImportInfo.put("deletedCreatedStudentCount", deletedCreatedStudentCount);
        undoImportInfo.put("deletedCreatedUserCount", deletedCreatedUserCount);
        undoImportInfo.put("deletedCreatedSubjectCount", deletedCreatedSubjectCount);
        response.setUndoImportInfo(undoImportInfo);
        return response;
    }

    @Transactional
    public SubjectRAINImportResponseDTO deleteImportStudentRecord(String batchId, Long studentId) {
        return deleteImportStudentRecord(batchId, studentId, "ADMIN", null);
    }

    private Map<String, Object> toImportStudentRecordRow(
            ScoreImportBatch batch,
            Long studentId,
            Student student,
            List<ScoreImportItem> items
    ) {
        int createdCount = (int) items.stream().filter(item -> item.getActionType() == ScoreImportItem.ActionType.CREATED).count();
        int updatedCount = (int) items.stream().filter(item -> item.getActionType() == ScoreImportItem.ActionType.UPDATED).count();
        String subjectSummary = items.stream()
                .map(ScoreImportItem::getSubjectName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .collect(Collectors.joining("，"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", batch.getId() + ":" + studentId);
        row.put("batchId", batch.getId());
        row.put("studentId", studentId);
        row.put("studentNo", student == null ? String.valueOf(studentId) : student.getStudentNo());
        row.put("studentName", student == null ? "-" : student.getName());
        row.put("semester", batch.getSemester());
        row.put("className", student != null && student.getClassName() != null ? student.getClassName() : batch.getClassName());
        row.put("createdScoreCount", createdCount);
        row.put("updatedScoreCount", updatedCount);
        row.put("unchangedScoreCount", 0);
        row.put("errorCount", 0);
        row.put("subjectSummary", subjectSummary);
        row.put("undone", Boolean.TRUE.equals(batch.getUndone()));
        row.put("deleteAvailable", !Boolean.TRUE.equals(batch.getUndone()) && !items.isEmpty());
        row.put("createdAt", batch.getCreatedAt());
        return row;
    }

    private boolean matchesImportStudentRecordKeyword(Map<String, Object> row, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return List.of("studentNo", "studentName", "className", "subjectSummary", "semester").stream()
                .map(key -> String.valueOf(row.getOrDefault(key, "")))
                .map(value -> value.toLowerCase())
                .anyMatch(value -> value.contains(keyword));
    }

    private LocalDateTime resolveBatchCreatedAt(ScoreImportBatch batch, List<ScoreImportItem> scoreItems) {
        if (batch.getCreatedAt() != null) {
            return batch.getCreatedAt();
        }
        return scoreItems.stream()
                .map(ScoreImportItem::getCreatedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private boolean canAccessBatch(ScoreImportBatch batch, String role, Long userId) {
        if (batch == null) {
            return false;
        }
        if (isAdmin(role)) {
            return true;
        }
        return userId != null && Objects.equals(batch.getCreatedByUserId(), userId);
    }

    private void requireBatchAccess(ScoreImportBatch batch, String role, Long userId) {
        if (!canAccessBatch(batch, role, userId)) {
            throw new SubjectRAINException("无权访问该导入记录");
        }
    }

    private boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(normalizeRole(role));
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null) {
            return "";
        }
        return userRepository.findById(userId)
                .map(user -> {
                    String nickname = user.getNickname();
                    if (nickname != null && !nickname.isBlank()) {
                        return nickname;
                    }
                    String username = user.getUsername();
                    return username == null ? "" : username;
                })
                .orElse("");
    }

    private int deleteCreatedStudentIfUnused(List<ScoreImportResourceItem> resourceItems, Long studentId) {
        int deletedCreatedStudentCount = 0;
        for (ScoreImportResourceItem item : resourceItems) {
            if (item.getResourceType() == ScoreImportResourceItem.ResourceType.STUDENT
                    && Objects.equals(item.getResourceId(), studentId)
                    && subjectScoreRAINServices.getScoresByStudentId(item.getResourceId()).isEmpty()
                    && studentRepository.findById(item.getResourceId()).isPresent()) {
                studentRepository.deleteById(item.getResourceId());
                deletedCreatedStudentCount++;
            }
        }
        return deletedCreatedStudentCount;
    }

    private int deleteUnusedCreatedUsers(List<ScoreImportResourceItem> resourceItems) {
        int deletedCreatedUserCount = 0;
        for (ScoreImportResourceItem item : resourceItems) {
            if (item.getResourceType() == ScoreImportResourceItem.ResourceType.USER
                    && subjectScoreRAINServices.getScoresByUserId(item.getResourceId()).isEmpty()
                    && studentRepository.findByUserId(item.getResourceId()).isEmpty()
                    && userRepository.findById(item.getResourceId()).isPresent()) {
                userRoleRepository.deleteByUserId(item.getResourceId());
                userRepository.deleteById(item.getResourceId());
                deletedCreatedUserCount++;
            }
        }
        return deletedCreatedUserCount;
    }

    private int deleteUnusedCreatedSubjects(List<ScoreImportResourceItem> resourceItems) {
        int deletedCreatedSubjectCount = 0;
        for (ScoreImportResourceItem item : resourceItems) {
            if (item.getResourceType() == ScoreImportResourceItem.ResourceType.SUBJECT
                    && subjectScoreRAINServices.getScoresBySubjectId(item.getResourceId()).isEmpty()
                    && subjectRAINRepository.findById(item.getResourceId()).isPresent()) {
                subjectRAINRepository.deleteById(item.getResourceId());
                deletedCreatedSubjectCount++;
            }
        }
        return deletedCreatedSubjectCount;
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private void persistImportItems(String batchId, String semester, List<ImportScoreChange> scoreChanges) {
        List<ScoreImportItem> items = scoreChanges.stream()
                .filter(change -> change.getActionType() != null)
                .map(change -> {
                    ScoreImportItem item = new ScoreImportItem();
                    item.setBatchId(batchId);
                    item.setScoreId(change.getScore().getId());
                    item.setActionType(change.getActionType());
                    item.setPreviousScore(change.getPreviousScore());
                    item.setCurrentScore(change.getScore().getScore());
                    item.setStudentId(change.getScore().getStudentId());
                    item.setUserId(change.getScore().getUserId());
                    item.setSubjectId(change.getScore().getSubjectId());
                    item.setSemester(semester);
                    item.setSubjectName(change.getSubjectName());
                    return item;
                })
                .toList();
        if (!items.isEmpty()) {
            scoreImportItemRepository.saveAll(items);
        }
    }

    private void persistResourceItems(String batchId, List<ImportResourceChange> resourceChanges) {
        List<ScoreImportResourceItem> items = resourceChanges.stream()
                .map(change -> {
                    ScoreImportResourceItem item = new ScoreImportResourceItem();
                    item.setBatchId(batchId);
                    item.setResourceType(change.getResourceType());
                    item.setResourceId(change.getResourceId());
                    item.setResourceName(change.getResourceName());
                    return item;
                })
                .toList();
        if (!items.isEmpty()) {
            scoreImportResourceItemRepository.saveAll(items);
        }
    }

    private void recordCreatedResource(
            List<ImportResourceChange> resourceChanges,
            Set<String> resourceKeys,
            ScoreImportResourceItem.ResourceType resourceType,
            Long resourceId,
            String resourceName) {
        String key = resourceType.name() + "_" + resourceId;
        if (resourceKeys.add(key)) {
            resourceChanges.add(new ImportResourceChange(resourceType, resourceId, resourceName));
        }
    }

    private int calculateSuccessRowCount(FileParseHelper.ParsedFileData fileData, List<String> errors) {
        return Math.max(fileData.getRows().size() - errors.size(), 0);
    }

    /** 试算模式：查学生，不存在时在内存用负数 id 模拟新建（不落库）。 */
    private Student resolveStudentForPreview(String studentNo, String name, String className, Long teacherId,
            Map<String, Student> studentCache, AtomicLong fakeIdSequence) {
        Student cached = studentCache.get(studentNo);
        if (cached != null) {
            return cached;
        }
        Student existing = studentRepository.findByStudentNo(studentNo).orElse(null);
        if (existing != null) {
            studentCache.put(studentNo, existing);
            return existing;
        }
        Student simulated = new Student();
        simulated.setId(fakeIdSequence.decrementAndGet());
        simulated.setStudentNo(studentNo);
        simulated.setName(name);
        simulated.setClassName(className);
        simulated.setTeacherId(teacherId);
        studentCache.put(studentNo, simulated);
        return simulated;
    }

    /** 试算模式：查绑定账号，不存在时在内存用负数 id 模拟新建（不落库）。 */
    private User resolveUserForPreview(Student student, String username, Map<String, User> fakeUserCache, AtomicLong fakeIdSequence) {
        if (student.getUserId() != null) {
            User byStudent = userRepository.findById(student.getUserId()).orElse(null);
            if (byStudent != null) {
                return byStudent;
            }
        }
        User cached = fakeUserCache.get(username);
        if (cached != null) {
            return cached;
        }
        User byUsername = userRepository.findByUsername(username).orElse(null);
        if (byUsername != null) {
            return byUsername;
        }
        User simulated = new User();
        simulated.setId(fakeIdSequence.decrementAndGet());
        simulated.setUsername(username);
        simulated.setStudentNo(student.getStudentNo());
        fakeUserCache.put(username, simulated);
        return simulated;
    }

    private Map<String, Object> buildScoreDelta(String subjectName, String action, Double previousScore, Double newScore) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("subjectName", subjectName);
        delta.put("action", action);
        delta.put("previousScore", previousScore);
        delta.put("newScore", newScore);
        return delta;
    }

    private SubjectScoreRAIN copyScoreForPreview(SubjectScoreRAIN source) {
        SubjectScoreRAIN copy = new SubjectScoreRAIN();
        copy.setId(source.getId());
        copy.setStudentId(source.getStudentId());
        copy.setUserId(source.getUserId());
        copy.setSubjectId(source.getSubjectId());
        copy.setSemester(source.getSemester());
        copy.setScore(source.getScore());
        return copy;
    }

    private long countFlaggedDetails(List<Map<String, Object>> importDetails, String flag) {
        return importDetails.stream().filter(detail -> Boolean.TRUE.equals(detail.get(flag))).count();
    }

    private Map<String, Object> buildImportDetail(
            int rowNumber,
            String studentNo,
            String studentName,
            String className,
            Integer affectedSubjectCount,
            String subjectSummary,
            String status,
            String message) {
        Map<String, Object> item = new HashMap<>();
        item.put("rowNumber", rowNumber);
        item.put("studentNo", studentNo == null ? "" : studentNo);
        item.put("studentName", studentName == null ? "" : studentName);
        item.put("className", className == null ? "" : className);
        item.put("affectedSubjectCount", affectedSubjectCount);
        item.put("subjectSummary", subjectSummary == null ? "" : subjectSummary);
        item.put("status", status);
        item.put("message", message == null ? "" : message);
        return item;
    }

    private String buildSubjectSummary(List<String> createdSubjects, List<String> updatedSubjects, List<String> unchangedSubjects) {
        List<String> parts = new ArrayList<>();
        if (!createdSubjects.isEmpty()) {
            parts.add("新增：" + String.join("，", createdSubjects));
        }
        if (!updatedSubjects.isEmpty()) {
            parts.add("更新：" + String.join("，", updatedSubjects));
        }
        if (!unchangedSubjects.isEmpty()) {
            parts.add("未变更：" + String.join("，", unchangedSubjects));
        }
        return String.join("；", parts);
    }

    private static class ImportScoreChange {
        private final String subjectName;
        private final Double previousScore;
        private final ScoreImportItem.ActionType actionType;
        private final SubjectScoreRAIN score;

        private ImportScoreChange(String subjectName, Double previousScore, ScoreImportItem.ActionType actionType, SubjectScoreRAIN score) {
            this.subjectName = subjectName;
            this.previousScore = previousScore;
            this.actionType = actionType;
            this.score = score;
        }

        public static ImportScoreChange created(String subjectName, SubjectScoreRAIN score) {
            return new ImportScoreChange(subjectName, null, ScoreImportItem.ActionType.CREATED, score);
        }

        public static ImportScoreChange updated(String subjectName, Double previousScore, SubjectScoreRAIN score) {
            return new ImportScoreChange(subjectName, previousScore, ScoreImportItem.ActionType.UPDATED, score);
        }

        public static ImportScoreChange unchanged(String subjectName, SubjectScoreRAIN score) {
            return new ImportScoreChange(subjectName, score.getScore(), null, score);
        }

        public String getSubjectName() {
            return subjectName;
        }

        public Double getPreviousScore() {
            return previousScore;
        }

        public ScoreImportItem.ActionType getActionType() {
            return actionType;
        }

        public SubjectScoreRAIN getScore() {
            return score;
        }
    }

    private static class ImportResourceChange {
        private final ScoreImportResourceItem.ResourceType resourceType;
        private final Long resourceId;
        private final String resourceName;

        private ImportResourceChange(ScoreImportResourceItem.ResourceType resourceType, Long resourceId, String resourceName) {
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.resourceName = resourceName;
        }

        public ScoreImportResourceItem.ResourceType getResourceType() {
            return resourceType;
        }

        public Long getResourceId() {
            return resourceId;
        }

        public String getResourceName() {
            return resourceName;
        }
    }
}
