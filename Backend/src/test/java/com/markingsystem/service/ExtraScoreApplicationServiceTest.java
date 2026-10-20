package com.markingsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreSubmissionWindow;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.User;
import com.markingsystem.dto.ExtraScoreApplicationBatchReviewRequest;
import com.markingsystem.dto.ExtraScoreApplicationReviewRequest;
import com.markingsystem.repository.AnnouncementRecipientRepository;
import com.markingsystem.repository.AnnouncementRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.ExtraScoreApplicationEventRepository;
import com.markingsystem.repository.ExtraScoreTemplateGroupRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemAuthorRuleRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemLevelRuleRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemRepository;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.SystemMessageRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.RequestUserContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraScoreApplicationServiceTest {

    @Test
    void searchesAssignedTemplateItemsByGroupOrItemName() {
        ExtraScoreApplicationService service = service(student(), window(), List.of(), new ArrayList<>());

        Map<String, Object> itemResult = service.searchMyTemplateItems(studentContext(), "MORAL", "志愿", 30);
        List<Map<String, Object>> itemGroups = mapList(itemResult.get("groups"));
        assertEquals(1, itemGroups.size());
        assertEquals("评选先进加分", itemGroups.get(0).get("groupName"));
        assertEquals("优秀志愿者、先进个人", mapList(itemGroups.get(0).get("items")).get(0).get("itemName"));

        Map<String, Object> groupResult = service.searchMyTemplateItems(studentContext(), "MORAL", "学生干部", 30);
        List<Map<String, Object>> groupItems = mapList(mapList(groupResult.get("groups")).get(0).get("items"));
        assertEquals(1, groupItems.size());
        assertEquals("校级组织副部长及以上加分", groupItems.get(0).get("itemName"));
        assertEquals(false, groupResult.get("hasMore"));
    }

    @Test
    void withdrawsImmediatelyWhenCommitteeHasNotReviewedAnyRecord() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        List<ExtraScoreApplication> applications = List.of(
                pendingApplication(1L, "pending", null),
                pendingApplication(2L, "pending", null)
        );
        List<ExtraScoreApplication> saved = new ArrayList<>();
        ExtraScoreApplicationService service = service(student, window, applications, saved);

        Map<String, Object> result = service.withdrawCurrentWindowApplications(studentContext());

        assertEquals(false, result.get("approvalRequired"));
        assertEquals("撤回成功，已退回草稿", result.get("message"));
        assertEquals(2, saved.size());
        for (ExtraScoreApplication application : saved) {
            assertEquals("draft", application.getStatus());
            assertEquals("draft", application.getReviewStage());
            assertEquals("draft", application.getCommitteeStatus());
            assertNull(application.getWithdrawalStatus());
        }
    }

    @Test
    void requiresCommitteeApprovalAfterAnyRecordHasCommitteeReviewResult() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        List<ExtraScoreApplication> applications = List.of(
                pendingApplication(1L, "approved", LocalDateTime.now()),
                pendingApplication(2L, "pending", null)
        );
        List<ExtraScoreApplication> saved = new ArrayList<>();
        ExtraScoreApplicationService service = service(student, window, applications, saved);

        Map<String, Object> result = service.withdrawCurrentWindowApplications(studentContext());

        assertEquals(true, result.get("approvalRequired"));
        assertEquals("撤回申请已提交，等待班委同意", result.get("message"));
        assertEquals(2, saved.size());
        for (ExtraScoreApplication application : saved) {
            assertEquals("pending", application.getStatus());
            assertEquals("pending", application.getWithdrawalStatus());
        }
    }

    @Test
    void teacherCanApproveWithdrawalWhenClassHasNoCommitteeMembers() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication application = pendingApplication(1L, "approved", LocalDateTime.now());
        application.setWithdrawalStatus("pending");
        List<ExtraScoreApplication> applications = List.of(application);
        List<ExtraScoreApplication> saved = new ArrayList<>();
        ExtraScoreApplicationService service = service(student, window, applications, saved);
        ExtraScoreApplicationReviewRequest request = new ExtraScoreApplicationReviewRequest();
        request.setAction("approve");

        service.reviewWithdrawalRequest(teacherContext(), 1L, request);

        assertEquals(1, saved.size());
        assertEquals("draft", saved.get(0).getStatus());
        assertEquals("approved", saved.get(0).getWithdrawalStatus());
        assertEquals(3L, saved.get(0).getWithdrawalReviewerId());
    }

    @Test
    void teacherCanReturnCommitteePendingApplicationWhenClassHasNoCommitteeMembers() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication application = pendingApplication(1L, "pending", null);
        List<ExtraScoreApplication> applications = List.of(application);
        List<ExtraScoreApplication> saved = new ArrayList<>();
        ExtraScoreApplicationService service = service(student, window, applications, saved);
        ExtraScoreApplicationReviewRequest request = new ExtraScoreApplicationReviewRequest();
        request.setAction("return");
        request.setComment("材料不完整");

        service.teacherReview(teacherContext(), 1L, request);

        assertEquals(1, saved.size());
        assertEquals("rejected", saved.get(0).getStatus());
        assertEquals("returned", saved.get(0).getReviewStage());
        assertEquals("returned", saved.get(0).getTeacherStatus());
        assertEquals("材料不完整", saved.get(0).getReturnReason());
    }

    @Test
    void batchReturnReviewRejectsWithUnifiedReasonAndRejectType() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication first = pendingApplication(1L, "pending", null);
        ExtraScoreApplication second = pendingApplication(2L, "pending", null);
        ExtraScoreApplicationService service = service(student, window, List.of(first, second), new ArrayList<>());

        ExtraScoreApplicationBatchReviewRequest request = new ExtraScoreApplicationBatchReviewRequest();
        request.setApplicationIds(List.of(1L, 2L));
        request.setAction("return");
        request.setComment("材料不符合加分细则");

        Map<String, Object> result = service.batchReturnReview(teacherContext(), request);

        assertEquals(2, result.get("successCount"));
        assertEquals(0, result.get("failedCount"));
        assertEquals("rejected", first.getStatus());
        assertEquals("returned", first.getReviewStage());
        assertEquals("returned", first.getTeacherStatus());
        assertEquals("reject", first.getReturnType());
        assertEquals("材料不符合加分细则", first.getReturnReason());
        assertEquals("rejected", second.getStatus());
        assertEquals("reject", second.getReturnType());
    }

    @Test
    void batchReturnReviewSupplementKeepsReturnedStatusAndSupplementType() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication application = pendingApplication(1L, "pending", null);
        ExtraScoreApplicationService service = service(student, window, List.of(application), new ArrayList<>());

        ExtraScoreApplicationBatchReviewRequest request = new ExtraScoreApplicationBatchReviewRequest();
        request.setApplicationIds(List.of(1L));
        request.setAction("supplement");
        request.setComment("请补充盖章页后重新提交");

        Map<String, Object> result = service.batchReturnReview(teacherContext(), request);

        assertEquals(1, result.get("successCount"));
        assertEquals("returned", application.getStatus());
        assertEquals("returned", application.getReviewStage());
        assertEquals("returned", application.getTeacherStatus());
        assertEquals("supplement", application.getReturnType());
        assertEquals("请补充盖章页后重新提交", application.getReturnReason());
    }

    @Test
    void batchReturnReviewSkipsIneligibleRecordsAndReportsFailures() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication pending = pendingApplication(1L, "pending", null);
        ExtraScoreApplication approved = approvedOpenPeriodApplication(2L);
        ExtraScoreApplicationService service = service(student, window, List.of(pending, approved), new ArrayList<>());

        ExtraScoreApplicationBatchReviewRequest request = new ExtraScoreApplicationBatchReviewRequest();
        request.setApplicationIds(List.of(1L, 2L));
        request.setAction("return");
        request.setComment("统一驳回");

        Map<String, Object> result = service.batchReturnReview(teacherContext(), request);

        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failedCount"));
        assertEquals("rejected", pending.getStatus());
        // 已终审通过的记录不能被直接驳回（teacherReview 拒绝），但会按既有规则整包退回，自身结论仍为通过
        assertEquals("returned", approved.getStatus());
        assertEquals("approved", approved.getTeacherStatus());
        List<Map<String, Object>> failed = mapList(result.get("failed"));
        assertEquals(2L, failed.get(0).get("id"));
        assertTrue(String.valueOf(failed.get(0).get("message")).contains("已完成审核"));
    }

    @Test
    void batchReturnReviewValidatesReasonRoleAndAction() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication application = pendingApplication(1L, "pending", null);
        ExtraScoreApplicationService service = service(student, window, List.of(application), new ArrayList<>());

        ExtraScoreApplicationBatchReviewRequest blankReason = new ExtraScoreApplicationBatchReviewRequest();
        blankReason.setApplicationIds(List.of(1L));
        blankReason.setAction("return");
        blankReason.setComment("   ");
        assertThrows(IllegalArgumentException.class, () -> service.batchReturnReview(teacherContext(), blankReason));

        ExtraScoreApplicationBatchReviewRequest approveAction = new ExtraScoreApplicationBatchReviewRequest();
        approveAction.setApplicationIds(List.of(1L));
        approveAction.setAction("approve");
        approveAction.setComment("走错接口");
        assertThrows(IllegalArgumentException.class, () -> service.batchReturnReview(teacherContext(), approveAction));

        ExtraScoreApplicationBatchReviewRequest emptyIds = new ExtraScoreApplicationBatchReviewRequest();
        emptyIds.setApplicationIds(List.of());
        emptyIds.setAction("supplement");
        emptyIds.setComment("请补材料");
        assertThrows(IllegalArgumentException.class, () -> service.batchReturnReview(teacherContext(), emptyIds));

        assertThrows(IllegalArgumentException.class, () -> service.batchReturnReview(studentContext(), blankReason));
    }

    @Test
    void reviewWorkbenchCountsDirectTeacherReviewAsActionable() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication application = pendingApplication(1L, "pending", null);
        application.setAcademicYear(window.getAcademicYear());
        application.setUpdatedAt(LocalDateTime.now());
        ExtraScoreApplicationService service = service(
                student,
                window,
                List.of(application),
                new ArrayList<>()
        );

        Map<String, Object> result = service.getReviewWorkbench(teacherContext(), "MORAL", true);
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        List<Map<String, Object>> classes = (List<Map<String, Object>>) result.get("classes");

        assertEquals(1, summary.get("actionablePending"));
        assertEquals(0, summary.get("waitingCommittee"));
        assertEquals(1, classes.size());
        assertEquals(1, classes.get(0).get("actionablePending"));
        assertEquals(0, classes.get(0).get("waitingCommittee"));
    }

    @Test
    void studentReviewGroupUsesTeacherActionableStageWhenClassHasNoCommittee() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication application = pendingApplication(1L, "pending", null);
        application.setAcademicYear(window.getAcademicYear());
        application.setCreatedAt(LocalDateTime.now());
        ExtraScoreApplicationService service = service(
                student,
                window,
                List.of(application),
                new ArrayList<>()
        );

        List<Map<String, Object>> groups = service.listReviewStudentGroups(
                teacherContext(),
                "MORAL",
                "all",
                "计科231",
                null,
                null,
                null,
                true
        );

        assertEquals(1, groups.size());
        assertEquals(1L, groups.get(0).get("actionablePending"));
        assertEquals(0L, groups.get(0).get("waitingCommittee"));
        assertEquals("teacher_actionable", groups.get(0).get("stageKey"));
        assertEquals("需要处理", groups.get(0).get("stageLabel"));
        assertEquals("开始审核", groups.get(0).get("actionLabel"));
    }

    @Test
    void submitCurrentWindowKeepsPackageApprovedRecordsOutOfResubmission() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication approvedInReturnedPackage = pendingApplication(1L, "approved", LocalDateTime.now());
        approvedInReturnedPackage.setStatus("returned");
        approvedInReturnedPackage.setReviewStage("returned");
        approvedInReturnedPackage.setTeacherStatus("approved");
        approvedInReturnedPackage.setReturnReason("本周期存在被驳回记录，整包退回；该条审核结论为通过");

        ExtraScoreApplication rejectedRecord = pendingApplication(2L, "returned", LocalDateTime.now());
        rejectedRecord.setStatus("rejected");
        rejectedRecord.setReviewStage("returned");
        rejectedRecord.setTeacherStatus("pending");
        rejectedRecord.setReturnReason("材料不完整");

        List<ExtraScoreApplication> applications = List.of(approvedInReturnedPackage, rejectedRecord);
        List<ExtraScoreApplication> saved = new ArrayList<>();
        ExtraScoreApplicationService service = service(student, window, applications, saved);

        Map<String, Object> result = service.submitCurrentWindowApplications(studentContext());

        assertEquals(1, result.get("submittedCount"));
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(item -> item.getId().equals(2L) && "pending".equals(item.getStatus())));
        assertTrue(saved.stream().anyMatch(item -> item.getId().equals(1L) && "approved".equals(item.getStatus())));
        assertEquals("approved", approvedInReturnedPackage.getStatus());
        assertEquals("approved", approvedInReturnedPackage.getReviewStage());
        assertEquals("approved", approvedInReturnedPackage.getTeacherStatus());
        assertNull(approvedInReturnedPackage.getReturnReason());
    }

    @Test
    void currentProcessKeepsApprovedOpenPeriodVisible() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication first = approvedOpenPeriodApplication(1L);
        ExtraScoreApplication second = approvedOpenPeriodApplication(2L);
        List<ExtraScoreApplication> applications = List.of(first, second);
        ExtraScoreApplicationService service = service(student, window, applications, new ArrayList<>());

        Map<String, Object> result = service.getMyCurrentApplicationProcess(studentContext());
        Map<String, Object> currentProcess = (Map<String, Object>) result.get("currentProcess");

        assertEquals("补充周期 4", currentProcess.get("submissionPeriodLabel"));
        assertEquals("approved", currentProcess.get("status"));
        assertEquals(2, currentProcess.get("total"));
    }

    @Test
    void currentProcessHidesApprovedPeriodAfterCompositeSnapshotIsUpdated() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        window.setEnabled(false);
        ExtraScoreApplication approved = approvedOpenPeriodApplication(1L);
        ExtraScoreApplicationService service = service(student, window, List.of(approved), new ArrayList<>());

        Map<String, Object> result = service.getMyCurrentApplicationProcess(studentContext());

        assertNull(result.get("currentProcess"));
    }

    @Test
    void currentProcessIgnoresTeacherImportWithoutSubmissionWindow() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication teacherImport = teacherImportApplication(1L);
        ExtraScoreApplicationService service = service(student, window, List.of(teacherImport), new ArrayList<>());

        Map<String, Object> result = service.getMyCurrentApplicationProcess(studentContext());

        assertNull(result.get("currentProcess"));
    }

    @Test
    void applicationListContextIncludesFullPeriodSummaryForPagedRecords() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        window.setBatchCode("current-batch");
        window.setStartTime(LocalDateTime.now().minusDays(1));
        window.setEndTime(LocalDateTime.now().plusDays(10));

        List<ExtraScoreApplication> applications = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            ExtraScoreApplication application = approvedOpenPeriodApplication(id);
            application.setSubmissionWindowId(window.getId());
            application.setSubmissionBatchCode(window.getBatchCode());
            application.setSubmissionStartTime(window.getStartTime());
            application.setSubmissionEndTime(window.getEndTime());
            application.setCreatedAt(LocalDateTime.now().minusMinutes(id));
            applications.add(application);
        }
        ExtraScoreApplication history = approvedOpenPeriodApplication(99L);
        history.setSubmissionWindowId(99L);
        history.setSubmissionBatchCode("history-batch");
        history.setSubmissionPeriodLabel("年度主周期");
        history.setSubmissionStartTime(LocalDateTime.now().minusDays(30));
        history.setSubmissionEndTime(LocalDateTime.now().minusDays(20));
        history.setCreatedAt(LocalDateTime.now().minusDays(20));
        applications.add(history);

        ExtraScoreApplicationService service = service(student, window, applications, new ArrayList<>());

        Map<String, Object> result = service.getMyApplicationListContext(studentContext(), "MORAL", "all", "", "upload-desc", 0, 10);

        assertEquals(13, result.get("total"));
        assertEquals(10, ((List<?>) result.get("records")).size());

        Map<String, Object> periodSummary = (Map<String, Object>) result.get("periodSummary");
        Map<String, Object> buckets = (Map<String, Object>) periodSummary.get("buckets");
        Map<String, Object> currentBucket = (Map<String, Object>) buckets.get("current");
        assertEquals(12, currentBucket.get("total"));
        assertEquals(List.of("已通过 12 条"), currentBucket.get("statusSummary"));

        Map<String, Object> groups = (Map<String, Object>) periodSummary.get("groups");
        Map<String, Object> currentGroup = (Map<String, Object>) groups.get("current-batch");
        assertEquals(12, currentGroup.get("total"));
        assertEquals(List.of("已通过 12 条"), currentGroup.get("statusSummary"));
    }

    @Test
    void historyPrioritizesActionableRecordsAndSupportsKeywordFiltering() {
        Student student = student();
        ExtraScoreSubmissionWindow window = window();
        ExtraScoreApplication draft = pendingApplication(1L, "draft", null);
        draft.setAcademicYear(window.getAcademicYear());
        draft.setStatus("draft");
        draft.setReviewStage("draft");
        draft.setCommitteeStatus("draft");
        draft.setTeacherStatus("draft");
        draft.setItemName("志愿服务");
        draft.setCertName("待补材料");
        draft.setUpdatedAt(LocalDateTime.now().minusHours(2));

        ExtraScoreApplication approved = pendingApplication(2L, "approved", LocalDateTime.now());
        approved.setAcademicYear(window.getAcademicYear());
        approved.setStatus("approved");
        approved.setReviewStage("approved");
        approved.setTeacherStatus("approved");
        approved.setItemName("竞赛获奖");
        approved.setCertName("程序设计竞赛");
        approved.setUpdatedAt(LocalDateTime.now());

        ExtraScoreApplicationService service = service(
                student,
                window,
                List.of(approved, draft),
                new ArrayList<>()
        );

        Map<String, Object> result = service.listMyApplicationHistory(
                studentContext(),
                window.getAcademicYear(),
                "all",
                "all",
                "",
                0,
                20
        );
        List<Map<String, Object>> records = mapList(result.get("records"));
        Map<String, Object> counts = (Map<String, Object>) result.get("counts");

        assertEquals(2, records.size());
        assertEquals(1L, counts.get("actionRequired"));
        assertEquals(1L, records.get(0).get("id"));
        assertEquals(true, records.get(0).get("actionRequired"));
        assertEquals("继续填写", records.get(0).get("primaryActionLabel"));

        Map<String, Object> filtered = service.listMyApplicationHistory(
                studentContext(),
                window.getAcademicYear(),
                "all",
                "completed",
                "程序设计",
                0,
                20
        );
        List<Map<String, Object>> filteredRecords = mapList(filtered.get("records"));
        assertEquals(1, filteredRecords.size());
        assertEquals(2L, filteredRecords.get(0).get("id"));
    }

    private ExtraScoreApplicationService service(
            Student student,
            ExtraScoreSubmissionWindow window,
            List<ExtraScoreApplication> applications,
            List<ExtraScoreApplication> saved
    ) {
        ExtraScoreApplicationRepository applicationRepository = proxy(
                ExtraScoreApplicationRepository.class,
                Map.of(
                        "findByStudentNoAndSubmissionWindowId", (MethodHandler) args -> applications,
                        "findByStudentNoOrderByCreatedAtDesc", (MethodHandler) args -> applications,
                        "findByStudentNoAndScoreTypeCaseInsensitive", (MethodHandler) args -> applications.stream()
                                .filter(application -> String.valueOf(args[1]).equalsIgnoreCase(application.getScoreType()))
                                .toList(),
                        "findByTeacherIdAndFilters", (MethodHandler) args -> applications,
                        "findReviewAcademicYears", (MethodHandler) args -> List.of(window.getAcademicYear()),
                        "findById", (MethodHandler) args -> applications.stream()
                                .filter(application -> application.getId().equals(args[0]))
                                .findFirst(),
                        "saveAll", (MethodHandler) args -> {
                            List<ExtraScoreApplication> rows = (List<ExtraScoreApplication>) args[0];
                            saved.clear();
                            saved.addAll(rows);
                            return rows;
                        },
                        "save", (MethodHandler) args -> {
                            ExtraScoreApplication row = (ExtraScoreApplication) args[0];
                            saved.clear();
                            saved.add(row);
                            return row;
                        }
                )
        );
        StudentRepository studentRepository = proxy(
                StudentRepository.class,
                Map.of(
                        "findByStudentNo", (MethodHandler) args -> Optional.of(student),
                        "findByUserId", (MethodHandler) args -> Optional.of(student),
                        "findByTeacherId", (MethodHandler) args -> List.of(student),
                        "findByClassName", (MethodHandler) args -> List.of()
                )
        );
        User teacher = new User();
        teacher.setId(3L);
        teacher.setUsername("teacher");
        teacher.setNickname("张老师");
        return new ExtraScoreApplicationService(
                applicationRepository,
                proxy(ExtraScoreTemplateGroupRepository.class, Map.of()),
                proxy(ExtraScoreTemplateItemRepository.class, Map.of()),
                proxy(ExtraScoreTemplateItemLevelRuleRepository.class, Map.of()),
                proxy(ExtraScoreTemplateItemAuthorRuleRepository.class, Map.of()),
                studentRepository,
                proxy(UserRepository.class, Map.of("findById", (MethodHandler) args -> Optional.of(teacher))),
                proxy(RoleRepository.class, Map.of("findByName", (MethodHandler) args -> Optional.empty())),
                proxy(AnnouncementRepository.class, Map.of()),
                proxy(AnnouncementRecipientRepository.class, Map.of()),
                new TestTemplateService(),
                new TestSubmissionWindowService(window),
                null,
                new TestEffectiveScoreService(),
                new ExtraScoreApplicationEventService(
                        proxy(
                                ExtraScoreApplicationEventRepository.class,
                                Map.of(
                                        "save", (MethodHandler) args -> args[0],
                                        "existsByApplicationIdAndEventTypeIn", (MethodHandler) args -> false,
                                        "findByApplicationIdOrderByOccurredAtDescIdDesc", (MethodHandler) args -> List.of(),
                                        "deleteByApplicationId", (MethodHandler) args -> null
                                )
                        ),
                        new ObjectMapper()
                ),
                new SystemMessageService(
                        proxy(
                                SystemMessageRepository.class,
                                Map.of("save", (MethodHandler) args -> args[0])
                        )
                )
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Map<String, Object> responses) {
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private RequestUserContext studentContext() {
        return new RequestUserContext(9L, "STUDENT", 3L, "计科231", "20230001");
    }

    private RequestUserContext teacherContext() {
        return new RequestUserContext(3L, "TEACHER", 3L, "", "");
    }

    private Student student() {
        Student student = new Student();
        student.setId(1L);
        student.setUserId(9L);
        student.setStudentNo("20230001");
        student.setName("张同学");
        student.setClassName("计科231");
        student.setTeacherId(3L);
        return student;
    }

    private ExtraScoreSubmissionWindow window() {
        ExtraScoreSubmissionWindow window = new ExtraScoreSubmissionWindow();
        window.setId(7L);
        window.setAcademicYear("2025-2026");
        window.setBatchCode("batch-7");
        window.setClassName("计科231");
        window.setTeacherId(3L);
        window.setEnabled(true);
        return window;
    }

    private ExtraScoreApplication pendingApplication(Long id, String committeeStatus, LocalDateTime committeeReviewedAt) {
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setId(id);
        application.setStudentId(1L);
        application.setStudentNo("20230001");
        application.setStudentName("张同学");
        application.setClassName("计科231");
        application.setTeacherId(3L);
        application.setSubmissionWindowId(7L);
        application.setScoreType("MORAL");
        application.setTemplateId(4L);
        application.setTemplateName("德育模板");
        application.setCertName("测试证书" + id);
        application.setStatus("pending");
        application.setReviewStage("committee_pending");
        application.setCommitteeStatus(committeeStatus);
        application.setCommitteeReviewedAt(committeeReviewedAt);
        application.setTeacherStatus("pending");
        return application;
    }

    private ExtraScoreApplication approvedOpenPeriodApplication(Long id) {
        ExtraScoreApplication application = pendingApplication(id, "approved", LocalDateTime.now().minusDays(1));
        application.setStatus("approved");
        application.setReviewStage("approved");
        application.setTeacherStatus("approved");
        application.setTeacherReviewedAt(LocalDateTime.now().minusHours(1));
        application.setSubmissionBatchCode("batch-open");
        application.setSubmissionPeriodLabel("补充周期 4");
        application.setSubmissionStartTime(LocalDateTime.now().minusDays(1));
        application.setSubmissionEndTime(LocalDateTime.now().plusDays(10));
        return application;
    }

    private ExtraScoreApplication teacherImportApplication(Long id) {
        ExtraScoreApplication application = pendingApplication(id, "approved", LocalDateTime.now().minusDays(1));
        application.setStatus("approved");
        application.setReviewStage("approved");
        application.setTeacherStatus("approved");
        application.setTeacherReviewedAt(LocalDateTime.now().minusHours(1));
        application.setSubmissionWindowId(null);
        application.setSubmissionBatchCode("teacher-import-batch");
        application.setSubmissionPeriodLabel("教师导入");
        application.setSubmissionStartTime(null);
        application.setSubmissionEndTime(null);
        application.setSubmissionType("teacher_import");
        return application;
    }

    private static class TestSubmissionWindowService extends ExtraScoreSubmissionWindowService {
        private final ExtraScoreSubmissionWindow window;

        TestSubmissionWindowService(ExtraScoreSubmissionWindow window) {
            super(null, null, null, null, null, null, null);
            this.window = window;
        }

        @Override
        public ExtraScoreSubmissionWindow getActiveSubmissionWindow(Student student) {
            return window;
        }

        @Override
        public Map<String, Object> getCurrentWindow(Student student) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("academicYear", window.getAcademicYear());
            result.put("className", window.getClassName());
            result.put("batchCode", window.getBatchCode());
            result.put("enabled", window.getEnabled());
            result.put("startTime", window.getStartTime());
            result.put("endTime", window.getEndTime());
            result.put("open", Boolean.TRUE.equals(window.getEnabled()));
            return result;
        }

        @Override
        public List<Long> findCurrentReviewWindowIds(RequestUserContext context) {
            return List.of(window.getId());
        }

        @Override
        public List<ExtraScoreSubmissionWindow> findEnabledReviewWindows(RequestUserContext context) {
            return List.of(window);
        }

        @Override
        public String resolveCurrentAcademicYear() {
            return window.getAcademicYear();
        }

        @Override
        public Map<Long, Map<String, Object>> getStudentProcessWindowStates(java.util.Collection<Long> windowIds) {
            if (window.getId() == null || !windowIds.contains(window.getId())) {
                return Map.of();
            }
            return Map.of(
                    window.getId(),
                    Map.of(
                            "periodSettled", !Boolean.TRUE.equals(window.getEnabled()),
                            "scoreSnapshotUpdated", !Boolean.TRUE.equals(window.getEnabled())
                    )
            );
        }
    }

    private static class TestTemplateService extends ExtraScoreTemplateService {
        TestTemplateService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getAssignedTemplateFormContext(RequestUserContext context, String scoreType) {
            Map<String, Object> activeItem = new LinkedHashMap<>();
            activeItem.put("id", 11L);
            activeItem.put("itemName", "校级组织副部长及以上加分");
            activeItem.put("status", "active");
            activeItem.put("levelRules", List.of());

            Map<String, Object> inactiveItem = new LinkedHashMap<>();
            inactiveItem.put("id", 12L);
            inactiveItem.put("itemName", "已停用学生干部项目");
            inactiveItem.put("status", "inactive");
            inactiveItem.put("levelRules", List.of());

            Map<String, Object> volunteerItem = new LinkedHashMap<>();
            volunteerItem.put("id", 21L);
            volunteerItem.put("itemName", "优秀志愿者、先进个人");
            volunteerItem.put("status", "active");
            volunteerItem.put("levelRules", List.of());

            Map<String, Object> template = new LinkedHashMap<>();
            template.put("id", 4L);
            template.put("name", "德育模板");
            template.put("groups", List.of(
                    Map.of("id", 1L, "groupName", "学生干部加分", "items", List.of(activeItem, inactiveItem)),
                    Map.of("id", 2L, "groupName", "评选先进加分", "items", List.of(volunteerItem))
            ));
            return Map.of("teacherId", 3L, "scoreType", scoreType, "template", template);
        }
    }

    private static class TestEffectiveScoreService extends ExtraScoreEffectiveScoreService {
        TestEffectiveScoreService() {
            super(null, null);
        }

        @Override
        public Map<String, Double> summarizeEffectiveScoresByType(List<ExtraScoreApplication> applications) {
            return Map.of("MORAL", 0D, "INTELLECTUAL", 0D, "PHYSICAL", 0D);
        }
    }
}
