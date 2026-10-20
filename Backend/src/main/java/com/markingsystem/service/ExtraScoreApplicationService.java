package com.markingsystem.service;

import com.markingsystem.dto.ExtraScoreApplicationBatchReviewRequest;
import com.markingsystem.dto.ExtraScoreApplicationReviewRequest;
import com.markingsystem.dto.ExtraScoreApplicationRequest;
import com.markingsystem.entity.Announcement;
import com.markingsystem.entity.AnnouncementRecipient;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreTemplate;
import com.markingsystem.entity.ExtraScoreTemplateGroup;
import com.markingsystem.entity.ExtraScoreTemplateItem;
import com.markingsystem.entity.ExtraScoreTemplateItemAuthorRule;
import com.markingsystem.entity.ExtraScoreTemplateItemLevelRule;
import com.markingsystem.entity.ExtraScoreSubmissionWindow;
import com.markingsystem.entity.Role;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.User;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.ExtraScoreTemplateGroupRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemAuthorRuleRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemLevelRuleRepository;
import com.markingsystem.repository.ExtraScoreTemplateItemRepository;
import com.markingsystem.repository.AnnouncementRecipientRepository;
import com.markingsystem.repository.AnnouncementRepository;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.UserRepository;
import com.markingsystem.util.RequestUserContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExtraScoreApplicationService {

    private final ExtraScoreApplicationRepository applicationRepository;
    private final ExtraScoreTemplateGroupRepository groupRepository;
    private final ExtraScoreTemplateItemRepository itemRepository;
    private final ExtraScoreTemplateItemLevelRuleRepository levelRuleRepository;
    private final ExtraScoreTemplateItemAuthorRuleRepository authorRuleRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository announcementRecipientRepository;
    private final ExtraScoreTemplateService extraScoreTemplateService;
    private final ExtraScoreSubmissionWindowService extraScoreSubmissionWindowService;
    private final ProofFileStorageService proofFileStorageService;
    private final ExtraScoreEffectiveScoreService effectiveScoreService;
    private final ExtraScoreApplicationEventService applicationEventService;

    private record TeacherImportCategory(
            ExtraScoreTemplate template,
            ExtraScoreTemplateGroup group,
            ExtraScoreTemplateItem item
    ) {
    }

    private record TeacherImportTemplateContext(
            ExtraScoreTemplate template,
            List<TeacherImportCategory> categories
    ) {
    }

    public ExtraScoreApplicationService(
            ExtraScoreApplicationRepository applicationRepository,
            ExtraScoreTemplateGroupRepository groupRepository,
            ExtraScoreTemplateItemRepository itemRepository,
            ExtraScoreTemplateItemLevelRuleRepository levelRuleRepository,
            ExtraScoreTemplateItemAuthorRuleRepository authorRuleRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AnnouncementRepository announcementRepository,
            AnnouncementRecipientRepository announcementRecipientRepository,
            ExtraScoreTemplateService extraScoreTemplateService,
            ExtraScoreSubmissionWindowService extraScoreSubmissionWindowService,
            ProofFileStorageService proofFileStorageService,
            ExtraScoreEffectiveScoreService effectiveScoreService,
            ExtraScoreApplicationEventService applicationEventService
    ) {
        this.applicationRepository = applicationRepository;
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.levelRuleRepository = levelRuleRepository;
        this.authorRuleRepository = authorRuleRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.announcementRepository = announcementRepository;
        this.announcementRecipientRepository = announcementRecipientRepository;
        this.extraScoreTemplateService = extraScoreTemplateService;
        this.extraScoreSubmissionWindowService = extraScoreSubmissionWindowService;
        this.proofFileStorageService = proofFileStorageService;
        this.effectiveScoreService = effectiveScoreService;
        this.applicationEventService = applicationEventService;
    }

    public Map<String, Object> listMyApplications(RequestUserContext context, String scoreType, int page, int size) {
        String normalizedType = normalizeScoreType(scoreType);
        Student student = resolveCurrentStudent(context);
        List<Map<String, Object>> rows = applicationRepository.findByStudentNoAndScoreTypeCaseInsensitive(student.getStudentNo(), normalizedType)
                .stream()
                .map(this::toStudentApplicationListItemMap)
                .toList();
        return buildRecordsPage(rows, page, size);
    }

    public List<Map<String, Object>> listMyApplicationProcess(RequestUserContext context) {
        Student student = resolveCurrentStudent(context);
        List<ExtraScoreApplication> applications = applicationRepository.findByStudentNoOrderByCreatedAtDesc(student.getStudentNo());
        Map<Long, Map<String, Object>> windowStates = resolveStudentProcessWindowStates(applications);
        Map<String, List<ExtraScoreApplication>> grouped = applications.stream()
                .collect(Collectors.groupingBy(this::buildApplicationPeriodKey, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .map(records -> toStudentProcessPeriodMap(records, windowStates))
                .sorted((left, right) -> String.valueOf(right.get("latestUploadTime")).compareTo(String.valueOf(left.get("latestUploadTime"))))
                .toList();
    }

    public Map<String, Object> getMyCurrentApplicationProcess(RequestUserContext context) {
        Student student = resolveCurrentStudent(context);
        migrateDraftLikeApplicationsToActiveWindow(student);
        List<ExtraScoreApplication> applications = applicationRepository.findByStudentNoOrderByCreatedAtDesc(student.getStudentNo());
        Map<Long, Map<String, Object>> windowStates = resolveStudentProcessWindowStates(applications);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentProcess", resolveCurrentProcessSummary(applications, windowStates));
        result.put("approvedExtraScoreByType", summarizeApprovedExtraScores(applications));
        return result;
    }

    public Map<String, Object> getMyApplicationListContext(RequestUserContext context, String scoreType, String status, String keyword, String sort, int page, int size) {
        String normalizedType = normalizeScoreType(scoreType);
        Student student = resolveCurrentStudent(context);
        migrateDraftLikeApplicationsToActiveWindow(student);
        List<ExtraScoreApplication> applications = applicationRepository.findByStudentNoAndScoreTypeCaseInsensitive(student.getStudentNo(), normalizedType);
        List<Map<String, Object>> allRows = applications.stream()
                .map(this::toStudentListMap)
                .toList();
        List<Map<String, Object>> filteredRows = filterAndSortStudentApplicationRows(allRows, status, keyword, sort);
        Map<String, Object> recordsPage = buildRecordsPage(filteredRows, page, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateInfo", extraScoreTemplateService.getAssignedTemplateFormContext(context, normalizedType));
        List<?> pageRows = recordsPage.get("records") instanceof List<?> list ? list : List.of();
        result.put("records", pageRows.stream()
                .filter(Map.class::isInstance)
                .map(item -> toStudentApplicationListItemMap((Map<String, Object>) item))
                .toList());
        result.put("total", recordsPage.get("total"));
        result.put("page", recordsPage.get("page"));
        result.put("size", recordsPage.get("size"));
        result.put("stats", buildStudentApplicationStats(allRows));
        Map<String, Object> submissionWindow = null;
        try {
            submissionWindow = extraScoreSubmissionWindowService.getCurrentWindow(student);
        } catch (IllegalArgumentException exception) {
            submissionWindow = null;
        }
        result.put("submissionWindow", submissionWindow);
        result.put("periodSummary", buildStudentApplicationPeriodSummary(filteredRows, submissionWindow));
        return result;
    }

    public List<Map<String, Object>> listMyApplicationsByAcademicYear(RequestUserContext context, String academicYear) {
        Student student = resolveCurrentStudent(context);
        return applicationRepository.findByStudentNoOrderByCreatedAtDesc(student.getStudentNo()).stream()
                .map(this::toMap)
                .filter(item -> academicYear == null || academicYear.isBlank() || academicYear.equals(String.valueOf(item.get("academicYear"))))
                .toList();
    }

    public Map<String, Object> listMyApplicationHistory(
            RequestUserContext context,
            String academicYear,
            String scoreType,
            String statusGroup,
            String keyword,
            int page,
            int size
    ) {
        Student student = resolveCurrentStudent(context);
        String normalizedType = normalizeReviewScoreType(scoreType);
        ExtraScoreSubmissionWindow activeWindow = resolveOptionalActiveWindow(student);
        List<ExtraScoreApplication> scopedApplications = applicationRepository
                .findByStudentNoOrderByCreatedAtDesc(student.getStudentNo()).stream()
                .filter(application -> academicYear == null || academicYear.isBlank() || academicYear.equals(resolveApplicationAcademicYear(application)))
                .filter(application -> normalizedType == null || normalizedType.equalsIgnoreCase(defaultString(application.getScoreType())))
                .filter(application -> matchesHistoryKeyword(application, keyword))
                .toList();
        Map<String, Long> counts = buildHistoryStatusCounts(scopedApplications, activeWindow);
        List<Map<String, Object>> rows = scopedApplications.stream()
                .filter(application -> matchesHistoryStatusGroup(application, activeWindow, statusGroup))
                .sorted(historyApplicationComparator(activeWindow))
                .map(application -> toStudentHistoryListMap(
                        application,
                        canEditHistoryApplication(application, activeWindow)
                ))
                .toList();
        Map<String, Object> result = buildRecordsPage(rows, page, size);
        result.put("actionRequiredCount", counts.getOrDefault("actionRequired", 0L));
        result.put("counts", counts);
        return result;
    }

    private ExtraScoreSubmissionWindow resolveOptionalActiveWindow(Student student) {
        try {
            return extraScoreSubmissionWindowService.getActiveSubmissionWindow(student);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean matchesHistoryKeyword(ExtraScoreApplication application, String keyword) {
        String normalizedKeyword = defaultString(keyword).trim().toLowerCase(Locale.ROOT);
        if (normalizedKeyword.isBlank()) {
            return true;
        }
        return java.util.stream.Stream.of(
                        application.getItemName(),
                        application.getGroupName(),
                        application.getCertName(),
                        application.getLevelName(),
                        application.getSubmissionPeriodLabel()
                )
                .map(this::defaultString)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedKeyword));
    }

    private boolean canEditHistoryApplication(
            ExtraScoreApplication application,
            ExtraScoreSubmissionWindow activeWindow
    ) {
        return activeWindow != null
                && isDraftLikeStatus(application.getStatus())
                && !isApprovedReviewResult(application);
    }

    private String resolveHistoryStatusGroup(ExtraScoreApplication application, boolean canEdit) {
        if (canEdit) {
            return "action_required";
        }
        if ("pending".equals(application.getStatus())) {
            return "reviewing";
        }
        if (List.of("approved", "returned", "rejected").contains(defaultString(application.getStatus()))) {
            return "completed";
        }
        return "other";
    }

    private String resolveHistoryDisplayStatus(ExtraScoreApplication application) {
        String status = defaultString(application.getStatus()).toLowerCase(Locale.ROOT);
        if ("draft".equals(status)) {
            return "草稿";
        }
        if ("returned".equals(status) || "rejected".equals(status)) {
            return "已打回";
        }
        if ("approved".equals(status)) {
            return "已通过";
        }
        if ("pending".equals(status)) {
            return "teacher_pending".equals(application.getReviewStage()) ? "教师审核中" : "班委审核中";
        }
        return resolveReviewStatusLabel(application.getStatus());
    }

    private boolean matchesHistoryStatusGroup(
            ExtraScoreApplication application,
            ExtraScoreSubmissionWindow activeWindow,
            String statusGroup
    ) {
        String normalizedGroup = defaultString(statusGroup).trim().toLowerCase(Locale.ROOT);
        if (normalizedGroup.isBlank() || "all".equals(normalizedGroup)) {
            return true;
        }
        return normalizedGroup.equals(resolveHistoryStatusGroup(
                application,
                canEditHistoryApplication(application, activeWindow)
        ));
    }

    private Map<String, Long> buildHistoryStatusCounts(
            List<ExtraScoreApplication> applications,
            ExtraScoreSubmissionWindow activeWindow
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", (long) applications.size());
        counts.put("actionRequired", applications.stream()
                .filter(application -> canEditHistoryApplication(application, activeWindow))
                .count());
        counts.put("reviewing", applications.stream()
                .filter(application -> "pending".equals(application.getStatus()))
                .count());
        counts.put("completed", applications.stream()
                .filter(application -> List.of("approved", "returned", "rejected")
                        .contains(defaultString(application.getStatus())))
                .count());
        return counts;
    }

    private Comparator<ExtraScoreApplication> historyApplicationComparator(
            ExtraScoreSubmissionWindow activeWindow
    ) {
        return Comparator
                .comparing((ExtraScoreApplication application) ->
                        canEditHistoryApplication(application, activeWindow) ? 0 : 1)
                .thenComparing(
                        application -> application.getUpdatedAt() == null
                                ? application.getCreatedAt()
                                : application.getUpdatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        ExtraScoreApplication::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
    }

    public Map<String, Object> getMyApplication(RequestUserContext context, Long id) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!student.getStudentNo().equals(application.getStudentNo())) {
            throw new IllegalArgumentException("无权查看该申请记录");
        }
        Map<String, Object> result = toStudentDetailMap(application);
        result.put("events", applicationEventService.listTimeline(application.getId()));
        return result;
    }

    public Map<String, Object> getMyApplicationEditContext(RequestUserContext context, Long id, String scoreType) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!student.getStudentNo().equals(application.getStudentNo())) {
            throw new IllegalArgumentException("无权查看该申请记录");
        }
        String normalizedType = scoreType == null || scoreType.isBlank()
                ? normalizeScoreType(application.getScoreType())
                : normalizeScoreType(scoreType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", toMap(application));
        result.put("templateInfo", extraScoreTemplateService.getAssignedTemplateFormContext(context, normalizedType));
        try {
            result.put("submissionWindow", extraScoreSubmissionWindowService.getCurrentWindow(student));
        } catch (IllegalArgumentException exception) {
            result.put("submissionWindow", null);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> createApplication(RequestUserContext context, ExtraScoreApplicationRequest request) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreSubmissionWindow submissionWindow = extraScoreSubmissionWindowService.getActiveSubmissionWindow(student);
        String normalizedType = normalizeScoreType(request.getScoreType());
        Map<String, Object> assignedTemplateData = extraScoreTemplateService.getAssignedTemplate(context, normalizedType);
        Map<String, Object> assignedTemplate = castTemplateMap(assignedTemplateData.get("template"));
        if (assignedTemplate == null) {
            throw new IllegalArgumentException("当前老师尚未为该附加分类配置模板");
        }
        Long assignedTemplateId = parseLongValue(assignedTemplate.get("id"));
        if (!assignedTemplateId.equals(request.getTemplateId())) {
            throw new IllegalArgumentException("当前模板已变更，请刷新后按最新模板重新提交");
        }

        ExtraScoreTemplate templateEntity = extraScoreTemplateService.findTemplateEntity(assignedTemplateId);
        ExtraScoreApplication application = new ExtraScoreApplication();
        applyRequest(application, student, templateEntity, request);
        applySubmissionWindow(application, submissionWindow);
        application.setSubmissionType(resolveSubmissionType(student.getStudentNo(), submissionWindow, false));
        resetDraftWorkflow(application);
        ExtraScoreApplication saved = applicationRepository.save(application);
        applicationEventService.record(saved, "CREATED", null, context, student.getName(), "", true);
        return toMap(saved);
    }

    @Transactional
    public Map<String, Object> updateApplication(RequestUserContext context, Long id, ExtraScoreApplicationRequest request) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreSubmissionWindow submissionWindow = extraScoreSubmissionWindowService.getActiveSubmissionWindow(student);
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!student.getStudentNo().equals(application.getStudentNo())) {
            throw new IllegalArgumentException("无权修改该申请记录");
        }
        if (!isDraftLikeStatus(application.getStatus())) {
            throw new IllegalArgumentException("仅草稿或被驳回记录可以编辑，已提交审核或已通过记录不可修改");
        }
        String previousStatus = application.getStatus();
        boolean wasApproved = "approved".equals(previousStatus);

        Map<String, Object> assignedTemplateData = extraScoreTemplateService.getAssignedTemplate(context, request.getScoreType());
        Map<String, Object> assignedTemplate = castTemplateMap(assignedTemplateData.get("template"));
        if (assignedTemplate == null) {
            throw new IllegalArgumentException("当前老师尚未为该附加分类配置模板");
        }
        Long assignedTemplateId = parseLongValue(assignedTemplate.get("id"));
        if (!assignedTemplateId.equals(request.getTemplateId())) {
            throw new IllegalArgumentException("当前模板已变更，请刷新后按最新模板重新提交");
        }

        ExtraScoreTemplate templateEntity = extraScoreTemplateService.findTemplateEntity(assignedTemplateId);
        applyRequest(application, student, templateEntity, request);
        applySubmissionWindow(application, submissionWindow);
        application.setSubmissionType(resolveSubmissionType(student.getStudentNo(), submissionWindow, wasApproved));
        resetDraftWorkflow(application);
        ExtraScoreApplication saved = applicationRepository.save(application);
        applicationEventService.record(saved, "UPDATED", previousStatus, context, student.getName(), "", true);
        return toMap(saved);
    }

    @Transactional
    public Map<String, Object> submitCurrentWindowApplications(RequestUserContext context) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreSubmissionWindow submissionWindow = extraScoreSubmissionWindowService.getActiveSubmissionWindow(student);
        migrateDraftLikeApplicationsToWindow(student, submissionWindow);
        List<ExtraScoreApplication> applications = applicationRepository
                .findByStudentNoAndSubmissionWindowId(student.getStudentNo(), submissionWindow.getId());
        assertNoSubmittedPackageInWindow(applications);
        List<ExtraScoreApplication> drafts = applications.stream()
                .filter(this::canResubmitApplication)
                .toList();
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("当前周期没有可提交审核的草稿或补充记录");
        }
        List<ExtraScoreApplication> approvedReviewRecords = restoreApprovedReviewRecordsForResubmission(applications);
        Map<Long, String> previousStatuses = drafts.stream().collect(Collectors.toMap(
                ExtraScoreApplication::getId,
                item -> defaultString(item.getStatus())
        ));
        for (ExtraScoreApplication application : drafts) {
            resetReviewWorkflow(application);
            applicationEventService.recordSubmission(
                    application,
                    previousStatuses.get(application.getId()),
                    context,
                    student.getName()
            );
        }
        List<ExtraScoreApplication> updatedApplications = new ArrayList<>(drafts);
        updatedApplications.addAll(approvedReviewRecords);
        applicationRepository.saveAll(updatedApplications);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submittedCount", drafts.size());
        result.put("applications", drafts.stream().map(this::toMap).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> submitSelectedApplications(RequestUserContext context, List<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要提交审核的草稿记录");
        }
        Student student = resolveCurrentStudent(context);
        ExtraScoreSubmissionWindow submissionWindow = extraScoreSubmissionWindowService.getActiveSubmissionWindow(student);
        migrateDraftLikeApplicationsToWindow(student, submissionWindow);
        Set<Long> selectedIds = new HashSet<>(applicationIds);
        List<ExtraScoreApplication> applications = applicationRepository
                .findByStudentNoAndSubmissionWindowId(student.getStudentNo(), submissionWindow.getId());
        assertNoSubmittedPackageInWindow(applications);
        List<ExtraScoreApplication> drafts = applications.stream()
                .filter(item -> item.getId() != null && selectedIds.contains(item.getId()))
                .filter(this::canResubmitApplication)
                .toList();
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("所选记录中没有可提交审核的草稿或补充记录");
        }
        List<ExtraScoreApplication> approvedReviewRecords = restoreApprovedReviewRecordsForResubmission(applications);
        Map<Long, String> previousStatuses = drafts.stream().collect(Collectors.toMap(
                ExtraScoreApplication::getId,
                item -> defaultString(item.getStatus())
        ));
        for (ExtraScoreApplication application : drafts) {
            resetReviewWorkflow(application);
            applicationEventService.recordSubmission(
                    application,
                    previousStatuses.get(application.getId()),
                    context,
                    student.getName()
            );
        }
        List<ExtraScoreApplication> updatedApplications = new ArrayList<>(drafts);
        updatedApplications.addAll(approvedReviewRecords);
        applicationRepository.saveAll(updatedApplications);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submittedCount", drafts.size());
        result.put("applications", drafts.stream().map(this::toStudentListMap).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> withdrawCurrentWindowApplications(RequestUserContext context) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreSubmissionWindow submissionWindow = extraScoreSubmissionWindowService.getActiveSubmissionWindow(student);
        List<ExtraScoreApplication> applications = applicationRepository
                .findByStudentNoAndSubmissionWindowId(student.getStudentNo(), submissionWindow.getId());
        List<ExtraScoreApplication> withdrawable = applications.stream()
                .filter(item -> "pending".equals(item.getStatus()))
                .filter(item -> !"pending".equals(item.getWithdrawalStatus()))
                .toList();
        if (withdrawable.isEmpty()) {
            boolean hasWithdrawalPending = applications.stream()
                    .anyMatch(item -> "pending".equals(item.getStatus())
                            && "pending".equals(item.getWithdrawalStatus()));
            if (hasWithdrawalPending) {
                throw new IllegalArgumentException("撤回申请已提交，请等待班委处理");
            }
            throw new IllegalArgumentException("当前周期没有可撤回的已提交申请");
        }

        boolean requiresCommitteeApproval = hasCommitteeReviewedAnyRecord(applications);
        if (!requiresCommitteeApproval) {
            for (ExtraScoreApplication application : withdrawable) {
                String previousStatus = application.getStatus();
                resetDraftWorkflow(application);
                applicationEventService.record(
                        application,
                        "WITHDRAWN",
                        previousStatus,
                        context,
                        student.getName(),
                        "学生主动撤回",
                        false
                );
            }
            applicationRepository.saveAll(withdrawable);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestedCount", withdrawable.size());
            result.put("withdrawnCount", withdrawable.size());
            result.put("approvalRequired", false);
            result.put("message", "撤回成功，已退回草稿");
            result.put("applications", withdrawable.stream().map(this::toStudentListMap).toList());
            return result;
        }

        for (ExtraScoreApplication application : withdrawable) {
            application.setWithdrawalStatus("pending");
            application.setWithdrawalRequestedAt(LocalDateTime.now());
            application.setWithdrawalReviewedAt(null);
            application.setWithdrawalReviewerId(null);
            application.setWithdrawalReviewerName(null);
            application.setWithdrawalComment(null);
            applicationEventService.record(
                    application,
                    "WITHDRAWAL_REQUESTED",
                    application.getStatus(),
                    context,
                    student.getName(),
                    "申请撤回",
                    false
            );
        }
        applicationRepository.saveAll(withdrawable);
        notifyClassCommitteeWithdrawalRequest(student, withdrawable);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedCount", withdrawable.size());
        result.put("withdrawnCount", 0);
        result.put("approvalRequired", true);
        result.put("message", "撤回申请已提交，等待班委同意");
        result.put("applications", withdrawable.stream().map(this::toStudentListMap).toList());
        return result;
    }

    @Transactional
    public void deleteApplication(RequestUserContext context, Long id) {
        Student student = resolveCurrentStudent(context);
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!student.getStudentNo().equals(application.getStudentNo())) {
            throw new IllegalArgumentException("无权删除该申请记录");
        }
        if ("approved".equals(application.getStatus()) || isApprovedReviewResult(application)) {
            throw new IllegalArgumentException("已通过的申请不可删除");
        }
        applicationEventService.deleteTimeline(application.getId());
        applicationRepository.delete(application);
    }

    @Transactional
    public void deleteTeacherImportedApplication(RequestUserContext context, Long id) {
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以删除自己导入的附加分记录");
        }
        Long teacherId = resolveTeacherId(context);
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!"teacher_import".equals(defaultString(application.getSubmissionType()))) {
            throw new IllegalArgumentException("学生提交的申请不能删除，请通过驳回处理");
        }
        if (!teacherId.equals(application.getTeacherId())) {
            throw new IllegalArgumentException("无权删除该导入记录");
        }
        applicationEventService.deleteTimeline(application.getId());
        applicationRepository.delete(application);
    }

    public List<Map<String, Object>> listReviewApplications(RequestUserContext context, String scoreType, String status,
                                                              String classId, String startDate, String endDate, String keyword,
                                                              boolean currentPeriodOnly) {
        String normalizedType = normalizeReviewScoreType(scoreType);
        String normalizedClassName = classId != null && !classId.equals("all") ? classId : null;
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        // Parse date strings
        try {
            if (startDate != null && !startDate.isEmpty()) {
                startDateTime = LocalDateTime.parse(startDate + "T00:00:00");
            }
            if (endDate != null && !endDate.isEmpty()) {
                endDateTime = LocalDateTime.parse(endDate + "T23:59:59");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式错误");
        }

        String normalizedKeyword = keyword != null && !keyword.trim().isEmpty() ? keyword.trim() : null;

        List<ExtraScoreApplication> applications;

        if (context.isAdmin()) {
            applications = applicationRepository.findByFilters(normalizedType, normalizedClassName,
                                                               startDateTime, endDateTime, normalizedKeyword);
        } else if (context.isTeacher()) {
            Long teacherId = resolveTeacherId(context);
            applications = applicationRepository.findByTeacherIdAndFilters(teacherId, normalizedType,
                                                                          normalizedClassName, startDateTime,
                                                                          endDateTime, normalizedKeyword);
        } else if (context.isClassCommittee()) {
            Long teacherId = resolveTeacherId(context);
            String className = defaultString(context.getClassName());
            if (className.isBlank()) {
                throw new IllegalArgumentException("当前班委未配置班级信息");
            }
            // For committee, use their own class if no class filter specified
            String filterClass = normalizedClassName != null ? normalizedClassName : className;
            applications = applicationRepository.findByTeacherIdAndFilters(teacherId, normalizedType,
                                                                          filterClass, startDateTime,
                                                                          endDateTime, normalizedKeyword);
        } else {
            throw new IllegalArgumentException("当前角色无权查看审核列表");
        }

        String normalizedStatus = status == null ? "all" : status.trim().toLowerCase();
        Set<Long> currentWindowIds = currentPeriodOnly
                ? new HashSet<>(extraScoreSubmissionWindowService.findCurrentReviewWindowIds(context))
                : Set.of();
        String currentAcademicYear = currentPeriodOnly ? extraScoreSubmissionWindowService.resolveCurrentAcademicYear() : "";
        Map<String, Long> committeeCountsByClass = new LinkedHashMap<>();
        return applications.stream()
                .filter(application -> !currentPeriodOnly || isCurrentReviewRecord(application, currentWindowIds, currentAcademicYear))
                .filter(application -> matchesStatus(application, normalizedStatus))
                .map(application -> toReviewMap(application, context, committeeCountsByClass))
                .toList();
    }

    public List<Map<String, Object>> listReviewStudentGroups(RequestUserContext context, String scoreType, String status,
                                                              String classId, String startDate, String endDate, String keyword,
                                                              boolean currentPeriodOnly) {
        List<Map<String, Object>> applications = listReviewApplications(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> application : applications) {
            String key = defaultString(String.valueOf(application.get("studentNo")));
            if (key.isBlank()) {
                key = defaultString(String.valueOf(application.get("studentName"))) + "-" + defaultString(String.valueOf(application.get("className")));
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(application);
        }
        return grouped.values().stream()
                .map(this::toReviewStudentGroupMap)
                .sorted((left, right) -> {
                    int actionableCompare = Long.compare(
                            ((Number) right.getOrDefault("actionablePending", 0)).longValue(),
                            ((Number) left.getOrDefault("actionablePending", 0)).longValue()
                    );
                    if (actionableCompare != 0) {
                        return actionableCompare;
                    }
                    int pendingCompare = Integer.compare(((Number) right.get("pending")).intValue(), ((Number) left.get("pending")).intValue());
                    if (pendingCompare != 0) {
                        return pendingCompare;
                    }
                    return String.valueOf(right.get("latestUploadTime")).compareTo(String.valueOf(left.get("latestUploadTime")));
                })
                .toList();
    }

    public List<Map<String, Object>> listReviewClassGroups(RequestUserContext context, String scoreType, String status,
                                                            String classId, String startDate, String endDate, String keyword,
                                                            boolean currentPeriodOnly) {
        List<Map<String, Object>> studentGroups = listReviewStudentGroups(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> student : studentGroups) {
            String name = defaultString(String.valueOf(student.get("className")));
            if (name.isBlank()) {
                name = "未分班";
            }
            grouped.computeIfAbsent(name, ignored -> new ArrayList<>()).add(student);
        }
        return grouped.entrySet().stream()
                .map(entry -> toReviewClassGroupMap(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> {
                    int pendingCompare = Integer.compare(((Number) right.get("pending")).intValue(), ((Number) left.get("pending")).intValue());
                    if (pendingCompare != 0) {
                        return pendingCompare;
                    }
                    return String.valueOf(left.get("className")).compareTo(String.valueOf(right.get("className")));
                })
                .toList();
    }

    public Map<String, Object> getReviewWorkbench(RequestUserContext context, String scoreType, boolean currentPeriodOnly) {
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("当前角色无权查看审核工作台");
        }

        List<Student> scopedStudents = context.isAdmin()
                ? studentRepository.findAll()
                : studentRepository.findByTeacherId(resolveTeacherId(context));
        Map<String, Map<String, Object>> rowsByClass = new LinkedHashMap<>();
        Map<String, Set<String>> submittedStudentsByClass = new LinkedHashMap<>();

        for (Student student : scopedStudents) {
            String className = normalizeReviewClassName(student.getClassName());
            Map<String, Object> row = rowsByClass.computeIfAbsent(className, this::newReviewWorkbenchClassRow);
            incrementReviewWorkbenchCount(row, "studentCount");
        }

        Map<String, ExtraScoreSubmissionWindow> windowsByClass = extraScoreSubmissionWindowService
                .findEnabledReviewWindows(context)
                .stream()
                .collect(Collectors.toMap(
                        window -> normalizeReviewClassName(window.getClassName()),
                        window -> window,
                        (left, right) -> {
                            LocalDateTime leftCreatedAt = left.getCreatedAt() == null ? LocalDateTime.MIN : left.getCreatedAt();
                            LocalDateTime rightCreatedAt = right.getCreatedAt() == null ? LocalDateTime.MIN : right.getCreatedAt();
                            return rightCreatedAt.isAfter(leftCreatedAt) ? right : left;
                        },
                        LinkedHashMap::new
                ));
        windowsByClass.forEach((className, window) -> {
            Map<String, Object> row = rowsByClass.computeIfAbsent(className, this::newReviewWorkbenchClassRow);
            row.put("reviewEnabled", true);
            row.put("academicYear", window.getAcademicYear());
            row.put("deadline", window.getEndTime());
        });

        String normalizedType = normalizeReviewScoreType(scoreType);
        List<ExtraScoreApplication> applications = context.isAdmin()
                ? applicationRepository.findByFilters(normalizedType, null, null, null, null)
                : applicationRepository.findByTeacherIdAndFilters(
                        resolveTeacherId(context), normalizedType, null, null, null, null
                );
        Set<Long> currentWindowIds = currentPeriodOnly
                ? new HashSet<>(extraScoreSubmissionWindowService.findCurrentReviewWindowIds(context))
                : Set.of();
        String currentAcademicYear = extraScoreSubmissionWindowService.resolveCurrentAcademicYear();
        Map<String, Long> committeeCountsByClass = countReviewCommitteeMembersByClass(scopedStudents);

        for (ExtraScoreApplication application : applications) {
            if (currentPeriodOnly && !isCurrentReviewRecord(application, currentWindowIds, currentAcademicYear)) {
                continue;
            }
            String className = normalizeReviewClassName(application.getClassName());
            Map<String, Object> row = rowsByClass.computeIfAbsent(className, this::newReviewWorkbenchClassRow);
            incrementReviewWorkbenchCount(row, "totalRecords");

            String studentKey = defaultString(application.getStudentNo()).trim();
            if (studentKey.isBlank()) {
                studentKey = defaultString(application.getStudentName()).trim();
            }
            if (!studentKey.isBlank()) {
                submittedStudentsByClass.computeIfAbsent(className, ignored -> new HashSet<>()).add(studentKey);
            }

            String status = defaultString(application.getStatus()).trim().toLowerCase();
            String stage = defaultString(application.getReviewStage()).trim().toLowerCase();
            if ("pending".equals(status)) {
                incrementReviewWorkbenchCount(row, "pending");
            } else if ("approved".equals(status)) {
                incrementReviewWorkbenchCount(row, "approved");
            } else if ("returned".equals(status) || "rejected".equals(status)) {
                incrementReviewWorkbenchCount(row, "returned");
            }
            if ("committee_pending".equals(stage)) {
                incrementReviewWorkbenchCount(row, "committeePending");
            } else if ("teacher_pending".equals(stage)) {
                incrementReviewWorkbenchCount(row, "teacherPending");
            }

            long committeeCount = committeeCountsByClass.getOrDefault(className, 0L);
            boolean canReview = canTeacherReviewForWorkbench(context, application, committeeCount);
            boolean canReviewWithdrawal = canTeacherReviewWithdrawalForWorkbench(
                    context, application, committeeCount
            );
            boolean withdrawalPending = "pending".equals(
                    defaultString(application.getWithdrawalStatus()).trim().toLowerCase()
            );
            if (withdrawalPending) {
                incrementReviewWorkbenchCount(row, "withdrawalPending");
                if (canReviewWithdrawal) {
                    incrementReviewWorkbenchCount(row, "actionableWithdrawal");
                }
            } else if (canReview) {
                incrementReviewWorkbenchCount(row, "actionableReview");
                if ("committee_pending".equals(stage)) {
                    incrementReviewWorkbenchCount(row, "directTeacherReview");
                }
            }

            LocalDateTime updatedAt = application.getUpdatedAt();
            if (updatedAt != null) {
                LocalDateTime latestUpdatedAt = (LocalDateTime) row.get("latestUpdatedAt");
                if (latestUpdatedAt == null || updatedAt.isAfter(latestUpdatedAt)) {
                    row.put("latestUpdatedAt", updatedAt);
                }
            }
        }

        List<Map<String, Object>> classes = rowsByClass.entrySet().stream()
                .map(entry -> finalizeReviewWorkbenchClassRow(
                        entry.getValue(),
                        submittedStudentsByClass.getOrDefault(entry.getKey(), Set.of())
                ))
                .filter(row -> Boolean.TRUE.equals(row.get("reviewEnabled"))
                        || ((Number) row.get("totalRecords")).intValue() > 0)
                .sorted((left, right) -> {
                    int actionableCompare = Integer.compare(
                            ((Number) right.get("actionablePending")).intValue(),
                            ((Number) left.get("actionablePending")).intValue()
                    );
                    if (actionableCompare != 0) {
                        return actionableCompare;
                    }
                    int pendingCompare = Integer.compare(
                            ((Number) right.get("pending")).intValue(),
                            ((Number) left.get("pending")).intValue()
                    );
                    if (pendingCompare != 0) {
                        return pendingCompare;
                    }
                    return valueString(left.get("className")).compareTo(valueString(right.get("className")));
                })
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("classCount", classes.size());
        summary.put("studentCount", sumReviewWorkbenchField(classes, "submittedStudentCount"));
        summary.put("totalRecords", sumReviewWorkbenchField(classes, "totalRecords"));
        summary.put("actionablePending", sumReviewWorkbenchField(classes, "actionablePending"));
        summary.put("pending", sumReviewWorkbenchField(classes, "pending"));
        summary.put("waitingCommittee", sumReviewWorkbenchField(classes, "waitingCommittee"));
        summary.put("approved", sumReviewWorkbenchField(classes, "approved"));
        summary.put("returned", sumReviewWorkbenchField(classes, "returned"));

        Long teacherId = context.isTeacher() ? resolveTeacherId(context) : null;
        Set<String> academicYears = new HashSet<>(applicationRepository.findReviewAcademicYears(teacherId));
        if (!currentAcademicYear.isBlank()) {
            academicYears.add(currentAcademicYear);
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("academicYears", academicYears.stream().filter(year -> !year.isBlank()).sorted(Comparator.reverseOrder()).toList());
        filters.put("classes", classes.stream().map(row -> row.get("className")).toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("context", Map.of(
                "role", context.getRole(),
                "readOnly", context.isAdmin(),
                "currentAcademicYear", currentAcademicYear
        ));
        result.put("summary", summary);
        result.put("filters", filters);
        result.put("classes", classes);
        return result;
    }

    public Map<String, Object> searchMyTemplateItems(
            RequestUserContext context,
            String scoreType,
            String keyword,
            int limit
    ) {
        String normalizedType = normalizeScoreType(scoreType);
        String normalizedKeyword = defaultString(keyword).toLowerCase(Locale.ROOT);
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        Map<String, Object> templateInfo = extraScoreTemplateService.getAssignedTemplateFormContext(context, normalizedType);
        Map<String, Object> template = castTemplateMap(templateInfo.get("template"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scoreType", normalizedType);
        result.put("keyword", defaultString(keyword));
        result.put("limit", normalizedLimit);
        if (template == null) {
            result.put("templateId", null);
            result.put("groups", List.of());
            result.put("total", 0);
            result.put("hasMore", false);
            return result;
        }

        List<Map<String, Object>> matchedGroups = new ArrayList<>();
        int matchedCount = 0;
        int returnedCount = 0;
        for (Map<String, Object> group : castMapList(template.get("groups"))) {
            String groupName = stringValue(group.get("groupName"));
            boolean groupMatches = containsIgnoreCase(groupName, normalizedKeyword);
            List<Map<String, Object>> returnedItems = new ArrayList<>();
            for (Map<String, Object> item : castMapList(group.get("items"))) {
                if ("inactive".equalsIgnoreCase(stringValue(item.get("status")))) {
                    continue;
                }
                boolean itemMatches = containsIgnoreCase(item.get("itemName"), normalizedKeyword);
                if (!normalizedKeyword.isBlank() && !groupMatches && !itemMatches) {
                    continue;
                }
                matchedCount++;
                if (returnedCount < normalizedLimit) {
                    returnedItems.add(item);
                    returnedCount++;
                }
            }
            if (!returnedItems.isEmpty()) {
                Map<String, Object> matchedGroup = new LinkedHashMap<>(group);
                matchedGroup.put("items", returnedItems);
                matchedGroups.add(matchedGroup);
            }
        }

        result.put("templateId", template.get("id"));
        result.put("templateName", template.get("name"));
        result.put("groups", matchedGroups);
        result.put("total", matchedCount);
        result.put("hasMore", matchedCount > returnedCount);
        return result;
    }

    private String normalizeReviewClassName(String className) {
        String normalized = defaultString(className).trim();
        return normalized.isBlank() ? "未分班" : normalized;
    }

    private Map<String, Object> newReviewWorkbenchClassRow(String className) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("className", className);
        row.put("studentCount", 0);
        row.put("submittedStudentCount", 0);
        row.put("totalRecords", 0);
        row.put("pending", 0);
        row.put("actionableReview", 0);
        row.put("actionableWithdrawal", 0);
        row.put("actionablePending", 0);
        row.put("withdrawalPending", 0);
        row.put("committeePending", 0);
        row.put("teacherPending", 0);
        row.put("directTeacherReview", 0);
        row.put("waitingCommittee", 0);
        row.put("approved", 0);
        row.put("returned", 0);
        row.put("processed", 0);
        row.put("progressPercent", 0);
        row.put("latestUpdatedAt", null);
        row.put("reviewEnabled", false);
        row.put("academicYear", "");
        row.put("deadline", null);
        return row;
    }

    private void incrementReviewWorkbenchCount(Map<String, Object> row, String key) {
        row.put(key, ((Number) row.getOrDefault(key, 0)).intValue() + 1);
    }

    private Map<String, Object> finalizeReviewWorkbenchClassRow(Map<String, Object> row, Set<String> submittedStudents) {
        int totalRecords = ((Number) row.get("totalRecords")).intValue();
        int approved = ((Number) row.get("approved")).intValue();
        int returned = ((Number) row.get("returned")).intValue();
        int actionableReview = ((Number) row.get("actionableReview")).intValue();
        int actionableWithdrawal = ((Number) row.get("actionableWithdrawal")).intValue();
        int committeePending = ((Number) row.get("committeePending")).intValue();
        int directTeacherReview = ((Number) row.get("directTeacherReview")).intValue();
        int processed = approved + returned;

        row.put("submittedStudentCount", submittedStudents.size());
        row.put("actionablePending", actionableReview + actionableWithdrawal);
        row.put("waitingCommittee", Math.max(committeePending - directTeacherReview, 0));
        row.put("processed", processed);
        row.put("progressPercent", totalRecords == 0 ? 0 : Math.round(processed * 100F / totalRecords));
        return row;
    }

    private int sumReviewWorkbenchField(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToInt(row -> ((Number) row.getOrDefault(key, 0)).intValue()).sum();
    }

    private Map<String, Long> countReviewCommitteeMembersByClass(List<Student> students) {
        Optional<Role> committeeRole = roleRepository.findByName("CLASS_COMMITTEE");
        if (committeeRole.isEmpty()) {
            return Map.of();
        }
        List<User> committeeUsers = userRepository.findByRoleId(committeeRole.get().getId());
        Set<Long> committeeUserIds = committeeUsers.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> committeeStudentNos = committeeUsers.stream()
                .map(User::getStudentNo)
                .filter(Objects::nonNull)
                .filter(studentNo -> !studentNo.isBlank())
                .collect(Collectors.toSet());
        return students.stream()
                .filter(student -> committeeUserIds.contains(student.getUserId())
                        || committeeStudentNos.contains(defaultString(student.getStudentNo())))
                .collect(Collectors.groupingBy(
                        student -> normalizeReviewClassName(student.getClassName()),
                        Collectors.counting()
                ));
    }

    private boolean canTeacherReviewForWorkbench(
            RequestUserContext context,
            ExtraScoreApplication application,
            long committeeCount
    ) {
        if (!context.isTeacher()
                || !"pending".equals(application.getStatus())
                || !"pending".equals(defaultString(application.getTeacherStatus()))
                || !resolveTeacherId(context).equals(application.getTeacherId())) {
            return false;
        }
        String stage = defaultString(application.getReviewStage());
        return "teacher_pending".equals(stage)
                || ("committee_pending".equals(stage) && committeeCount == 0);
    }

    private boolean canTeacherReviewWithdrawalForWorkbench(
            RequestUserContext context,
            ExtraScoreApplication application,
            long committeeCount
    ) {
        return context.isTeacher()
                && "pending".equals(application.getWithdrawalStatus())
                && resolveTeacherId(context).equals(application.getTeacherId())
                && committeeCount == 0;
    }

    public List<Map<String, Object>> listReviewClassProgress(RequestUserContext context) {
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("当前角色无权查看班级审核进度");
        }

        List<Student> scopedStudents = context.isAdmin()
                ? studentRepository.findAll()
                : studentRepository.findByTeacherId(resolveTeacherId(context));
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

        scopedStudents.stream()
                .sorted(Comparator.comparing(student -> defaultString(student.getClassName())))
                .forEach(student -> {
                    String className = defaultString(student.getClassName());
                    if (className.isBlank()) {
                        className = "未分班";
                    }
                    Map<String, Object> row = grouped.computeIfAbsent(className, this::newClassProgressRow);
                    row.put("studentCount", ((Number) row.get("studentCount")).intValue() + 1);
                });

        List<ExtraScoreApplication> applications = context.isAdmin()
                ? applicationRepository.findByFilters(null, null, null, null, null)
                : applicationRepository.findByTeacherIdAndFilters(resolveTeacherId(context), null, null, null, null, null);
        Map<String, Set<String>> appliedStudentsByClass = new LinkedHashMap<>();
        Set<String> enabledReviewClassNames = extraScoreSubmissionWindowService.findEnabledReviewClassNames(context);
        Set<Long> enabledReviewWindowIds = extraScoreSubmissionWindowService.findEnabledReviewWindowIds(context);
        Map<String, ExtraScoreSubmissionWindow> enabledWindowsByClass = extraScoreSubmissionWindowService.findEnabledReviewWindows(context).stream()
                .collect(Collectors.toMap(
                        window -> {
                            String className = defaultString(window.getClassName());
                            return className.isBlank() ? "未分班" : className;
                        },
                        window -> window,
                        (left, right) -> {
                            LocalDateTime leftCreatedAt = left.getCreatedAt() == null ? LocalDateTime.MIN : left.getCreatedAt();
                            LocalDateTime rightCreatedAt = right.getCreatedAt() == null ? LocalDateTime.MIN : right.getCreatedAt();
                            return rightCreatedAt.isAfter(leftCreatedAt) ? right : left;
                        },
                        LinkedHashMap::new
                ));
        applications = enabledReviewWindowIds.isEmpty()
                ? List.of()
                : applications.stream()
                    .filter(application -> application.getSubmissionWindowId() != null)
                    .filter(application -> enabledReviewWindowIds.contains(application.getSubmissionWindowId()))
                    .toList();

        for (ExtraScoreApplication application : applications) {
            String className = defaultString(application.getClassName());
            if (className.isBlank()) {
                className = "未分班";
            }
            Map<String, Object> row = grouped.computeIfAbsent(className, this::newClassProgressRow);
            row.put("total", ((Number) row.get("total")).intValue() + 1);
            LocalDateTime updatedAt = application.getUpdatedAt();
            LocalDateTime latestUpdatedAt = (LocalDateTime) row.get("recentUpdatedAt");
            if (updatedAt != null && (latestUpdatedAt == null || updatedAt.isAfter(latestUpdatedAt))) {
                row.put("recentUpdatedAt", updatedAt);
            }

            String status = defaultString(application.getStatus()).toLowerCase();
            String stage = defaultString(application.getReviewStage()).toLowerCase();
            if ("approved".equals(status)) {
                row.put("completed", ((Number) row.get("completed")).intValue() + 1);
            } else if ("teacher_pending".equals(stage)) {
                row.put("teacher", ((Number) row.get("teacher")).intValue() + 1);
            } else if ("pending".equals(status) && "committee_pending".equals(stage)) {
                row.put("committee", ((Number) row.get("committee")).intValue() + 1);
            } else if ("returned".equals(status) || "rejected".equals(status) || "returned".equals(stage)) {
                row.put("returned", ((Number) row.get("returned")).intValue() + 1);
            } else {
                row.put("draft", ((Number) row.get("draft")).intValue() + 1);
            }

            String studentKey = defaultString(application.getStudentNo());
            if (studentKey.isBlank()) {
                studentKey = defaultString(application.getStudentName());
            }
            if (!studentKey.isBlank()) {
                appliedStudentsByClass.computeIfAbsent(className, ignored -> new HashSet<>()).add(studentKey);
            }
        }

        grouped.forEach((className, row) -> {
            int studentCount = ((Number) row.get("studentCount")).intValue();
            int appliedStudentCount = appliedStudentsByClass.getOrDefault(className, Set.of()).size();
            row.put("appliedStudentCount", appliedStudentCount);
            row.put("unsubmittedCount", Math.max(studentCount - appliedStudentCount, 0));
            boolean reviewEnabled = enabledReviewClassNames.contains(className);
            int total = ((Number) row.get("total")).intValue();
            ExtraScoreSubmissionWindow window = enabledWindowsByClass.get(className);
            if (window != null) {
                row.put("academicYear", window.getAcademicYear());
                row.put("submissionStartTime", window.getStartTime());
                row.put("submissionEndTime", window.getEndTime());
                row.put("deadline", window.getEndTime());
                if (window.getEndTime() != null) {
                    row.put("daysUntilDeadline", ChronoUnit.DAYS.between(LocalDateTime.now(), window.getEndTime()));
                }
            }
            row.put("reviewEnabled", reviewEnabled);
            row.put("reviewInProgress", reviewEnabled && total > 0);
            applyClassProgressStatus(row);
            row.put("reviewActionLabel", !reviewEnabled ? "开启审核" : (total > 0 ? "进入审核" : "查看周期"));
            row.put("reviewActionPath", reviewEnabled && total > 0
                    ? "/admin/extra-score-review"
                    : "/admin/extra-score-submission-windows");
        });

        return grouped.values().stream()
                .sorted((left, right) -> {
                    int committeeCompare = Integer.compare(((Number) right.get("committee")).intValue(), ((Number) left.get("committee")).intValue());
                    if (committeeCompare != 0) {
                        return committeeCompare;
                    }
                    int teacherCompare = Integer.compare(((Number) right.get("teacher")).intValue(), ((Number) left.get("teacher")).intValue());
                    if (teacherCompare != 0) {
                        return teacherCompare;
                    }
                    int totalCompare = Integer.compare(((Number) right.get("total")).intValue(), ((Number) left.get("total")).intValue());
                    if (totalCompare != 0) {
                        return totalCompare;
                    }
                    return String.valueOf(left.get("className")).compareTo(String.valueOf(right.get("className")));
                })
                .toList();
    }

    private Map<String, Object> newClassProgressRow(String className) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("className", className);
        row.put("studentCount", 0);
        row.put("total", 0);
        row.put("draft", 0);
        row.put("committee", 0);
        row.put("teacher", 0);
        row.put("completed", 0);
        row.put("appliedStudentCount", 0);
        row.put("unsubmittedCount", 0);
        row.put("returned", 0);
        row.put("recentUpdatedAt", null);
        return row;
    }

    private void applyClassProgressStatus(Map<String, Object> row) {
        int studentCount = ((Number) row.get("studentCount")).intValue();
        int appliedStudentCount = ((Number) row.get("appliedStudentCount")).intValue();
        int committee = ((Number) row.get("committee")).intValue();
        int teacher = ((Number) row.get("teacher")).intValue();
        int completed = ((Number) row.get("completed")).intValue();
        int returned = ((Number) row.get("returned")).intValue();
        int total = ((Number) row.get("total")).intValue();
        int unsubmitted = ((Number) row.get("unsubmittedCount")).intValue();
        boolean reviewEnabled = Boolean.TRUE.equals(row.get("reviewEnabled"));

        String currentStage = "未开启";
        String currentStageKey = "not_started";
        int progressCurrent = 0;
        int progressTotal = Math.max(studentCount, total);
        String statusLabel = reviewEnabled ? "正常进行中" : "未开启";
        String attentionLevel = "normal";
        String attentionReason = "";

        if (!reviewEnabled) {
            progressTotal = Math.max(studentCount, 1);
        } else if (teacher > 0) {
            currentStage = "教师审核";
            currentStageKey = "teacher_review";
            progressCurrent = teacher;
            progressTotal = total;
            statusLabel = "需要处理";
            attentionLevel = "urgent";
            attentionReason = teacher + " 份材料等待教师审核";
        } else if (committee > 0) {
            currentStage = "班委审核";
            currentStageKey = "committee_review";
            progressCurrent = Math.max(appliedStudentCount - committee, 0);
            statusLabel = "等待班委";
            attentionLevel = "attention";
            attentionReason = committee + " 份材料等待班委初审";
        } else if (returned > 0) {
            currentStage = "退回修改";
            currentStageKey = "returned";
            progressCurrent = returned;
            progressTotal = total;
            statusLabel = "需要关注";
            attentionLevel = "attention";
            attentionReason = returned + " 份申请被退回";
        } else if (studentCount > 0 && appliedStudentCount < studentCount) {
            currentStage = "学生申请";
            currentStageKey = "student_apply";
            progressCurrent = appliedStudentCount;
            progressTotal = studentCount;
            statusLabel = "进行中";
            if (unsubmitted > 0) {
                attentionReason = unsubmitted + " 名学生尚未提交";
            }
        } else if (total > 0 && completed >= total) {
            currentStage = "完成审核";
            currentStageKey = "completed";
            progressCurrent = completed;
            progressTotal = total;
            statusLabel = "已完成";
        } else if (reviewEnabled) {
            currentStage = "学生申请";
            currentStageKey = "student_apply";
            progressCurrent = appliedStudentCount;
            progressTotal = Math.max(studentCount, 1);
            statusLabel = "等待提交";
        }

        Object daysValue = row.get("daysUntilDeadline");
        if (reviewEnabled && daysValue instanceof Number number) {
            long days = number.longValue();
            if (days >= 0 && days <= 2 && !"urgent".equals(attentionLevel)) {
                attentionLevel = "attention";
                attentionReason = attentionReason.isBlank()
                        ? "距离截止时间还有 " + days + " 天"
                        : attentionReason + "，距离截止还有 " + days + " 天";
                statusLabel = "需要关注";
            }
        }

        row.put("currentStage", currentStage);
        row.put("currentStageKey", currentStageKey);
        row.put("currentProgress", progressCurrent);
        row.put("currentProgressTotal", Math.max(progressTotal, 1));
        row.put("statusLabel", statusLabel);
        row.put("attentionLevel", attentionLevel);
        row.put("attentionReason", attentionReason);
        row.put("needsTeacherAction", teacher > 0);
        row.put("needsAttention", "urgent".equals(attentionLevel) || "attention".equals(attentionLevel));
    }

    private boolean isCurrentReviewRecord(ExtraScoreApplication application, Set<Long> currentWindowIds, String currentAcademicYear) {
        if (application == null) {
            return false;
        }
        if (application.getSubmissionWindowId() != null && currentWindowIds.contains(application.getSubmissionWindowId())) {
            return true;
        }
        return "teacher_import".equals(defaultString(application.getSubmissionType()))
                && !defaultString(currentAcademicYear).isBlank()
                && defaultString(currentAcademicYear).equals(resolveApplicationAcademicYear(application));
    }

    public List<Map<String, Object>> listReviewClassStudents(RequestUserContext context, String targetClassName,
                                                              String scoreType, String status, String classId,
                                                              String startDate, String endDate, String keyword,
                                                              boolean currentPeriodOnly) {
        String normalized = defaultString(targetClassName).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("班级名称不能为空");
        }
        return listReviewStudentGroups(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly).stream()
                .filter(item -> normalized.equals(defaultString(String.valueOf(item.get("className"))).trim()))
                .toList();
    }

    public Map<String, Object> listReviewClassStudents(RequestUserContext context, String targetClassName,
                                                       String scoreType, String status, String classId,
                                                       String startDate, String endDate, String keyword,
                                                       boolean currentPeriodOnly, int page, int size) {
        List<Map<String, Object>> rows = listReviewClassStudents(context, targetClassName, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly);
        return buildPage(rows, page, size);
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows.subList(fromIndex, toIndex));
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private List<Map<String, Object>> filterAndSortStudentApplicationRows(List<Map<String, Object>> rows, String status, String keyword, String sort) {
        String normalizedStatus = status == null || status.isBlank() ? "all" : status.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        Comparator<Map<String, Object>> comparator = switch (sort == null ? "" : sort) {
            case "award-desc" -> Comparator.comparing(row -> String.valueOf(row.getOrDefault("awardTime", "")), Comparator.reverseOrder());
            case "award-asc" -> Comparator.comparing(row -> String.valueOf(row.getOrDefault("awardTime", "")));
            case "score-desc" -> Comparator.comparing(row -> toDouble(row.get("expectedScore")), Comparator.reverseOrder());
            case "score-asc" -> Comparator.comparing(row -> toDouble(row.get("expectedScore")));
            default -> Comparator.comparing(row -> String.valueOf(row.getOrDefault("uploadTime", "")), Comparator.reverseOrder());
        };
        return rows.stream()
                .filter(row -> "all".equals(normalizedStatus) || normalizedStatus.equals(String.valueOf(row.get("status"))))
                .filter(row -> {
                    if (normalizedKeyword.isBlank()) {
                        return true;
                    }
                    String haystack = List.of("certName", "certFileName", "itemName", "groupName", "levelName", "authorLabel", "detailDesc").stream()
                            .map(key -> String.valueOf(row.getOrDefault(key, "")))
                            .collect(Collectors.joining(" "))
                            .toLowerCase();
                    return haystack.contains(normalizedKeyword);
                })
                .sorted(comparator)
                .toList();
    }

    private Map<String, Object> buildStudentApplicationStats(List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        double approvedTotal = rows.stream()
                .filter(row -> "approved".equals(String.valueOf(row.get("status"))))
                .mapToDouble(row -> {
                    double score = toDouble(row.get("expectedScore"));
                    return "deduct".equals(String.valueOf(row.get("scoreDirection"))) ? -score : score;
                })
                .sum();
        long draftCount = rows.stream()
                .filter(row -> Set.of("draft", "returned", "rejected").contains(String.valueOf(row.get("status"))))
                .count();
        long returnedCount = rows.stream().filter(row -> "returned".equals(String.valueOf(row.get("status")))).count();
        long reviewingCount = rows.stream().filter(row -> "pending".equals(String.valueOf(row.get("status")))).count();
        long committeeReviewingCount = rows.stream()
                .filter(row -> "pending".equals(String.valueOf(row.get("status"))) && "committee_pending".equals(String.valueOf(row.get("reviewStage"))))
                .count();
        long teacherReviewingCount = rows.stream()
                .filter(row -> "pending".equals(String.valueOf(row.get("status"))) && "teacher_pending".equals(String.valueOf(row.get("reviewStage"))))
                .count();
        result.put("approvedTotal", approvedTotal);
        result.put("draftCount", draftCount);
        result.put("returnedCount", returnedCount);
        result.put("reviewingCount", reviewingCount);
        result.put("committeeReviewingCount", committeeReviewingCount);
        result.put("teacherReviewingCount", teacherReviewingCount);
        return result;
    }

    private Map<String, Object> buildStudentApplicationPeriodSummary(List<Map<String, Object>> rows, Map<String, Object> currentWindow) {
        Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            addStudentApplicationSummaryRow(buckets, resolveStudentApplicationBucketKey(row, currentWindow), row);
            addStudentApplicationSummaryRow(groups, resolveStudentApplicationPeriodKey(row), row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buckets", toStudentApplicationSummaryOutput(buckets));
        result.put("groups", toStudentApplicationSummaryOutput(groups));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void addStudentApplicationSummaryRow(Map<String, Map<String, Object>> summaries, String key, Map<String, Object> row) {
        Map<String, Object> summary = summaries.computeIfAbsent(key, ignored -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("total", 0);
            item.put("statusCounts", new LinkedHashMap<String, Integer>());
            return item;
        });
        summary.put("total", ((Integer) summary.get("total")) + 1);
        Map<String, Integer> statusCounts = (Map<String, Integer>) summary.get("statusCounts");
        String status = valueString(row.get("status")).trim().toLowerCase();
        statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStudentApplicationSummaryOutput(Map<String, Map<String, Object>> summaries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : summaries.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("total", entry.getValue().get("total"));
            item.put("statusSummary", buildStudentApplicationStatusSummary((Map<String, Integer>) entry.getValue().get("statusCounts")));
            result.put(entry.getKey(), item);
        }
        return result;
    }

    private List<String> buildStudentApplicationStatusSummary(Map<String, Integer> statusCounts) {
        List<String> result = new ArrayList<>();
        addStudentApplicationStatusSummary(result, statusCounts, "draft", "草稿");
        addStudentApplicationStatusSummary(result, statusCounts, "pending", "待审核");
        addStudentApplicationStatusSummary(result, statusCounts, "approved", "已通过");
        addStudentApplicationStatusSummary(result, statusCounts, "returned", "已打回");
        addStudentApplicationStatusSummary(result, statusCounts, "rejected", "已拒绝");
        return result;
    }

    private void addStudentApplicationStatusSummary(List<String> result, Map<String, Integer> statusCounts, String status, String label) {
        int count = statusCounts.getOrDefault(status, 0);
        if (count > 0) {
            result.add(label + " " + count + " 条");
        }
    }

    private String resolveStudentApplicationPeriodKey(Map<String, Object> row) {
        String batchCode = valueString(row.get("submissionBatchCode")).trim();
        if (!batchCode.isBlank()) {
            return batchCode;
        }
        String academicYear = valueString(row.get("academicYear")).trim();
        String periodTime = valueString(row.get("submissionStartTime")).trim();
        if (periodTime.isBlank()) {
            periodTime = valueString(row.get("uploadTime")).trim();
        }
        return (academicYear.isBlank() ? "unknown" : academicYear) + "-" + (periodTime.isBlank() ? "unknown" : periodTime);
    }

    private String resolveStudentApplicationBucketKey(Map<String, Object> row, Map<String, Object> currentWindow) {
        if (isStudentApplicationCurrentPeriod(row, currentWindow)) {
            return "current";
        }
        if (isStudentApplicationTeacherImportPeriod(row)) {
            return "imported";
        }
        return "history";
    }

    private boolean isStudentApplicationTeacherImportPeriod(Map<String, Object> row) {
        String periodKey = resolveStudentApplicationPeriodKey(row);
        String periodLabel = valueString(row.get("submissionPeriodLabel"));
        String submissionType = valueString(row.get("submissionType"));
        return periodKey.startsWith("teacher-import")
                || periodLabel.contains("教师导入")
                || "teacher_import".equals(submissionType);
    }

    private boolean isStudentApplicationCurrentPeriod(Map<String, Object> row, Map<String, Object> currentWindow) {
        if (currentWindow == null || !Boolean.TRUE.equals(currentWindow.get("open"))) {
            return false;
        }
        String rowBatchCode = valueString(row.get("submissionBatchCode")).trim();
        String currentBatchCode = valueString(currentWindow.get("batchCode")).trim();
        if (!rowBatchCode.isBlank() && !currentBatchCode.isBlank() && rowBatchCode.equals(currentBatchCode)) {
            return true;
        }
        String rowStartTime = valueString(row.get("submissionStartTime")).trim();
        String rowEndTime = valueString(row.get("submissionEndTime")).trim();
        String currentStartTime = valueString(currentWindow.get("startTime")).trim();
        String currentEndTime = valueString(currentWindow.get("endTime")).trim();
        return (!rowStartTime.isBlank() || !rowEndTime.isBlank())
                && rowStartTime.equals(currentStartTime)
                && rowEndTime.equals(currentEndTime);
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private Map<String, Object> buildRecordsPage(List<Map<String, Object>> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int total = rows.size();
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows.subList(fromIndex, toIndex));
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    public List<Map<String, Object>> listReviewApplicationsByStudent(RequestUserContext context, String studentNo, String scoreType, String status,
                                                                      String classId, String startDate, String endDate, String keyword,
                                                                      boolean currentPeriodOnly) {
        String normalizedStudentNo = defaultString(studentNo);
        if (normalizedStudentNo.isBlank()) {
            throw new IllegalArgumentException("学生学号不能为空");
        }
        return listReviewApplications(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly).stream()
                .filter(item -> normalizedStudentNo.equals(defaultString(String.valueOf(item.get("studentNo")))))
                .toList();
    }

    public Map<String, Object> listReviewApplicationsByStudent(RequestUserContext context, String studentNo, String scoreType, String status,
                                                              String classId, String startDate, String endDate, String keyword,
                                                              boolean currentPeriodOnly, int page, int size) {
        List<Map<String, Object>> rows = listReviewApplicationsByStudent(context, studentNo, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly).stream()
                .map(this::toReviewApplicationListMap)
                .toList();
        return buildPage(rows, page, size);
    }

    public Map<String, Object> getReviewApplicationDetail(RequestUserContext context, Long id) {
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!canAccessReviewApplication(context, application)) {
            throw new IllegalArgumentException("无权查看该申请记录");
        }
        return toReviewMap(application, context);
    }

    public List<String> uploadProofImages(RequestUserContext context, List<MultipartFile> files) {
        resolveCurrentStudent(context);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的凭证文件");
        }
        try {
            List<String> paths = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String contentType = defaultString(file.getContentType()).toLowerCase();
                if (!isAllowedMediaType(contentType) || file.getSize() > 50L * 1024 * 1024) {
                    throw new IllegalArgumentException("仅支持上传图片或视频文件");
                }
                if (!hasValidMediaSignature(file, contentType)) {
                    throw new IllegalArgumentException("文件内容与声明的媒体类型不一致");
                }
                String extension = resolveExtension(null, contentType);
                paths.add(proofFileStorageService.upload(file, extension));
            }
            if (paths.isEmpty()) {
                throw new IllegalArgumentException("请选择要上传的凭证文件");
            }
            return paths;
        } catch (IOException exception) {
            throw new IllegalArgumentException("凭证文件保存失败");
        }
    }

    public Resource exportProofImages(RequestUserContext context, String academicYear, String className, String studentNo, String studentNos, List<Long> applicationIds) {
        if (!context.isTeacher() && !context.isAdmin()) {
            throw new IllegalArgumentException("仅老师或管理员可以导出凭证图片");
        }
        List<ExtraScoreApplication> applications = findExportApplications(context, academicYear, className, studentNo, studentNos, applicationIds);
        return buildProofImagesZip(applications);
    }

    private Resource buildProofImagesZip(List<ExtraScoreApplication> applications) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            List<String> skipped = new ArrayList<>();
            Set<String> entryNames = new HashSet<>();
            int exportedCount = 0;
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                for (ExtraScoreApplication application : applications) {
                    List<String> images = parseProofImages(application);
                    for (int index = 0; index < images.size(); index++) {
                        String imagePath = images.get(index);
                        String extension = resolveExtensionFromPath(imagePath);
                        String baseName = sanitizeFilename(defaultString(application.getClassName()))
                                + "+"
                                + sanitizeFilename(defaultString(application.getStudentName()))
                                + "+"
                                + sanitizeFilename(defaultString(application.getItemName()))
                                + "+"
                                + sanitizeFilename(resolveAwardName(application));
                        String entryName = ensureUniqueEntryName(entryNames, baseName + (images.size() > 1 ? "+" + (index + 1) : "") + extension);
                        try (InputStream inputStream = proofFileStorageService.open(imagePath)) {
                            if (inputStream == null) {
                                skipped.add(buildMissingFileLine(application, imagePath));
                                continue;
                            }
                            zip.putNextEntry(new ZipEntry(entryName));
                            inputStream.transferTo(zip);
                            zip.closeEntry();
                        }
                        exportedCount++;
                    }
                }
                if (exportedCount == 0 || !skipped.isEmpty()) {
                    zip.putNextEntry(new ZipEntry("导出说明.txt"));
                    String note = buildExportNote(exportedCount, skipped);
                    zip.write(note.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return new ByteArrayResource(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("导出图片失败");
        }
    }

    public Resource exportMyProofImages(RequestUserContext context, String academicYear, String scoreType) {
        Student student = resolveCurrentStudent(context);
        List<ExtraScoreApplication> applications = findMyExportApplications(student, academicYear, scoreType);
        return buildProofImagesZip(applications);
    }

    public Resource exportMyCertificates(RequestUserContext context, String academicYear, String scoreType) {
        Student student = resolveCurrentStudent(context);
        List<ExtraScoreApplication> applications = findMyExportApplications(student, academicYear, scoreType);
        return buildCertificateZip(applications);
    }

    private Resource buildCertificateZip(List<ExtraScoreApplication> applications) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            List<String> skipped = new ArrayList<>();
            Set<String> entryNames = new HashSet<>();
            int exportedCount = 0;
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                for (ExtraScoreApplication application : applications) {
                    String certificatePath = defaultString(application.getCertFileUrl());
                    if (certificatePath.isBlank()) {
                        continue;
                    }
                    String extension = resolveExtensionFromPath(certificatePath);
                    String baseName = sanitizeFilename(defaultString(application.getClassName()))
                            + "+"
                            + sanitizeFilename(defaultString(application.getStudentName()))
                            + "+"
                            + sanitizeFilename(defaultString(application.getItemName()))
                            + "+"
                            + sanitizeFilename(defaultString(application.getCertName()).isBlank()
                                    ? resolveAwardName(application)
                                    : defaultString(application.getCertName()));
                    String folderName = sanitizeFilename(resolveScoreTypeLabel(application.getScoreType()));
                    String entryName = ensureUniqueEntryName(entryNames, folderName + "/" + baseName + extension);
                    try (InputStream inputStream = proofFileStorageService.open(certificatePath)) {
                        if (inputStream == null) {
                            skipped.add(buildMissingFileLine(application, certificatePath));
                            continue;
                        }
                        zip.putNextEntry(new ZipEntry(entryName));
                        inputStream.transferTo(zip);
                        zip.closeEntry();
                    }
                    exportedCount++;
                }
                if (exportedCount == 0 || !skipped.isEmpty()) {
                    zip.putNextEntry(new ZipEntry("导出说明.txt"));
                    String note = buildCertificateExportNote(exportedCount, skipped);
                    zip.write(note.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return new ByteArrayResource(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("导出证书失败");
        }
    }

    public Resource exportMyExcel(RequestUserContext context, String academicYear, String scoreType) {
        Student student = resolveCurrentStudent(context);
        List<ExtraScoreApplication> applications = findMyExportApplications(student, academicYear, scoreType);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("附加分明细");
            String[] headers = {
                    "学年", "提交周期", "附加分类", "模板", "大类", "小类", "级别", "作者",
                    "证书名称", "获奖时间", "分值", "状态", "当前阶段", "提交类型", "提交时间", "打回原因"
            };
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                headerRow.createCell(index).setCellValue(headers[index]);
            }
            for (int rowIndex = 0; rowIndex < applications.size(); rowIndex++) {
                ExtraScoreApplication item = applications.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue(resolveApplicationAcademicYear(item));
                row.createCell(1).setCellValue(defaultString(item.getSubmissionPeriodLabel()));
                row.createCell(2).setCellValue(resolveScoreTypeLabel(item.getScoreType()));
                row.createCell(3).setCellValue(defaultString(item.getTemplateName()));
                row.createCell(4).setCellValue(defaultString(item.getGroupName()));
                row.createCell(5).setCellValue(defaultString(item.getItemName()));
                row.createCell(6).setCellValue(defaultString(item.getLevelName()));
                row.createCell(7).setCellValue(defaultString(item.getAuthorLabel()));
                row.createCell(8).setCellValue(defaultString(item.getCertName()));
                row.createCell(9).setCellValue(item.getAwardTime() == null ? "" : item.getAwardTime().toString());
                row.createCell(10).setCellValue(resolveSignedExpectedScore(item));
                row.createCell(11).setCellValue(resolveReviewStatusLabel(item.getStatus()));
                row.createCell(12).setCellValue(resolveReviewStageLabel(item.getReviewStage()));
                row.createCell(13).setCellValue("supplement".equals(item.getSubmissionType()) ? "补充" : "正常提交");
                row.createCell(14).setCellValue(item.getCreatedAt() == null ? "" : item.getCreatedAt().toString().replace('T', ' '));
                row.createCell(15).setCellValue(defaultString(item.getReturnReason()));
            }
            for (int index = 0; index < headers.length; index++) {
                sheet.autoSizeColumn(index);
            }
            workbook.write(outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("导出附加分明细失败");
        }
    }

    public Resource exportTeacherImportTemplate(RequestUserContext context, String scoreType, String className) {
        String normalizedType = normalizeTeacherImportScoreType(scoreType);
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以使用导入示例");
        }
        TeacherImportTemplateContext templateContext = loadTeacherImportTemplateContext(context, normalizedType);
        TeacherImportCategory exampleCategory = templateContext.categories().get(0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(resolveScoreTypeLabel(normalizedType) + "导入示例");
            Sheet categorySheet = workbook.createSheet("分类参考");
            String[] headers = {"序号", "学号", "姓名", "申请大类", "申请小类", "加分值", "凭证图片", "说明"};
            String[] categoryHeaders = {"申请大类", "申请小类", "计分方向", "小类说明"};

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFont(headerFont);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

            writeHeaderRow(sheet, headers, headerStyle);
            writeHeaderRow(categorySheet, categoryHeaders, headerStyle);

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue(1);
            Cell studentNoCell = exampleRow.createCell(1);
            studentNoCell.setCellStyle(textStyle);
            studentNoCell.setCellValue("2023000001");
            exampleRow.createCell(2).setCellValue("张三");
            exampleRow.createCell(3).setCellValue(exampleCategory.group().getGroupName());
            exampleRow.createCell(4).setCellValue(exampleCategory.item().getItemName());
            exampleRow.createCell(5).setCellValue(0.5);
            exampleRow.createCell(6).setCellValue("可在本列插入图片");
            exampleRow.createCell(7).setCellValue("示例：请替换为真实学生信息和加分说明");
            sheet.setColumnWidth(6, 18 * 256);

            int rowNumber = 1;
            for (TeacherImportCategory category : templateContext.categories()) {
                Row row = categorySheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(defaultString(category.group().getGroupName()));
                row.createCell(1).setCellValue(defaultString(category.item().getItemName()));
                row.createCell(2).setCellValue("deduct".equals(normalizeScoreDirection(category.item().getScoreDirection())) ? "减分" : "加分");
                row.createCell(3).setCellValue(defaultString(category.item().getDescription()));
            }

            for (int index = 0; index < headers.length; index++) {
                sheet.autoSizeColumn(index);
            }
            sheet.setColumnWidth(6, Math.max(sheet.getColumnWidth(6), 18 * 256));
            for (int index = 0; index < categoryHeaders.length; index++) {
                categorySheet.autoSizeColumn(index);
            }
            workbook.write(outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("生成导入示例失败");
        }
    }

    @Transactional
    public Map<String, Object> importTeacherExtraScore(RequestUserContext context, String scoreType, String className, MultipartFile file) {
        String normalizedType = normalizeTeacherImportScoreType(scoreType);
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以导入附加分");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 Excel 文件");
        }

        List<Student> scopedStudents = findTeacherImportStudents(context, className);
        TeacherImportTemplateContext templateContext = loadTeacherImportTemplateContext(context, normalizedType);
        Map<String, Student> studentMap = scopedStudents.stream()
                .filter(student -> student.getStudentNo() != null && !student.getStudentNo().isBlank())
                .collect(Collectors.toMap(student -> defaultString(student.getStudentNo()), student -> student, (left, right) -> left, LinkedHashMap::new));
        if (studentMap.isEmpty()) {
            throw new IllegalArgumentException("当前导入范围内没有可导入学生");
        }

        String batchCode = "teacher-import-" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        String reviewerName = resolveReviewerName(context);
        List<ExtraScoreApplication> applications = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        int skippedCount = 0;

        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 中没有可读取的工作表");
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = readTeacherImportHeaders(sheet.getRow(0), formatter);
            int studentNoIndex = resolveHeaderIndex(headers, "学号", 1);
            int studentNameIndex = resolveHeaderIndex(headers, "姓名", 2);
            int classNameIndex = resolveHeaderIndex(headers, "班级", -1);
            int groupNameIndex = resolveHeaderIndex(headers, List.of("申请大类", "大类"), 3);
            int itemNameIndex = resolveHeaderIndex(headers, List.of("申请小类", "小类", "申请分类"), 4);
            int scoreIndex = resolveHeaderIndex(headers, "加分值", 5);
            int proofImageIndex = resolveHeaderIndex(headers, List.of("凭证图片", "证书图片", "证明图片", "图片"), -1);
            int remarkIndex = resolveHeaderIndex(headers, "说明", proofImageIndex >= 0 ? 7 : 6);
            Map<Integer, List<String>> proofImagesByRow = extractTeacherImportProofImages(sheet, proofImageIndex);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String studentNo = readCellText(row, studentNoIndex, formatter);
                String studentName = readCellText(row, studentNameIndex, formatter);
                String rowClassName = readCellText(row, classNameIndex, formatter);
                String groupName = readCellText(row, groupNameIndex, formatter);
                String itemName = readCellText(row, itemNameIndex, formatter);
                String remark = readCellText(row, remarkIndex, formatter);
                String proofImageText = readCellText(row, proofImageIndex, formatter);
                List<String> proofImagePaths = new ArrayList<>(proofImagesByRow.getOrDefault(rowIndex, List.of()));
                if (isProofImageReference(proofImageText)) {
                    proofImagePaths.add(proofImageText);
                }
                Double score;
                try {
                    score = readPositiveScore(row.getCell(scoreIndex), formatter);
                } catch (IllegalArgumentException exception) {
                    failed.add(buildTeacherImportFailure(rowIndex + 1, studentNo, studentName, exception.getMessage()));
                    continue;
                }

                if (studentNo.isBlank() && studentName.isBlank() && score == null) {
                    skippedCount++;
                    continue;
                }
                if (score == null) {
                    skippedCount++;
                    continue;
                }
                TeacherImportCategory category;
                try {
                    category = resolveTeacherImportCategory(templateContext, groupName, itemName);
                } catch (IllegalArgumentException exception) {
                    failed.add(buildTeacherImportFailure(rowIndex + 1, studentNo, studentName, exception.getMessage()));
                    continue;
                }
                if (studentNo.isBlank()) {
                    failed.add(buildTeacherImportFailure(rowIndex + 1, studentNo, studentName, "学号不能为空"));
                    continue;
                }
                Student student = studentMap.get(studentNo);
                if (student == null) {
                    failed.add(buildTeacherImportFailure(rowIndex + 1, studentNo, studentName, "学生不在当前老师负责范围内"));
                    continue;
                }
                if (!studentName.isBlank() && !studentName.equals(defaultString(student.getName()))) {
                    failed.add(buildTeacherImportFailure(rowIndex + 1, studentNo, studentName, "姓名与系统学生信息不一致"));
                    continue;
                }
                if (!rowClassName.isBlank() && !rowClassName.equals(defaultString(student.getClassName()))) {
                    failed.add(buildTeacherImportFailure(rowIndex + 1, studentNo, studentName, "班级与系统学生信息不一致"));
                    continue;
                }

                ExtraScoreApplication application = buildTeacherImportApplication(
                        context,
                        student,
                        category,
                        score,
                        remark,
                        proofImagePaths,
                        batchCode,
                        now,
                        reviewerName
                );
                applications.add(application);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 Excel 文件失败");
        }

        List<ExtraScoreApplication> savedApplications = applications.isEmpty()
                ? List.of()
                : applicationRepository.saveAll(applications);
        for (ExtraScoreApplication application : savedApplications) {
            applicationEventService.record(
                    application,
                    "TEACHER_IMPORTED",
                    null,
                    context,
                    resolveReviewerName(context),
                    "教师导入附加分",
                    true
            );
        }
        if (savedApplications.isEmpty() && failed.isEmpty()) {
            throw new IllegalArgumentException("没有可导入的加分记录，请在“加分值”列填写分数");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", savedApplications.size() + failed.size() + skippedCount);
        result.put("successCount", savedApplications.size());
        result.put("failedCount", failed.size());
        result.put("skippedCount", skippedCount);
        result.put("failed", failed);
        result.put("records", savedApplications.stream().map(application -> toReviewMap(application, context)).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> committeeReview(RequestUserContext context, Long id, ExtraScoreApplicationReviewRequest request) {
        if (!context.isClassCommittee()) {
            throw new IllegalArgumentException("仅班委可以执行初审");
        }
        Long teacherId = resolveTeacherId(context);
        String className = defaultString(context.getClassName());
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!teacherId.equals(application.getTeacherId()) || !className.equals(defaultString(application.getClassName()))) {
            throw new IllegalArgumentException("无权审核该申请");
        }
        if (!"pending".equals(application.getStatus()) || !"committee_pending".equals(application.getReviewStage())) {
            throw new IllegalArgumentException("当前申请不在班委初审阶段");
        }
        if ("pending".equals(application.getWithdrawalStatus())) {
            throw new IllegalArgumentException("学生已申请撤回，请先处理撤回申请");
        }
        if (!canCommitteeReviewApplication(context, application)) {
            throw new IllegalArgumentException("当前班级有多个班委，不能审核自己的申请，请由其他班委处理");
        }

        String action = normalizeReviewAction(request.getAction());
        String comment = defaultString(request.getComment());
        String previousStatus = application.getStatus();
        applyCommitteeReviewResult(application, context, action, comment);
        ExtraScoreApplication saved = applicationRepository.save(application);
        applicationEventService.record(
                saved,
                "approve".equals(action) ? "COMMITTEE_APPROVED" : "COMMITTEE_RETURNED",
                previousStatus,
                context,
                resolveReviewerName(context),
                saved.getReturnReason() == null ? comment : saved.getReturnReason(),
                false
        );
        settleRejectedPackageIfReady(saved);
        return toReviewMap(applicationRepository.findById(id).orElse(saved), context);
    }

    @Transactional
    public Map<String, Object> reviewWithdrawalRequest(RequestUserContext context, Long id, ExtraScoreApplicationReviewRequest request) {
        if (!context.isClassCommittee() && !context.isTeacher()) {
            throw new IllegalArgumentException("仅班委或无班委班级的老师可以处理撤回申请");
        }
        Long teacherId = resolveTeacherId(context);
        String className = defaultString(context.getClassName());
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (context.isClassCommittee()
                && (!teacherId.equals(application.getTeacherId()) || !className.equals(defaultString(application.getClassName())))) {
            throw new IllegalArgumentException("无权处理该撤回申请");
        }
        if (context.isTeacher() && !teacherId.equals(application.getTeacherId())) {
            throw new IllegalArgumentException("无权处理该撤回申请");
        }
        if (!"pending".equals(application.getWithdrawalStatus())) {
            throw new IllegalArgumentException("当前没有待处理的撤回申请");
        }
        if (!canHandleWithdrawal(context, application)) {
            if (context.isTeacher()) {
                throw new IllegalArgumentException("当前班级已设置班委，请由班委处理撤回申请");
            }
            throw new IllegalArgumentException("当前班级有多个班委，不能处理自己的撤回申请，请由其他班委处理");
        }

        String action = normalizeReviewAction(request.getAction());
        String comment = defaultString(request.getComment());
        LocalDateTime reviewedAt = LocalDateTime.now();
        String reviewerName = resolveReviewerName(context);
        if ("approve".equals(action)) {
            List<ExtraScoreApplication> packageRecords = findSameSubmissionPackage(application).stream()
                    .filter(item -> "pending".equals(item.getStatus()))
                    .toList();
            for (ExtraScoreApplication item : packageRecords) {
                String previousStatus = item.getStatus();
                resetDraftWorkflow(item);
                item.setWithdrawalStatus("approved");
                item.setWithdrawalReviewedAt(reviewedAt);
                item.setWithdrawalReviewerId(context.getUserId());
                item.setWithdrawalReviewerName(reviewerName);
                item.setWithdrawalComment(comment);
                item.setReturnReason("班委同意撤回，待补充后重新提交");
                applicationEventService.record(
                        item,
                        "WITHDRAWN",
                        previousStatus,
                        context,
                        reviewerName,
                        comment,
                        false
                );
            }
            applicationRepository.saveAll(packageRecords);
            return toReviewMap(applicationRepository.findById(id).orElse(application), context);
        }
        if (comment.isBlank()) {
            throw new IllegalArgumentException("拒绝撤回时请填写原因");
        }
        List<ExtraScoreApplication> packageRecords = findSameSubmissionPackage(application).stream()
                .filter(item -> "pending".equals(item.getStatus()))
                .toList();
        for (ExtraScoreApplication item : packageRecords) {
            item.setWithdrawalStatus("rejected");
            item.setWithdrawalReviewedAt(reviewedAt);
            item.setWithdrawalReviewerId(context.getUserId());
            item.setWithdrawalReviewerName(reviewerName);
            item.setWithdrawalComment(comment);
            applicationEventService.record(
                    item,
                    "WITHDRAWAL_REJECTED",
                    item.getStatus(),
                    context,
                    reviewerName,
                    comment,
                    false
            );
        }
        applicationRepository.saveAll(packageRecords);
        return toReviewMap(applicationRepository.findById(id).orElse(application), context);
    }

    @Transactional
    public Map<String, Object> teacherReview(RequestUserContext context, Long id, ExtraScoreApplicationReviewRequest request) {
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以执行终审");
        }
        ExtraScoreApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
        if (!resolveTeacherId(context).equals(application.getTeacherId())) {
            throw new IllegalArgumentException("无权审核该申请");
        }
        if (!"pending".equals(application.getStatus())) {
            throw new IllegalArgumentException("当前申请已完成审核");
        }
        if ("pending".equals(application.getWithdrawalStatus())) {
            throw new IllegalArgumentException("学生已申请撤回，请先由班委处理撤回申请");
        }
        if (!canTeacherReviewApplication(context, application)) {
            throw new IllegalArgumentException("当前申请尚未完成班委初审，老师不能直接终审");
        }

        String action = normalizeReviewAction(request.getAction());
        String comment = defaultString(request.getComment());
        String previousStatus = application.getStatus();
        applyTeacherReviewResult(application, context, action, comment);
        ExtraScoreApplication saved = applicationRepository.save(application);
        applicationEventService.record(
                saved,
                "approve".equals(action) ? "TEACHER_APPROVED" : "TEACHER_RETURNED",
                previousStatus,
                context,
                resolveReviewerName(context),
                saved.getReturnReason() == null ? comment : saved.getReturnReason(),
                false
        );
        settleRejectedPackageIfReady(saved);
        return toReviewMap(applicationRepository.findById(id).orElse(saved), context);
    }

    public Map<String, Object> batchApproveReview(RequestUserContext context, ExtraScoreApplicationBatchReviewRequest request) {
        if (!context.isClassCommittee() && !context.isTeacher()) {
            throw new IllegalArgumentException("当前角色无权批量审核");
        }
        List<Long> ids = request == null || request.getApplicationIds() == null
                ? List.of()
                : request.getApplicationIds().stream()
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要批量通过的申请");
        }

        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                ExtraScoreApplication application = applicationRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("申请记录不存在"));
                ExtraScoreApplicationReviewRequest reviewRequest = new ExtraScoreApplicationReviewRequest();
                reviewRequest.setAction("approve");
                reviewRequest.setComment("");
                reviewRequest.setReviewScore(application.getExpectedScore());
                Map<String, Object> result = context.isClassCommittee()
                        ? committeeReview(context, id, reviewRequest)
                        : teacherReview(context, id, reviewRequest);
                approved.add(result);
            } catch (IllegalArgumentException exception) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("id", id);
                failure.put("message", exception.getMessage());
                failed.add(failure);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", ids.size());
        result.put("successCount", approved.size());
        result.put("failedCount", failed.size());
        result.put("approved", approved);
        result.put("failed", failed);
        return result;
    }

    @Transactional
    public Map<String, Object> forceCurrentClassToTeacherStage(RequestUserContext context) {
        if (!context.isClassCommittee()) {
            throw new IllegalArgumentException("仅班委可以强制进入老师审核阶段");
        }
        Long teacherId = resolveTeacherId(context);
        String className = defaultString(context.getClassName());
        if (className.isBlank()) {
            throw new IllegalArgumentException("当前班委未配置班级信息");
        }

        List<Long> currentWindowIds = extraScoreSubmissionWindowService.findCurrentReviewWindowIds(context);
        if (currentWindowIds.isEmpty()) {
            throw new IllegalArgumentException("当前班级没有开放中的提交周期");
        }

        List<ExtraScoreApplication> applications = applicationRepository.findBySubmissionWindowIdIn(currentWindowIds).stream()
                .filter(item -> teacherId.equals(item.getTeacherId()))
                .filter(item -> className.equals(defaultString(item.getClassName())))
                .filter(item -> "pending".equals(item.getStatus()))
                .filter(item -> "committee_pending".equals(item.getReviewStage()))
                .toList();

        if (applications.isEmpty()) {
            throw new IllegalArgumentException("当前班级没有可推进到老师审核的申请");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ExtraScoreApplication application : applications) {
            String previousStatus = application.getStatus();
            application.setCommitteeStatus("forced");
            application.setCommitteeComment("班委强制进入老师审核阶段");
            application.setCommitteeReviewerId(context.getUserId());
            application.setCommitteeReviewerName(resolveReviewerName(context));
            application.setCommitteeReviewedAt(now);
            application.setReviewStage("teacher_pending");
            application.setStatus("pending");
            application.setReturnReason(null);
            applicationEventService.record(
                    application,
                    "COMMITTEE_FORCED",
                    previousStatus,
                    context,
                    resolveReviewerName(context),
                    "班委强制进入老师审核阶段",
                    false
            );
        }
        applicationRepository.saveAll(applications);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("className", className);
        result.put("advancedCount", applications.size());
        result.put("message", String.format("已将 %d 条申请推进到老师审核阶段", applications.size()));
        return result;
    }

    private void applyRequest(ExtraScoreApplication application, Student student, ExtraScoreTemplate template, ExtraScoreApplicationRequest request) {
        if (request.getItemId() == null) {
            throw new IllegalArgumentException("请选择小类");
        }
        if (request.getLevelRuleId() == null) {
            throw new IllegalArgumentException("请选择级别规则");
        }
        if (request.getAuthorRuleId() == null) {
            throw new IllegalArgumentException("请选择作者规则");
        }
        if (defaultString(request.getCertName()).isBlank()) {
            throw new IllegalArgumentException("请填写证书名称");
        }

        ExtraScoreTemplateItem item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("所选小类不存在"));
        if (!template.getId().equals(item.getTemplateId())) {
            throw new IllegalArgumentException("所选小类不属于当前模板");
        }

        ExtraScoreTemplateItemLevelRule levelRule = levelRuleRepository.findById(request.getLevelRuleId())
                .orElseThrow(() -> new IllegalArgumentException("所选级别规则不存在"));
        if (!item.getId().equals(levelRule.getItemId())) {
            throw new IllegalArgumentException("所选级别规则不属于当前小类");
        }

        ExtraScoreTemplateItemAuthorRule authorRule = authorRuleRepository.findById(request.getAuthorRuleId())
                .orElseThrow(() -> new IllegalArgumentException("所选作者规则不存在"));
        if (!levelRule.getId().equals(authorRule.getLevelRuleId())) {
            throw new IllegalArgumentException("所选作者规则不属于当前级别");
        }

        double expectedScore;
        if ("fixed".equalsIgnoreCase(authorRule.getMode())) {
            expectedScore = authorRule.getFixedScore() == null ? 0D : authorRule.getFixedScore();
        } else {
            expectedScore = levelRule.getScore() * (authorRule.getRatio() == null ? 0D : authorRule.getRatio()) / 100D;
        }
        if (expectedScore <= 0) {
            throw new IllegalArgumentException("计算后的分值必须大于 0");
        }

        application.setStudentId(student.getId());
        application.setStudentNo(student.getStudentNo());
        application.setStudentName(student.getName());
        application.setClassName(student.getClassName());
        application.setTeacherId(student.getTeacherId());
        application.setAcademicYear(extraScoreSubmissionWindowService.resolveCurrentAcademicYear());
        application.setScoreType(template.getScoreType());
        application.setTemplateId(template.getId());
        application.setTemplateName(template.getName());
        application.setGroupName(resolveGroupName(template, item.getGroupId()));
        application.setItemId(item.getId());
        application.setItemName(item.getItemName());
        application.setScoreDirection(normalizeScoreDirection(item.getScoreDirection()));
        application.setLevelRuleId(levelRule.getId());
        application.setLevelName(levelRule.getLevelName());
        application.setAuthorRuleId(authorRule.getId());
        application.setAuthorLabel(authorRule.getAuthorLabel());
        application.setBaseScore(levelRule.getScore());
        application.setAuthorRatio(authorRule.getRatio());
        List<String> certFileNames = normalizeStringList(request.getCertFileNames());
        List<String> certFileUrls = normalizeStringList(request.getCertFileUrls());
        if (certFileNames.isEmpty() && !defaultString(request.getCertFileName()).isBlank()) {
            certFileNames = List.of(defaultString(request.getCertFileName()));
        }
        if (certFileUrls.isEmpty() && !defaultString(request.getCertFileUrl()).isBlank()) {
            certFileUrls = List.of(defaultString(request.getCertFileUrl()));
        }
        application.setCertFileName(String.join("||", certFileNames));
        application.setCertFileUrl(String.join("||", certFileUrls));
        application.setCertName(defaultString(request.getCertName()));
        application.setDetailDesc(defaultString(request.getDetailDesc()));
        application.setAwardTime(parseDate(request.getAwardTime()));
        application.setExpectedScore(expectedScore);
        application.setProofText(defaultString(request.getProofText()));
        application.setProofImages(String.join("||", request.getProofImages() == null ? List.of() : request.getProofImages()));
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::defaultString)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> splitStoredList(String value) {
        String text = defaultString(value);
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\|\\|")).stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private String firstStoredValue(String value) {
        List<String> values = splitStoredList(value);
        return values.isEmpty() ? "" : values.get(0);
    }

    private List<String> buildCertificateThumbnailUrls(ExtraScoreApplication application) {
        List<String> certUrls = splitStoredList(application.getCertFileUrl());
        if (!certUrls.isEmpty()) {
            return certUrls;
        }
        return splitStoredList(application.getProofImages());
    }

    private void applySubmissionWindow(ExtraScoreApplication application, ExtraScoreSubmissionWindow window) {
        if (window == null) {
            return;
        }
        application.setAcademicYear(defaultString(window.getAcademicYear()));
        application.setSubmissionWindowId(window.getId());
        application.setSubmissionPeriodId(window.getPeriodId());
        application.setSubmissionBatchCode(defaultString(window.getBatchCode()));
        application.setSubmissionPeriodLabel(extraScoreSubmissionWindowService.resolvePeriodLabel(window));
        application.setSubmissionStartTime(window.getStartTime());
        application.setSubmissionEndTime(window.getEndTime());
    }

    private Map<String, Object> toMap(ExtraScoreApplication application) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("studentId", application.getStudentId());
        result.put("studentNo", application.getStudentNo());
        result.put("studentName", application.getStudentName());
        result.put("className", application.getClassName());
        result.put("teacherId", application.getTeacherId());
        result.put("academicYear", resolveApplicationAcademicYear(application));
        result.put("submissionWindowId", application.getSubmissionWindowId());
        result.put("submissionPeriodId", application.getSubmissionPeriodId());
        result.put("submissionBatchCode", application.getSubmissionBatchCode());
        result.put("submissionPeriodLabel", application.getSubmissionPeriodLabel());
        result.put("submissionStartTime", application.getSubmissionStartTime());
        result.put("submissionEndTime", application.getSubmissionEndTime());
        result.put("scoreType", application.getScoreType());
        result.put("templateId", application.getTemplateId());
        result.put("templateName", application.getTemplateName());
        result.put("groupName", application.getGroupName());
        result.put("itemId", application.getItemId());
        result.put("itemName", application.getItemName());
        result.put("title", application.getItemName());
        result.put("scoreDirection", normalizeScoreDirection(application.getScoreDirection()));
        result.put("levelRuleId", application.getLevelRuleId());
        result.put("levelName", application.getLevelName());
        result.put("authorRuleId", application.getAuthorRuleId());
        result.put("authorLabel", application.getAuthorLabel());
        result.put("baseScore", application.getBaseScore());
        result.put("authorRatio", application.getAuthorRatio());
        result.put("certFileName", firstStoredValue(application.getCertFileName()));
        result.put("certFileUrl", firstStoredValue(application.getCertFileUrl()));
        result.put("certFileNames", splitStoredList(application.getCertFileName()));
        result.put("certFileUrls", splitStoredList(application.getCertFileUrl()));
        result.put("thumbnailUrls", buildCertificateThumbnailUrls(application));
        result.put("certName", application.getCertName());
        result.put("detailDesc", application.getDetailDesc());
        result.put("awardTime", application.getAwardTime());
        result.put("date", application.getAwardTime());
        result.put("expectedScore", application.getExpectedScore());
        result.put("score", application.getExpectedScore());
        result.put("proofText", application.getProofText());
        result.put("proofImages", application.getProofImages() == null || application.getProofImages().isBlank()
                ? List.of()
                : List.of(application.getProofImages().split("\\|\\|")));
        result.put("proofMedia", buildProofMedia(application));
        result.put("status", application.getStatus());
        result.put("reviewStage", application.getReviewStage());
        result.put("reviewStageLabel", resolveReviewStageLabel(application.getReviewStage()));
        result.put("committeeStatus", application.getCommitteeStatus());
        result.put("committeeStatusLabel", resolveReviewStatusLabel(application.getCommitteeStatus()));
        result.put("committeeComment", application.getCommitteeComment());
        result.put("committeeReviewerId", application.getCommitteeReviewerId());
        result.put("committeeReviewerName", application.getCommitteeReviewerName());
        result.put("committeeReviewedAt", application.getCommitteeReviewedAt());
        result.put("teacherStatus", application.getTeacherStatus());
        result.put("teacherStatusLabel", resolveReviewStatusLabel(application.getTeacherStatus()));
        result.put("teacherComment", application.getTeacherComment());
        result.put("teacherReviewerId", application.getTeacherReviewerId());
        result.put("teacherReviewerName", application.getTeacherReviewerName());
        result.put("teacherReviewedAt", application.getTeacherReviewedAt());
        result.put("returnReason", application.getReturnReason());
        result.put("packageReturned", isPackageReturned(application));
        result.put("recordReviewResult", resolveRecordReviewResult(application));
        result.put("recordReviewResultLabel", resolveRecordReviewResultLabel(application));
        result.put("withdrawalStatus", application.getWithdrawalStatus());
        result.put("withdrawalRequestedAt", application.getWithdrawalRequestedAt());
        result.put("withdrawalReviewedAt", application.getWithdrawalReviewedAt());
        result.put("withdrawalReviewerName", application.getWithdrawalReviewerName());
        result.put("withdrawalComment", application.getWithdrawalComment());
        result.put("submissionType", application.getSubmissionType() == null ? "normal" : application.getSubmissionType());
        result.put("submissionTypeLabel", resolveSubmissionTypeLabel(application.getSubmissionType()));
        result.put("uploadTime", application.getCreatedAt());
        result.put("updatedAt", application.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toReviewMap(ExtraScoreApplication application, RequestUserContext context) {
        return toReviewMap(application, context, new LinkedHashMap<>());
    }

    private Map<String, Object> toReviewMap(
            ExtraScoreApplication application,
            RequestUserContext context,
            Map<String, Long> committeeCountsByClass
    ) {
        Map<String, Object> result = toMap(application);
        String className = defaultString(application.getClassName());
        long committeeCount = committeeCountsByClass.computeIfAbsent(
                className,
                this::countClassCommitteeMembers
        );
        boolean teacherActionable = isTeacherReviewActionable(application, committeeCount);
        result.put("canCommitteeReview", canCommitteeReviewApplication(context, application));
        result.put("canReviewWithdrawal", canHandleWithdrawal(context, application));
        result.put("canTeacherReview", canTeacherReviewApplication(context, application, teacherActionable));
        result.put("teacherActionable", teacherActionable);
        result.put("teacherWithdrawalActionable", isTeacherWithdrawalActionable(application, committeeCount));
        result.put("readOnly", context.isAdmin());
        result.put("aiSuggestionJson", application.getAiSuggestionJson());
        result.put("aiReviewedAt", application.getAiReviewedAt());
        return result;
    }

    private Map<String, Object> toReviewApplicationListMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        copyKeys(result, source,
                "id",
                "studentNo",
                "studentName",
                "className",
                "templateName",
                "scoreType",
                "groupName",
                "itemName",
                "scoreDirection",
                "levelName",
                "authorLabel",
                "expectedScore",
                "certName",
                "certFileName",
                "certFileUrl",
                "certFileNames",
                "certFileUrls",
                "thumbnailUrls",
                "awardTime",
                "status",
                "statusLabel",
                "reviewStage",
                "reviewStageLabel",
                "committeeStatus",
                "committeeStatusLabel",
                "committeeReviewerName",
                "committeeReviewedAt",
                "committeeComment",
                "withdrawalStatus",
                "canReviewWithdrawal",
                "packageReturned",
                "recordReviewResult",
                "recordReviewResultLabel",
                "submissionPeriodLabel",
                "submissionStartTime",
                "submissionEndTime",
                "uploadTime",
                "canCommitteeReview",
                "canTeacherReview");
        return result;
    }

    boolean canAccessReviewApplication(RequestUserContext context, ExtraScoreApplication application) {
        if (context.isAdmin()) {
            return true;
        }
        if (context.isTeacher()) {
            return resolveTeacherId(context).equals(application.getTeacherId());
        }
        if (context.isClassCommittee()) {
            Long teacherId = resolveTeacherId(context);
            String className = defaultString(context.getClassName());
            return teacherId.equals(application.getTeacherId()) && className.equals(defaultString(application.getClassName()));
        }
        return false;
    }

    private void copyKeys(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            target.put(key, source.get(key));
        }
    }

    private boolean canCommitteeReviewApplication(RequestUserContext context, ExtraScoreApplication application) {
        if (!context.isClassCommittee()
                || !"pending".equals(application.getStatus())
                || !"committee_pending".equals(application.getReviewStage())) {
            return false;
        }
        Long teacherId = resolveTeacherId(context);
        String className = defaultString(context.getClassName());
        if (!teacherId.equals(application.getTeacherId()) || !className.equals(defaultString(application.getClassName()))) {
            return false;
        }
        if (!isOwnApplication(context, application)) {
            return true;
        }
        return countClassCommitteeMembers(className) <= 1;
    }

    private boolean isTeacherReviewActionable(ExtraScoreApplication application) {
        if (!"pending".equals(application.getStatus())
                || !"pending".equals(defaultString(application.getTeacherStatus()))) {
            return false;
        }
        String reviewStage = defaultString(application.getReviewStage());
        if ("teacher_pending".equals(reviewStage)) {
            return true;
        }
        return "committee_pending".equals(reviewStage)
                && countClassCommitteeMembers(application.getClassName()) == 0;
    }

    private boolean isTeacherReviewActionable(ExtraScoreApplication application, long committeeCount) {
        if (!"pending".equals(application.getStatus())
                || !"pending".equals(defaultString(application.getTeacherStatus()))) {
            return false;
        }
        String reviewStage = defaultString(application.getReviewStage());
        if ("teacher_pending".equals(reviewStage)) {
            return true;
        }
        return "committee_pending".equals(reviewStage)
                && committeeCount == 0;
    }

    private boolean canTeacherReviewApplication(RequestUserContext context, ExtraScoreApplication application) {
        return canTeacherReviewApplication(context, application, isTeacherReviewActionable(application));
    }

    private boolean canTeacherReviewApplication(
            RequestUserContext context,
            ExtraScoreApplication application,
            boolean teacherActionable
    ) {
        return teacherActionable
                && context.isTeacher()
                && resolveTeacherId(context).equals(application.getTeacherId());
    }

    private boolean isTeacherWithdrawalActionable(ExtraScoreApplication application) {
        return "pending".equals(application.getWithdrawalStatus())
                && countClassCommitteeMembers(application.getClassName()) == 0;
    }

    private boolean isTeacherWithdrawalActionable(ExtraScoreApplication application, long committeeCount) {
        return "pending".equals(application.getWithdrawalStatus())
                && committeeCount == 0;
    }

    private boolean canCommitteeHandleWithdrawal(RequestUserContext context, ExtraScoreApplication application) {
        if (!context.isClassCommittee() || !"pending".equals(application.getWithdrawalStatus())) {
            return false;
        }
        Long teacherId = resolveTeacherId(context);
        String className = defaultString(context.getClassName());
        if (!teacherId.equals(application.getTeacherId()) || !className.equals(defaultString(application.getClassName()))) {
            return false;
        }
        if (!isOwnApplication(context, application)) {
            return true;
        }
        return countClassCommitteeMembers(className) <= 1;
    }

    private boolean canTeacherHandleWithdrawal(RequestUserContext context, ExtraScoreApplication application) {
        if (!context.isTeacher() || !"pending".equals(application.getWithdrawalStatus())) {
            return false;
        }
        Long teacherId = resolveTeacherId(context);
        if (!teacherId.equals(application.getTeacherId())) {
            return false;
        }
        return countClassCommitteeMembers(application.getClassName()) == 0;
    }

    private boolean canHandleWithdrawal(RequestUserContext context, ExtraScoreApplication application) {
        return canCommitteeHandleWithdrawal(context, application)
                || canTeacherHandleWithdrawal(context, application);
    }

    private boolean isOwnApplication(RequestUserContext context, ExtraScoreApplication application) {
        String contextStudentNo = defaultString(context.getStudentNo());
        if (!contextStudentNo.isBlank() && contextStudentNo.equals(defaultString(application.getStudentNo()))) {
            return true;
        }
        if (context.getUserId() == null || application.getStudentId() == null) {
            return false;
        }
        return studentRepository.findById(application.getStudentId())
                .map(student -> context.getUserId().equals(student.getUserId()))
                .orElse(false);
    }

    private long countClassCommitteeMembers(String className) {
        String normalizedClassName = defaultString(className);
        if (normalizedClassName.isBlank()) {
            return 0;
        }
        Optional<Role> committeeRole = roleRepository.findByName("CLASS_COMMITTEE");
        if (committeeRole.isEmpty()) {
            return 0;
        }
        List<User> committeeUsers = userRepository.findByRoleId(committeeRole.get().getId());
        Set<Long> committeeUserIds = new HashSet<>();
        Set<String> committeeStudentNos = new HashSet<>();
        for (User user : committeeUsers) {
            if (user.getId() != null) {
                committeeUserIds.add(user.getId());
            }
            if (user.getStudentNo() != null && !user.getStudentNo().isBlank()) {
                committeeStudentNos.add(user.getStudentNo());
            }
        }
        return studentRepository.findByClassName(normalizedClassName).stream()
                .filter(student -> (student.getUserId() != null && committeeUserIds.contains(student.getUserId()))
                        || (student.getStudentNo() != null && committeeStudentNos.contains(student.getStudentNo())))
                .count();
    }

    private Map<String, Object> toStudentListMap(ExtraScoreApplication application) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("academicYear", resolveApplicationAcademicYear(application));
        result.put("submissionBatchCode", application.getSubmissionBatchCode());
        result.put("submissionPeriodLabel", application.getSubmissionPeriodLabel());
        result.put("submissionStartTime", application.getSubmissionStartTime());
        result.put("submissionEndTime", application.getSubmissionEndTime());
        result.put("scoreType", application.getScoreType());
        result.put("groupName", application.getGroupName());
        result.put("itemName", application.getItemName());
        result.put("levelName", application.getLevelName());
        result.put("authorLabel", application.getAuthorLabel());
        result.put("certFileName", firstStoredValue(application.getCertFileName()));
        result.put("certFileUrl", firstStoredValue(application.getCertFileUrl()));
        result.put("certFileNames", splitStoredList(application.getCertFileName()));
        result.put("certFileUrls", splitStoredList(application.getCertFileUrl()));
        result.put("thumbnailUrls", buildCertificateThumbnailUrls(application));
        result.put("certName", application.getCertName());
        result.put("detailDesc", application.getDetailDesc());
        result.put("awardTime", application.getAwardTime());
        result.put("expectedScore", application.getExpectedScore());
        result.put("scoreDirection", normalizeScoreDirection(application.getScoreDirection()));
        result.put("proofText", application.getProofText());
        result.put("proofImages", application.getProofImages() == null || application.getProofImages().isBlank()
                ? List.of()
                : List.of(application.getProofImages().split("\\|\\|")));
        result.put("proofMedia", buildProofMedia(application));
        result.put("status", application.getStatus());
        result.put("statusLabel", resolveReviewStatusLabel(application.getStatus()));
        result.put("reviewStage", application.getReviewStage());
        result.put("reviewStageLabel", resolveReviewStageLabel(application.getReviewStage()));
        result.put("returnReason", application.getReturnReason());
        result.put("packageReturned", isPackageReturned(application));
        result.put("recordReviewResult", resolveRecordReviewResult(application));
        result.put("recordReviewResultLabel", resolveRecordReviewResultLabel(application));
        result.put("withdrawalStatus", application.getWithdrawalStatus());
        result.put("withdrawalRequestedAt", application.getWithdrawalRequestedAt());
        result.put("withdrawalReviewedAt", application.getWithdrawalReviewedAt());
        result.put("withdrawalReviewerName", application.getWithdrawalReviewerName());
        result.put("withdrawalComment", application.getWithdrawalComment());
        result.put("submissionType", application.getSubmissionType() == null ? "normal" : application.getSubmissionType());
        result.put("submissionTypeLabel", resolveSubmissionTypeLabel(application.getSubmissionType()));
        result.put("uploadTime", application.getCreatedAt());
        result.put("updatedAt", application.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toStudentApplicationListItemMap(ExtraScoreApplication application) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("academicYear", resolveApplicationAcademicYear(application));
        result.put("submissionBatchCode", application.getSubmissionBatchCode());
        result.put("submissionPeriodLabel", application.getSubmissionPeriodLabel());
        result.put("submissionStartTime", application.getSubmissionStartTime());
        result.put("submissionEndTime", application.getSubmissionEndTime());
        result.put("scoreType", application.getScoreType());
        result.put("groupName", application.getGroupName());
        result.put("itemName", application.getItemName());
        result.put("levelName", application.getLevelName());
        result.put("authorLabel", application.getAuthorLabel());
        result.put("certFileName", firstStoredValue(application.getCertFileName()));
        result.put("certFileUrl", firstStoredValue(application.getCertFileUrl()));
        result.put("certFileNames", splitStoredList(application.getCertFileName()));
        result.put("certFileUrls", splitStoredList(application.getCertFileUrl()));
        result.put("thumbnailUrls", buildCertificateThumbnailUrls(application));
        result.put("certName", application.getCertName());
        result.put("awardTime", application.getAwardTime());
        result.put("expectedScore", application.getExpectedScore());
        result.put("scoreDirection", normalizeScoreDirection(application.getScoreDirection()));
        result.put("status", application.getStatus());
        result.put("statusLabel", resolveReviewStatusLabel(application.getStatus()));
        result.put("reviewStage", application.getReviewStage());
        result.put("reviewStageLabel", resolveReviewStageLabel(application.getReviewStage()));
        result.put("returnReason", application.getReturnReason());
        result.put("packageReturned", isPackageReturned(application));
        result.put("recordReviewResult", resolveRecordReviewResult(application));
        result.put("recordReviewResultLabel", resolveRecordReviewResultLabel(application));
        result.put("submissionType", application.getSubmissionType() == null ? "normal" : application.getSubmissionType());
        result.put("submissionTypeLabel", resolveSubmissionTypeLabel(application.getSubmissionType()));
        result.put("uploadTime", application.getCreatedAt());
        return result;
    }

    private Map<String, Object> toStudentApplicationListItemMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        copyKeys(result, source,
                "id",
                "academicYear",
                "submissionBatchCode",
                "submissionPeriodLabel",
                "submissionStartTime",
                "submissionEndTime",
                "scoreType",
                "groupName",
                "itemName",
                "levelName",
                "authorLabel",
                "certFileName",
                "certFileUrl",
                "certFileNames",
                "certFileUrls",
                "thumbnailUrls",
                "certName",
                "awardTime",
                "expectedScore",
                "scoreDirection",
                "status",
                "statusLabel",
                "reviewStage",
                "reviewStageLabel",
                "returnReason",
                "packageReturned",
                "recordReviewResult",
                "recordReviewResultLabel",
                "submissionType",
                "submissionTypeLabel",
                "uploadTime");
        return result;
    }

    private Map<String, Object> toStudentHistoryListMap(
            ExtraScoreApplication application,
            boolean canEdit
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("academicYear", resolveApplicationAcademicYear(application));
        result.put("submissionPeriodId", application.getSubmissionPeriodId());
        result.put("submissionPeriodLabel", application.getSubmissionPeriodLabel());
        result.put("submissionStartTime", application.getSubmissionStartTime());
        result.put("submissionEndTime", application.getSubmissionEndTime());
        result.put("scoreType", application.getScoreType());
        result.put("groupName", application.getGroupName());
        result.put("itemName", application.getItemName());
        result.put("levelName", application.getLevelName());
        result.put("certName", application.getCertName());
        result.put("expectedScore", application.getExpectedScore());
        result.put("scoreDirection", normalizeScoreDirection(application.getScoreDirection()));
        result.put("status", application.getStatus());
        result.put("statusLabel", resolveReviewStatusLabel(application.getStatus()));
        result.put("displayStatus", resolveHistoryDisplayStatus(application));
        result.put("statusGroup", resolveHistoryStatusGroup(application, canEdit));
        result.put("reviewStageLabel", resolveReviewStageLabel(application.getReviewStage()));
        result.put("returnReason", application.getReturnReason());
        result.put("submissionTypeLabel", resolveSubmissionTypeLabel(application.getSubmissionType()));
        result.put("currentRevisionNo", application.getCurrentRevisionNo());
        result.put("uploadTime", application.getCreatedAt());
        result.put("updatedAt", application.getUpdatedAt() == null ? application.getCreatedAt() : application.getUpdatedAt());
        result.put("actionRequired", canEdit);
        result.put("availableActions", canEdit ? List.of("EDIT", "VIEW") : List.of("VIEW"));
        result.put("primaryAction", canEdit ? "EDIT" : "VIEW");
        result.put("primaryActionLabel", canEdit
                ? ("draft".equals(application.getStatus()) ? "继续填写" : "修改重提")
                : "查看");
        return result;
    }

    private Map<String, Object> toStudentDetailMap(ExtraScoreApplication application) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("academicYear", resolveApplicationAcademicYear(application));
        result.put("submissionPeriodId", application.getSubmissionPeriodId());
        result.put("submissionPeriodLabel", application.getSubmissionPeriodLabel());
        result.put("submissionStartTime", application.getSubmissionStartTime());
        result.put("submissionEndTime", application.getSubmissionEndTime());
        result.put("scoreType", application.getScoreType());
        result.put("templateName", application.getTemplateName());
        result.put("groupName", application.getGroupName());
        result.put("itemName", application.getItemName());
        result.put("levelName", application.getLevelName());
        result.put("authorLabel", application.getAuthorLabel());
        result.put("certName", application.getCertName());
        result.put("certFileName", firstStoredValue(application.getCertFileName()));
        result.put("certFileUrl", firstStoredValue(application.getCertFileUrl()));
        result.put("certFileNames", splitStoredList(application.getCertFileName()));
        result.put("certFileUrls", splitStoredList(application.getCertFileUrl()));
        result.put("thumbnailUrls", buildCertificateThumbnailUrls(application));
        result.put("awardTime", application.getAwardTime());
        result.put("expectedScore", application.getExpectedScore());
        result.put("scoreDirection", normalizeScoreDirection(application.getScoreDirection()));
        result.put("detailDesc", application.getDetailDesc());
        result.put("proofText", application.getProofText());
        result.put("proofImages", application.getProofImages() == null || application.getProofImages().isBlank()
                ? List.of()
                : List.of(application.getProofImages().split("\\|\\|")));
        result.put("proofMedia", buildProofMedia(application));
        result.put("status", application.getStatus());
        result.put("statusLabel", resolveReviewStatusLabel(application.getStatus()));
        result.put("displayStatus", resolveHistoryDisplayStatus(application));
        result.put("reviewStageLabel", resolveReviewStageLabel(application.getReviewStage()));
        result.put("committeeStatusLabel", resolveReviewStatusLabel(application.getCommitteeStatus()));
        result.put("committeeComment", application.getCommitteeComment());
        result.put("teacherStatusLabel", resolveReviewStatusLabel(application.getTeacherStatus()));
        result.put("teacherComment", application.getTeacherComment());
        result.put("returnReason", application.getReturnReason());
        result.put("packageReturned", isPackageReturned(application));
        result.put("recordReviewResult", resolveRecordReviewResult(application));
        result.put("recordReviewResultLabel", resolveRecordReviewResultLabel(application));
        result.put("currentRevisionNo", application.getCurrentRevisionNo());
        result.put("uploadTime", application.getCreatedAt());
        result.put("updatedAt", application.getUpdatedAt() == null ? application.getCreatedAt() : application.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toStudentProcessPeriodMap(
            List<ExtraScoreApplication> periodRecords,
            Map<Long, Map<String, Object>> windowStates
    ) {
        ExtraScoreApplication first = periodRecords.stream()
                .min(Comparator.comparing(this::buildApplicationSortKey, Comparator.nullsLast(String::compareTo)))
                .orElseThrow();
        ExtraScoreApplication latest = periodRecords.stream()
                .max(Comparator.comparing(this::buildApplicationSortKey, Comparator.nullsLast(String::compareTo)))
                .orElse(first);

        long draftCount = countByStatus(periodRecords, "draft");
        long pendingCount = countByStatus(periodRecords, "pending");
        long approvedCount = countByStatus(periodRecords, "approved");
        long returnedCount = countByStatus(periodRecords, "returned");
        long rejectedCount = countByStatus(periodRecords, "rejected");
        long withdrawalPendingCount = periodRecords.stream()
                .filter(item -> "pending".equals(defaultString(item.getWithdrawalStatus()).toLowerCase()))
                .count();
        long committeePendingCount = periodRecords.stream()
                .filter(item -> "pending".equals(defaultString(item.getStatus()).toLowerCase()))
                .filter(item -> "committee_pending".equals(defaultString(item.getReviewStage()).toLowerCase()))
                .count();
        long teacherPendingCount = periodRecords.stream()
                .filter(item -> "pending".equals(defaultString(item.getStatus()).toLowerCase()))
                .filter(item -> "teacher_pending".equals(defaultString(item.getReviewStage()).toLowerCase()))
                .count();
        long resubmittableCount = periodRecords.stream()
                .filter(this::canResubmitApplication)
                .count();

        String reviewStage = resolvePeriodReviewStage(periodRecords, draftCount, pendingCount, approvedCount, returnedCount, rejectedCount);
        String status = resolvePeriodStatus(periodRecords.size(), draftCount, pendingCount, approvedCount, returnedCount, rejectedCount);
        Map<String, Object> windowState = resolveWindowState(first, windowStates);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodKey", buildApplicationPeriodKey(first));
        result.put("academicYear", resolveApplicationAcademicYear(first));
        result.put("submissionPeriodLabel", windowState.getOrDefault("periodLabel", first.getSubmissionPeriodLabel()));
        result.put("submissionStartTime", first.getSubmissionStartTime());
        result.put("submissionEndTime", first.getSubmissionEndTime());
        result.put("totalCount", periodRecords.size());
        result.put("draftCount", draftCount);
        result.put("pendingCount", pendingCount);
        result.put("committeePendingCount", committeePendingCount);
        result.put("teacherPendingCount", teacherPendingCount);
        result.put("approvedCount", approvedCount);
        result.put("returnedCount", returnedCount);
        result.put("rejectedCount", rejectedCount);
        result.put("withdrawalPendingCount", withdrawalPendingCount);
        result.put("reviewStage", reviewStage);
        result.put("reviewStageLabel", resolveReviewStageLabel(reviewStage));
        result.put("status", status);
        result.put("statusLabel", resolveReviewStatusLabel(status));
        result.put("latestUploadTime", latest.getCreatedAt());
        result.put("canSubmit", resubmittableCount > 0 && pendingCount == 0 && withdrawalPendingCount == 0);
        result.put("canWithdraw", pendingCount > 0 && withdrawalPendingCount == 0);
        result.put("withdrawalPending", withdrawalPendingCount > 0);
        result.put("periodSettled", Boolean.TRUE.equals(windowState.get("periodSettled")));
        result.put("scoreSnapshotUpdated", Boolean.TRUE.equals(windowState.get("scoreSnapshotUpdated")));
        return result;
    }

    private long countByStatus(List<ExtraScoreApplication> records, String status) {
        return records.stream()
                .filter(item -> status.equals(defaultString(item.getStatus()).toLowerCase()))
                .count();
    }

    private String resolvePeriodStatus(int totalCount, long draftCount, long pendingCount, long approvedCount, long returnedCount, long rejectedCount) {
        if (pendingCount > 0) {
            return "pending";
        }
        if (draftCount > 0) {
            return "draft";
        }
        if (returnedCount > 0) {
            return "returned";
        }
        if (rejectedCount > 0) {
            return "rejected";
        }
        if (approvedCount == totalCount && totalCount > 0) {
            return "approved";
        }
        return "pending";
    }

    private String resolvePeriodReviewStage(List<ExtraScoreApplication> records, long draftCount, long pendingCount, long approvedCount, long returnedCount, long rejectedCount) {
        boolean hasTeacherPending = records.stream().anyMatch(item -> "teacher_pending".equals(defaultString(item.getReviewStage()).toLowerCase()));
        if (hasTeacherPending) {
            return "teacher_pending";
        }
        boolean hasCommitteePending = records.stream().anyMatch(item -> "committee_pending".equals(defaultString(item.getReviewStage()).toLowerCase()));
        if (hasCommitteePending) {
            return "committee_pending";
        }
        if (pendingCount > 0) {
            return "pending";
        }
        if (draftCount > 0) {
            return "draft";
        }
        if (returnedCount > 0) {
            return "returned";
        }
        if (rejectedCount > 0) {
            return "rejected";
        }
        if (approvedCount == records.size() && !records.isEmpty()) {
            return "approved";
        }
        return "draft";
    }

    private void notifyClassCommitteeWithdrawalRequest(Student student, List<ExtraScoreApplication> applications) {
        if (student == null || applications == null || applications.isEmpty()) {
            return;
        }
        List<Student> committeeStudents = findClassCommitteeStudents(student.getClassName());
        if (committeeStudents.isEmpty()) {
            return;
        }
        ExtraScoreApplication first = applications.get(0);
        Announcement announcement = new Announcement();
        announcement.setTitle("附加分撤回申请待处理");
        announcement.setContent(String.format(
                "<p>%s（%s）申请撤回%s的附加分申报，共 %d 条，请班委及时审核。</p>",
                defaultString(student.getName()),
                defaultString(student.getStudentNo()),
                defaultString(first.getSubmissionPeriodLabel()).isBlank() ? "当前周期" : first.getSubmissionPeriodLabel(),
                applications.size()
        ));
        announcement.setCategory("COMMITTEE_NOTICE");
        announcement.setPublisherId(student.getUserId() == null ? 0L : student.getUserId());
        announcement.setPublisherName(defaultString(student.getName()).isBlank() ? defaultString(student.getStudentNo()) : student.getName());
        announcement.setPublisherRole("STUDENT");
        announcement.setTeacherId(student.getTeacherId());
        announcement.setClassName(student.getClassName());
        announcement.setRecipientCount(committeeStudents.size());
        announcement.setReadCount(0);
        announcement.setUnreadCount(committeeStudents.size());
        announcement.setRemindCount(0);
        Announcement saved = announcementRepository.save(announcement);

        List<AnnouncementRecipient> recipients = committeeStudents.stream()
                .map(target -> {
                    AnnouncementRecipient recipient = new AnnouncementRecipient();
                    recipient.setAnnouncementId(saved.getId());
                    recipient.setStudentId(target.getId());
                    recipient.setStudentNo(target.getStudentNo());
                    recipient.setStudentName(target.getName());
                    recipient.setTeacherId(target.getTeacherId());
                    recipient.setClassName(target.getClassName());
                    recipient.setRead(false);
                    recipient.setRemindCount(0);
                    return recipient;
                })
                .toList();
        announcementRecipientRepository.saveAll(recipients);
    }

    private List<Student> findClassCommitteeStudents(String className) {
        String normalizedClassName = defaultString(className);
        if (normalizedClassName.isBlank()) {
            return List.of();
        }
        Optional<Role> committeeRole = roleRepository.findByName("CLASS_COMMITTEE");
        if (committeeRole.isEmpty()) {
            return List.of();
        }
        Set<String> committeeStudentNos = userRepository.findByRoleId(committeeRole.get().getId()).stream()
                .filter(user -> normalizedClassName.equals(defaultString(user.getClassName())))
                .map(User::getStudentNo)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        if (committeeStudentNos.isEmpty()) {
            return List.of();
        }
        return studentRepository.findByClassName(normalizedClassName).stream()
                .filter(student -> committeeStudentNos.contains(student.getStudentNo()))
                .toList();
    }

    private Map<String, Object> resolveCurrentProcessSummary(
            List<ExtraScoreApplication> applications,
            Map<Long, Map<String, Object>> windowStates
    ) {
        if (applications == null || applications.isEmpty()) {
            return null;
        }
        List<ExtraScoreApplication> activeApplications = applications.stream()
                .filter(application -> isCurrentProcessCandidate(application, resolveWindowState(application, windowStates)))
                .toList();
        if (activeApplications.isEmpty()) {
            return null;
        }

        ExtraScoreApplication latestRecord = activeApplications.stream()
                .max(Comparator.comparing(this::buildApplicationSortKey, Comparator.nullsLast(String::compareTo)))
                .orElse(null);
        if (latestRecord == null) {
            return null;
        }

        String periodKey = buildApplicationPeriodKey(latestRecord);
        List<ExtraScoreApplication> periodRecords = activeApplications.stream()
                .filter(item -> periodKey.equals(buildApplicationPeriodKey(item)))
                .toList();
        ExtraScoreApplication stageRecord = periodRecords.stream()
                .max(Comparator.comparingInt(this::resolveProcessStageRank))
                .orElse(latestRecord);

        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (ExtraScoreApplication item : periodRecords) {
            String type = defaultString(item.getScoreType()).trim().toUpperCase();
            if (!type.isBlank()) {
                typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
            }
        }

        String stage = defaultString(stageRecord.getReviewStage()).isBlank()
                ? defaultString(stageRecord.getStatus())
                : defaultString(stageRecord.getReviewStage());
        Map<String, Object> windowState = resolveWindowState(stageRecord, windowStates);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", stageRecord.getId());
        result.put("status", stageRecord.getStatus());
        result.put("statusLabel", resolveReviewStatusLabel(stageRecord.getStatus()));
        result.put("reviewStage", stageRecord.getReviewStage());
        result.put("reviewStageLabel", resolveReviewStageLabel(stageRecord.getReviewStage()));
        result.put("stage", stage);
        result.put("stageLabel", resolveReviewStageLabel(stage));
        result.put("submissionBatchCode", stageRecord.getSubmissionBatchCode());
        result.put("submissionPeriodLabel", windowState.getOrDefault("periodLabel", stageRecord.getSubmissionPeriodLabel()));
        result.put("submissionStartTime", stageRecord.getSubmissionStartTime());
        result.put("submissionEndTime", stageRecord.getSubmissionEndTime());
        result.put("uploadTime", stageRecord.getCreatedAt());
        result.put("returnReason", stageRecord.getReturnReason());
        result.put("committeeStatus", stageRecord.getCommitteeStatus());
        result.put("committeeReviewerName", stageRecord.getCommitteeReviewerName());
        result.put("committeeReviewedAt", stageRecord.getCommitteeReviewedAt());
        result.put("committeeComment", stageRecord.getCommitteeComment());
        result.put("teacherStatus", stageRecord.getTeacherStatus());
        result.put("teacherReviewerName", stageRecord.getTeacherReviewerName());
        result.put("teacherReviewedAt", stageRecord.getTeacherReviewedAt());
        result.put("teacherComment", stageRecord.getTeacherComment());
        result.put("total", periodRecords.size());
        result.put("typeCounts", typeCounts);
        result.put("periodSettled", Boolean.TRUE.equals(windowState.get("periodSettled")));
        result.put("scoreSnapshotUpdated", Boolean.TRUE.equals(windowState.get("scoreSnapshotUpdated")));
        return result;
    }

    private Map<Long, Map<String, Object>> resolveStudentProcessWindowStates(List<ExtraScoreApplication> applications) {
        List<Long> windowIds = applications.stream()
                .map(ExtraScoreApplication::getSubmissionWindowId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return extraScoreSubmissionWindowService.getStudentProcessWindowStates(windowIds);
    }

    private Map<String, Object> resolveWindowState(
            ExtraScoreApplication application,
            Map<Long, Map<String, Object>> windowStates
    ) {
        if (application == null || application.getSubmissionWindowId() == null) {
            return Map.of();
        }
        return windowStates.getOrDefault(application.getSubmissionWindowId(), Map.of());
    }

    private Map<String, Double> summarizeApprovedExtraScores(List<ExtraScoreApplication> applications) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("MORAL", 0D);
        result.put("INTELLECTUAL", 0D);
        result.put("PHYSICAL", 0D);

        if (applications == null || applications.isEmpty()) {
            return result;
        }

        return effectiveScoreService.summarizeEffectiveScoresByType(applications.stream()
                .filter(application -> "approved".equals(defaultString(application.getStatus()).trim().toLowerCase()))
                .toList());
    }

    private boolean isCurrentProcessCandidate(
            ExtraScoreApplication application,
            Map<String, Object> windowState
    ) {
        if (isTeacherImportApplication(application)) {
            return false;
        }
        String status = defaultString(application.getStatus()).trim().toLowerCase();
        String reviewStage = defaultString(application.getReviewStage()).trim().toLowerCase();
        if ("pending".equals(status)
                || "committee_pending".equals(reviewStage)
                || "teacher_pending".equals(reviewStage)) {
            return true;
        }
        if ("approved".equals(status)
                && !Boolean.TRUE.equals(windowState.get("periodSettled"))
                && isSubmissionPeriodOpen(application)) {
            return true;
        }

        boolean draftLike = "draft".equals(status)
                || "returned".equals(status)
                || "rejected".equals(status)
                || "draft".equals(reviewStage);
        return draftLike && hasSubmissionPeriod(application);
    }

    private boolean isTeacherImportApplication(ExtraScoreApplication application) {
        if (application == null) {
            return false;
        }
        String submissionType = defaultString(application.getSubmissionType()).trim().toLowerCase();
        String periodLabel = defaultString(application.getSubmissionPeriodLabel()).trim();
        String batchCode = defaultString(application.getSubmissionBatchCode()).trim().toLowerCase();
        return "teacher_import".equals(submissionType)
                || periodLabel.contains("教师导入")
                || batchCode.startsWith("teacher-import");
    }

    private boolean isSubmissionPeriodOpen(ExtraScoreApplication application) {
        if (!hasSubmissionPeriod(application)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = application.getSubmissionStartTime();
        LocalDateTime endTime = application.getSubmissionEndTime();
        return (startTime == null || !now.isBefore(startTime))
                && (endTime == null || !now.isAfter(endTime));
    }

    private boolean hasSubmissionPeriod(ExtraScoreApplication application) {
        return application != null
                && (application.getSubmissionWindowId() != null
                    || !defaultString(application.getSubmissionBatchCode()).trim().isBlank()
                    || !defaultString(application.getSubmissionPeriodLabel()).trim().isBlank()
                    || application.getSubmissionStartTime() != null
                    || application.getSubmissionEndTime() != null);
    }

    private String buildApplicationPeriodKey(ExtraScoreApplication application) {
        String batchCode = defaultString(application.getSubmissionBatchCode()).trim();
        if (!batchCode.isBlank()) {
            return batchCode;
        }
        return defaultString(application.getSubmissionPeriodLabel())
                + "|"
                + valueString(application.getSubmissionStartTime())
                + "|"
                + valueString(application.getSubmissionEndTime());
    }

    private String buildApplicationSortKey(ExtraScoreApplication application) {
        return valueString(application.getSubmissionStartTime())
                + "|"
                + valueString(application.getCreatedAt())
                + "|"
                + valueString(application.getId());
    }

    private int resolveProcessStageRank(ExtraScoreApplication application) {
        String reviewStage = defaultString(application.getReviewStage()).trim().toLowerCase();
        if ("approved".equals(reviewStage)) {
            return 5;
        }
        if ("teacher_pending".equals(reviewStage)) {
            return 4;
        }
        if ("committee_pending".equals(reviewStage)) {
            return 3;
        }
        if ("draft".equals(reviewStage)) {
            return 2;
        }
        if ("returned".equals(reviewStage) || "rejected".equals(reviewStage)) {
            return 1;
        }
        return 0;
    }

    private String valueString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> toReviewStudentGroupMap(List<Map<String, Object>> records) {
        Map<String, Object> first = records.isEmpty() ? Map.of() : records.get(0);
        List<Map<String, Object>> sortedRecords = records.stream()
                .sorted((left, right) -> String.valueOf(right.get("uploadTime")).compareTo(String.valueOf(left.get("uploadTime"))))
                .toList();
        long pending = sortedRecords.stream().filter(item -> "pending".equals(item.get("status"))).count();
        long approved = sortedRecords.stream().filter(item -> "approved".equals(item.get("status"))).count();
        long returned = sortedRecords.stream().filter(item -> "returned".equals(item.get("status")) || "rejected".equals(item.get("status"))).count();
        long committeePending = sortedRecords.stream().filter(item -> "pending".equals(item.get("status")) && "committee_pending".equals(item.get("reviewStage"))).count();
        long teacherPending = sortedRecords.stream().filter(item -> "pending".equals(item.get("status")) && "teacher_pending".equals(item.get("reviewStage"))).count();
        long committeeApproved = sortedRecords.stream().filter(item -> "approved".equals(item.get("committeeStatus"))).count();
        long committeeReturned = sortedRecords.stream().filter(item -> "returned".equals(item.get("committeeStatus"))).count();
        long committeeReviewed = committeeApproved + committeeReturned;
        long withdrawalPending = sortedRecords.stream()
                .filter(item -> "pending".equals(item.get("withdrawalStatus")))
                .count();
        long actionableWithdrawal = sortedRecords.stream()
                .filter(item -> "pending".equals(item.get("withdrawalStatus")))
                .filter(item -> Boolean.TRUE.equals(item.get("canReviewWithdrawal")))
                .count();
        long committeeActionable = sortedRecords.stream()
                .filter(item -> !"pending".equals(item.get("withdrawalStatus")))
                .filter(item -> Boolean.TRUE.equals(item.get("canCommitteeReview")))
                .count();
        long teacherReviewActionable = sortedRecords.stream()
                .filter(item -> !"pending".equals(item.get("withdrawalStatus")))
                .filter(item -> Boolean.TRUE.equals(item.get("canTeacherReview")))
                .count();
        long teacherActionable = sortedRecords.stream()
                .filter(item -> !"pending".equals(item.get("withdrawalStatus")))
                .filter(item -> Boolean.TRUE.equals(item.get("teacherActionable")))
                .count();
        long teacherWithdrawalActionable = sortedRecords.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("teacherWithdrawalActionable")))
                .count();
        long actionablePending = actionableWithdrawal + committeeActionable + teacherReviewActionable;
        long waitingCommittee = sortedRecords.stream()
                .filter(item -> "pending".equals(item.get("status")))
                .filter(item -> "committee_pending".equals(item.get("reviewStage")))
                .filter(item -> !Boolean.TRUE.equals(item.get("teacherActionable")))
                .count();
        long processed = approved + returned;
        int progressPercent = sortedRecords.isEmpty()
                ? 0
                : Math.round(processed * 100F / sortedRecords.size());

        String stageKey;
        String stageLabel;
        String stageHint;
        String actionLabel;
        if (actionableWithdrawal > 0) {
            stageKey = "withdrawal_actionable";
            stageLabel = "需要处理";
            stageHint = actionableWithdrawal + " 条撤回申请需要处理";
            actionLabel = "处理撤回";
        } else if (committeeActionable > 0) {
            stageKey = "committee_actionable";
            stageLabel = "需要处理";
            stageHint = committeeActionable + " 条申请等待班委初审";
            actionLabel = "开始审核";
        } else if (teacherReviewActionable > 0) {
            stageKey = "teacher_actionable";
            stageLabel = "需要处理";
            stageHint = teacherReviewActionable + " 条申请等待教师审核";
            actionLabel = "开始审核";
        } else if (teacherWithdrawalActionable > 0) {
            stageKey = "waiting_teacher";
            stageLabel = "等待教师";
            stageHint = teacherWithdrawalActionable + " 条撤回申请等待教师处理";
            actionLabel = "查看进度";
        } else if (teacherActionable > 0 || teacherPending > 0) {
            long waitingTeacher = Math.max(teacherActionable, teacherPending);
            stageKey = "waiting_teacher";
            stageLabel = "等待教师";
            stageHint = waitingTeacher + " 条申请等待教师审核";
            actionLabel = "查看进度";
        } else if (waitingCommittee > 0) {
            stageKey = "waiting_committee";
            stageLabel = "等待班委";
            stageHint = waitingCommittee + " 条申请等待班委初审";
            actionLabel = "查看进度";
        } else if (pending > 0) {
            stageKey = "pending";
            stageLabel = "审核中";
            stageHint = pending + " 条申请正在审核";
            actionLabel = "查看进度";
        } else if (returned > 0) {
            stageKey = "returned";
            stageLabel = "存在打回";
            stageHint = returned + " 条申请已打回";
            actionLabel = "查看记录";
        } else if (!sortedRecords.isEmpty() && approved == sortedRecords.size()) {
            stageKey = "completed";
            stageLabel = "审核完成";
            stageHint = "全部申请已通过";
            actionLabel = "查看记录";
        } else {
            stageKey = "no_records";
            stageLabel = "暂无申请";
            stageHint = "当前没有申请记录";
            actionLabel = "查看记录";
        }
        String committeeReviewSummary;
        String committeeReviewSummaryLabel;
        if (sortedRecords.isEmpty()) {
            committeeReviewSummary = "none";
            committeeReviewSummaryLabel = "暂无申请";
        } else if (committeePending > 0) {
            committeeReviewSummary = committeeReviewed > 0 ? "partial" : "pending";
            committeeReviewSummaryLabel = committeeReviewed > 0 ? "班委部分完成" : "班委待审";
        } else if (committeeReturned > 0) {
            committeeReviewSummary = committeeApproved > 0 ? "mixed" : "returned";
            committeeReviewSummaryLabel = committeeApproved > 0 ? "班委部分驳回" : "班委已驳回";
        } else if (committeeApproved > 0 && committeeReviewed == sortedRecords.size()) {
            committeeReviewSummary = "approved";
            committeeReviewSummaryLabel = "班委已通过";
        } else if (teacherPending > 0 || approved > 0) {
            committeeReviewSummary = "approved";
            committeeReviewSummaryLabel = "班委已通过";
        } else {
            committeeReviewSummary = "pending";
            committeeReviewSummaryLabel = "班委待审";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", first.get("studentNo"));
        result.put("studentNo", first.get("studentNo"));
        result.put("studentName", first.get("studentName"));
        result.put("className", first.get("className"));
        result.put("academicYear", first.get("academicYear"));
        result.put("recordsCount", sortedRecords.size());
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("returned", returned);
        result.put("committeePending", committeePending);
        result.put("teacherPending", teacherPending);
        result.put("committeeApproved", committeeApproved);
        result.put("committeeReturned", committeeReturned);
        result.put("committeeReviewed", committeeReviewed);
        result.put("committeeReviewSummary", committeeReviewSummary);
        result.put("committeeReviewSummaryLabel", committeeReviewSummaryLabel);
        result.put("withdrawalPending", withdrawalPending);
        result.put("actionablePending", actionablePending);
        result.put("waitingCommittee", waitingCommittee);
        result.put("processed", processed);
        result.put("progressPercent", progressPercent);
        result.put("stageKey", stageKey);
        result.put("stageLabel", stageLabel);
        result.put("stageHint", stageHint);
        result.put("actionLabel", actionLabel);
        result.put("latestUploadTime", sortedRecords.isEmpty() ? null : sortedRecords.get(0).get("uploadTime"));
        result.put("latestPeriodLabel", sortedRecords.isEmpty() ? "" : sortedRecords.get(0).get("submissionPeriodLabel"));
        return result;
    }

    private Map<String, Object> toReviewClassGroupMap(String className, List<Map<String, Object>> students) {
        int studentCount = students.size();
        long totalRecords = students.stream().mapToLong(s -> ((Number) s.getOrDefault("recordsCount", 0)).longValue()).sum();
        long pending = students.stream().mapToLong(s -> ((Number) s.getOrDefault("pending", 0)).longValue()).sum();
        long approved = students.stream().mapToLong(s -> ((Number) s.getOrDefault("approved", 0)).longValue()).sum();
        long returned = students.stream().mapToLong(s -> ((Number) s.getOrDefault("returned", 0)).longValue()).sum();
        long committeePending = students.stream().mapToLong(s -> ((Number) s.getOrDefault("committeePending", 0)).longValue()).sum();
        long teacherPending = students.stream().mapToLong(s -> ((Number) s.getOrDefault("teacherPending", 0)).longValue()).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("className", className);
        result.put("studentCount", studentCount);
        result.put("totalRecords", totalRecords);
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("returned", returned);
        result.put("committeePending", committeePending);
        result.put("teacherPending", teacherPending);
        return result;
    }

    private String normalizeTeacherImportScoreType(String scoreType) {
        String normalized = normalizeScoreType(scoreType);
        if (!List.of("MORAL", "INTELLECTUAL", "PHYSICAL").contains(normalized)) {
            throw new IllegalArgumentException("请选择德育、智育或体育中的一个附加分类");
        }
        return normalized;
    }

    private List<Student> findTeacherImportStudents(RequestUserContext context, String className) {
        if (!context.isTeacher()) {
            throw new IllegalArgumentException("仅老师可以使用导入示例");
        }
        Long teacherId = resolveTeacherId(context);
        String normalizedClassName = defaultString(className);
        List<Student> students = normalizedClassName.isBlank() || "all".equalsIgnoreCase(normalizedClassName)
                ? studentRepository.findByTeacherId(teacherId)
                : studentRepository.findByTeacherIdAndClassName(teacherId, normalizedClassName);
        List<Student> sorted = students.stream()
                .sorted(Comparator.comparing(Student::getClassName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Student::getStudentNo, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("当前范围内没有学生可导入");
        }
        return sorted;
    }

    private TeacherImportTemplateContext loadTeacherImportTemplateContext(RequestUserContext context, String scoreType) {
        Map<String, Object> assignedTemplateData = extraScoreTemplateService.getAssignedTemplate(context, scoreType);
        Map<String, Object> assignedTemplate = castTemplateMap(assignedTemplateData.get("template"));
        if (assignedTemplate == null) {
            throw new IllegalArgumentException("请先在当前附加分模板页设置一个启用模板，再下载或导入");
        }
        Long templateId = parseLongValue(assignedTemplate.get("id"));
        ExtraScoreTemplate template = extraScoreTemplateService.findTemplateEntity(templateId);
        List<TeacherImportCategory> categories = buildTeacherImportCategories(template);
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("当前使用模板没有可导入的小类，请先配置小类");
        }
        return new TeacherImportTemplateContext(template, categories);
    }

    private List<TeacherImportCategory> buildTeacherImportCategories(ExtraScoreTemplate template) {
        List<ExtraScoreTemplateGroup> groups = groupRepository.findByTemplateIdOrderBySortOrderAscIdAsc(template.getId());
        List<Long> groupIds = groups.stream().map(ExtraScoreTemplateGroup::getId).toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ExtraScoreTemplateGroup> groupById = groups.stream()
                .collect(Collectors.toMap(ExtraScoreTemplateGroup::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        return itemRepository.findByGroupIdInOrderBySortOrderAscIdAsc(groupIds).stream()
                .filter(item -> !"inactive".equals(defaultString(item.getStatus())))
                .map(item -> {
                    ExtraScoreTemplateGroup group = groupById.get(item.getGroupId());
                    return group == null ? null : new TeacherImportCategory(template, group, item);
                })
                .filter(item -> item != null)
                .toList();
    }

    private TeacherImportCategory resolveTeacherImportCategory(TeacherImportTemplateContext templateContext, String groupName, String itemName) {
        String normalizedItemName = normalizeLookupText(itemName);
        if (normalizedItemName.isBlank()) {
            throw new IllegalArgumentException("申请小类不能为空");
        }
        String normalizedGroupName = normalizeLookupText(groupName);
        List<TeacherImportCategory> matched = templateContext.categories().stream()
                .filter(category -> normalizedItemName.equals(normalizeLookupText(category.item().getItemName())))
                .filter(category -> normalizedGroupName.isBlank()
                        || normalizedGroupName.equals(normalizeLookupText(category.group().getGroupName())))
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("申请分类不在当前使用模板中，请参考“分类参考”工作表填写");
        }
        if (matched.size() > 1 && normalizedGroupName.isBlank()) {
            throw new IllegalArgumentException("当前小类名称在多个大类下重复，请同时填写申请大类");
        }
        return matched.get(0);
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
    }

    private Map<String, Integer> readTeacherImportHeaders(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        if (headerRow == null) {
            return headers;
        }
        short lastCellNum = headerRow.getLastCellNum();
        for (int index = 0; index < lastCellNum; index++) {
            String text = readCellText(headerRow, index, formatter);
            if (!text.isBlank()) {
                headers.put(text, index);
            }
        }
        return headers;
    }

    private int resolveHeaderIndex(Map<String, Integer> headers, String name, int fallback) {
        return headers.getOrDefault(name, fallback);
    }

    private int resolveHeaderIndex(Map<String, Integer> headers, List<String> names, int fallback) {
        for (String name : names) {
            if (headers.containsKey(name)) {
                return headers.get(name);
            }
        }
        return fallback;
    }

    private String readCellText(Row row, int index, DataFormatter formatter) {
        if (row == null || index < 0) {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString().trim();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private Double readPositiveScore(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        double value;
        if (cell.getCellType() == CellType.NUMERIC) {
            value = cell.getNumericCellValue();
        } else {
            String text = formatter.formatCellValue(cell)
                    .replace("分", "")
                    .replace("+", "")
                    .replace(",", "")
                    .trim();
            if (text.isBlank()) {
                return null;
            }
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("加分值必须是数字");
            }
        }
        if (value <= 0) {
            throw new IllegalArgumentException("加分值必须大于 0");
        }
        return value;
    }

    private Map<Integer, List<String>> extractTeacherImportProofImages(Sheet sheet, int proofImageIndex) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        if (proofImageIndex < 0 || !(sheet instanceof XSSFSheet xssfSheet)) {
            return result;
        }
        XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
        if (drawing == null) {
            return result;
        }
        for (XSSFShape shape : drawing.getShapes()) {
            if (!(shape instanceof XSSFPicture picture)) {
                continue;
            }
            XSSFClientAnchor anchor = picture.getClientAnchor();
            if (anchor == null || anchor.getCol1() != proofImageIndex || anchor.getRow1() <= 0) {
                continue;
            }
            XSSFPictureData pictureData = picture.getPictureData();
            if (pictureData == null || pictureData.getData() == null || pictureData.getData().length == 0) {
                continue;
            }
            String extension = defaultString(pictureData.suggestFileExtension());
            String contentType = defaultString(pictureData.getMimeType());
            try {
                String path = proofFileStorageService.uploadBytes(pictureData.getData(), extension, contentType);
                result.computeIfAbsent(anchor.getRow1(), ignored -> new ArrayList<>()).add(path);
            } catch (IOException exception) {
                throw new IllegalArgumentException("保存 Excel 凭证图片失败");
            }
        }
        return result;
    }

    private boolean isProofImageReference(String value) {
        String text = defaultString(value).trim();
        if (text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("/api/uploads/")
                || lower.startsWith("/uploads/");
    }

    private Map<String, Object> buildTeacherImportFailure(int rowNumber, String studentNo, String studentName, String message) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("rowNumber", rowNumber);
        failure.put("studentNo", studentNo);
        failure.put("studentName", studentName);
        failure.put("message", message);
        return failure;
    }

    private ExtraScoreApplication buildTeacherImportApplication(
            RequestUserContext context,
            Student student,
            TeacherImportCategory category,
            Double score,
            String remark,
            List<String> proofImagePaths,
            String batchCode,
            LocalDateTime now,
            String reviewerName
    ) {
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setStudentId(student.getId());
        application.setStudentNo(student.getStudentNo());
        application.setStudentName(student.getName());
        application.setClassName(student.getClassName());
        application.setTeacherId(resolveTeacherId(context));
        application.setAcademicYear(extraScoreSubmissionWindowService.resolveCurrentAcademicYear());
        try {
            applySubmissionWindow(application, extraScoreSubmissionWindowService.getActiveSubmissionWindow(student));
        } catch (IllegalArgumentException ignored) {
            // 没有开放周期时，教师导入仍按当前学年落库，供成绩汇总使用。
        }
        application.setSubmissionBatchCode(batchCode);
        application.setSubmissionPeriodLabel("教师导入");
        application.setScoreType(category.template().getScoreType());
        application.setTemplateId(category.template().getId());
        application.setTemplateName(category.template().getName());
        application.setGroupName(category.group().getGroupName());
        application.setItemId(category.item().getId());
        application.setItemName(category.item().getItemName());
        application.setScoreDirection(normalizeScoreDirection(category.item().getScoreDirection()));
        application.setBaseScore(score);
        application.setAuthorRatio(100D);
        application.setCertName("教师导入");
        application.setDetailDesc(defaultString(remark));
        application.setAwardTime(now.toLocalDate());
        application.setExpectedScore(score);
        List<String> normalizedProofImages = proofImagePaths == null
                ? List.of()
                : proofImagePaths.stream()
                    .map(this::defaultString)
                    .filter(item -> !item.isBlank())
                    .toList();
        application.setProofImages(String.join("||", normalizedProofImages));
        application.setProofText(normalizedProofImages.isEmpty()
                ? "老师通过 Excel 导入"
                : "老师通过 Excel 导入，含凭证图片 " + normalizedProofImages.size() + " 张");
        application.setStatus("approved");
        application.setReviewStage("approved");
        application.setCommitteeStatus("approved");
        application.setCommitteeComment("教师导入，无需班委初审");
        application.setCommitteeReviewerId(context.getUserId());
        application.setCommitteeReviewerName(reviewerName);
        application.setCommitteeReviewedAt(now);
        application.setTeacherStatus("approved");
        application.setTeacherComment("教师导入通过");
        application.setTeacherReviewerId(context.getUserId());
        application.setTeacherReviewerName(reviewerName);
        application.setTeacherReviewedAt(now);
        application.setSubmissionType("teacher_import");
        return application;
    }

    private List<Map<String, Object>> buildProofMedia(ExtraScoreApplication application) {
        return parseProofImages(application).stream()
                .map(path -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("url", path);
                    item.put("name", resolveMediaName(path));
                    item.put("type", resolveMediaType(path));
                    return item;
                })
                .toList();
    }

    private String resolveMediaName(String path) {
        String text = defaultString(path);
        int slash = text.lastIndexOf('/');
        return slash >= 0 ? text.substring(slash + 1) : text;
    }

    private String resolveMediaType(String path) {
        String text = defaultString(path).toLowerCase();
        if (text.matches(".*\\.(mp4|webm|mov|m4v|avi)$")) {
            return "video";
        }
        return "image";
    }

    public Student resolveCurrentStudent(RequestUserContext context) {
        Optional<Student> student = Optional.empty();
        if (context.getStudentNo() != null) {
            student = studentRepository.findByStudentNo(context.getStudentNo());
        }
        if (student.isEmpty() && context.getUserId() != null) {
            student = studentRepository.findByUserId(context.getUserId());
        }
        return student.orElseThrow(() -> new IllegalArgumentException("当前学生信息不存在"));
    }

    private String normalizeScoreType(String scoreType) {
        if (scoreType == null || scoreType.isBlank()) {
            throw new IllegalArgumentException("附加分类型不能为空");
        }
        return scoreType.trim().toUpperCase();
    }

    private String normalizeReviewScoreType(String scoreType) {
        if (scoreType == null || scoreType.isBlank() || "all".equalsIgnoreCase(scoreType)) {
            return null;
        }
        return normalizeScoreType(scoreType);
    }

    private String normalizeScoreDirection(String scoreDirection) {
        String normalized = scoreDirection == null || scoreDirection.isBlank() ? "add" : scoreDirection.trim().toLowerCase();
        if (!List.of("add", "deduct").contains(normalized)) {
            return "add";
        }
        return normalized;
    }

    private String resolveSubmissionTypeLabel(String submissionType) {
        return switch (defaultString(submissionType)) {
            case "supplement" -> "补充";
            case "teacher_import" -> "教师导入";
            default -> "正常提交";
        };
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeLookupText(String value) {
        return defaultString(value).replaceAll("\\s+", "");
    }

    private void resetReviewWorkflow(ExtraScoreApplication application) {
        application.setStatus("pending");
        application.setReviewStage("committee_pending");
        application.setCommitteeStatus("pending");
        application.setCommitteeComment("");
        application.setCommitteeReviewerId(null);
        application.setCommitteeReviewerName(null);
        application.setCommitteeReviewedAt(null);
        application.setTeacherStatus("pending");
        application.setTeacherComment("");
        application.setTeacherReviewerId(null);
        application.setTeacherReviewerName(null);
        application.setTeacherReviewedAt(null);
        application.setReturnReason(null);
        clearWithdrawalRequest(application);
    }

    private void resetDraftWorkflow(ExtraScoreApplication application) {
        application.setStatus("draft");
        application.setReviewStage("draft");
        application.setCommitteeStatus("draft");
        application.setCommitteeComment("");
        application.setCommitteeReviewerId(null);
        application.setCommitteeReviewerName(null);
        application.setCommitteeReviewedAt(null);
        application.setTeacherStatus("draft");
        application.setTeacherComment("");
        application.setTeacherReviewerId(null);
        application.setTeacherReviewerName(null);
        application.setTeacherReviewedAt(null);
        application.setReturnReason(null);
        clearWithdrawalRequest(application);
    }

    private void clearWithdrawalRequest(ExtraScoreApplication application) {
        application.setWithdrawalStatus(null);
        application.setWithdrawalRequestedAt(null);
        application.setWithdrawalReviewedAt(null);
        application.setWithdrawalReviewerId(null);
        application.setWithdrawalReviewerName(null);
        application.setWithdrawalComment(null);
    }

    private boolean hasCommitteeReviewedAnyRecord(List<ExtraScoreApplication> applications) {
        return applications.stream().anyMatch(this::hasCommitteeReviewResult);
    }

    private boolean hasCommitteeReviewResult(ExtraScoreApplication application) {
        if (application.getCommitteeReviewedAt() != null) {
            return true;
        }
        String committeeStatus = defaultString(application.getCommitteeStatus()).toLowerCase();
        return List.of("approved", "returned", "rejected").contains(committeeStatus);
    }

    private void assertNoSubmittedPackageInWindow(List<ExtraScoreApplication> applications) {
        boolean hasSubmittedPackage = applications.stream()
                .anyMatch(item -> "pending".equals(item.getStatus())
                        || "approved".equals(item.getStatus())
                        || "pending".equals(item.getWithdrawalStatus()));
        if (hasSubmittedPackage) {
            throw new IllegalArgumentException("当前周期已有申请提交审核，可继续创建草稿；如需重新提交，请先申请撤回并等待班委同意");
        }
    }

    private void migrateDraftLikeApplicationsToActiveWindow(Student student) {
        try {
            migrateDraftLikeApplicationsToWindow(student, extraScoreSubmissionWindowService.getActiveSubmissionWindow(student));
        } catch (IllegalArgumentException ignored) {
            // 没有开放周期时，草稿先停留在原周期外；下个周期开放后再自动归入。
        }
    }

    private void migrateDraftLikeApplicationsToWindow(Student student, ExtraScoreSubmissionWindow window) {
        if (student == null || window == null || window.getId() == null) {
            return;
        }
        List<ExtraScoreApplication> movable = applicationRepository.findByStudentNoOrderByCreatedAtDesc(student.getStudentNo()).stream()
                .filter(item -> isDraftLikeStatus(item.getStatus()))
                .filter(item -> !isApprovedReviewResult(item))
                .filter(item -> item.getSubmissionWindowId() == null || !window.getId().equals(item.getSubmissionWindowId()))
                .toList();
        if (movable.isEmpty()) {
            return;
        }
        for (ExtraScoreApplication application : movable) {
            applySubmissionWindow(application, window);
        }
        applicationRepository.saveAll(movable);
    }

    private boolean isDraftLikeStatus(String status) {
        String normalized = defaultString(status).toLowerCase();
        return "draft".equals(normalized) || "returned".equals(normalized) || "rejected".equals(normalized);
    }

    private boolean isApprovedReviewResult(ExtraScoreApplication application) {
        return "approved".equals(resolveRecordReviewResult(application));
    }

    private boolean canResubmitApplication(ExtraScoreApplication application) {
        return isDraftLikeStatus(application.getStatus()) && !isApprovedReviewResult(application);
    }

    private List<ExtraScoreApplication> restoreApprovedReviewRecordsForResubmission(List<ExtraScoreApplication> applications) {
        List<ExtraScoreApplication> approvedReviewRecords = applications.stream()
                .filter(this::isPackageReturned)
                .toList();
        for (ExtraScoreApplication application : approvedReviewRecords) {
            application.setStatus("approved");
            application.setReviewStage("approved");
            application.setReturnReason(null);
            clearWithdrawalRequest(application);
        }
        return approvedReviewRecords;
    }

    private List<ExtraScoreApplication> findSameSubmissionPackage(ExtraScoreApplication application) {
        if (application == null || application.getSubmissionWindowId() == null) {
            return List.of();
        }
        return applicationRepository.findByStudentNoAndSubmissionWindowId(
                application.getStudentNo(),
                application.getSubmissionWindowId()
        );
    }

    private void settleRejectedPackageIfReady(ExtraScoreApplication application) {
        List<ExtraScoreApplication> packageRecords = findSameSubmissionPackage(application);
        if (packageRecords.isEmpty()) {
            return;
        }
        boolean hasRejected = packageRecords.stream()
                .anyMatch(item -> "rejected".equals(item.getStatus())
                        || "returned".equals(item.getCommitteeStatus())
                        || "returned".equals(item.getTeacherStatus()));
        if (!hasRejected) {
            return;
        }
        boolean hasPending = packageRecords.stream().anyMatch(item -> "pending".equals(item.getStatus()));
        if (hasPending) {
            return;
        }
        for (ExtraScoreApplication item : packageRecords) {
            if ("rejected".equals(item.getStatus())) {
                item.setReviewStage("returned");
                continue;
            }
            if ("approved".equals(item.getStatus())) {
                item.setStatus("returned");
                item.setReviewStage("returned");
                if (defaultString(item.getReturnReason()).isBlank()) {
                    item.setReturnReason("本周期存在被驳回记录，整包退回；该条审核结论为通过");
                }
            }
        }
        applicationRepository.saveAll(packageRecords);
    }

    private boolean matchesStatus(ExtraScoreApplication application, String status) {
        return switch (status) {
            case "", "all" -> !"draft".equals(application.getStatus());
            case "pending" -> "pending".equals(application.getStatus());
            case "approved" -> "approved".equals(application.getStatus());
            case "returned" -> "returned".equals(application.getStatus()) || "rejected".equals(application.getStatus());
            case "draft" -> "draft".equals(application.getStatus());
            default -> !"draft".equals(application.getStatus());
        };
    }

    private String resolveSubmissionType(String studentNo, ExtraScoreSubmissionWindow window, boolean forceSupplement) {
        if (forceSupplement || window == null || window.getId() == null) {
            return forceSupplement ? "supplement" : "normal";
        }
        boolean hasSubmittedInWindow = applicationRepository.findByStudentNoAndSubmissionWindowId(studentNo, window.getId())
                .stream()
                .anyMatch(item -> !"draft".equals(item.getStatus()));
        return hasSubmittedInWindow ? "supplement" : "normal";
    }

    private Long resolveTeacherId(RequestUserContext context) {
        if (context.getTeacherId() != null) {
            return context.getTeacherId();
        }
        if (context.isTeacher() && context.getUserId() != null) {
            return context.getUserId();
        }
        throw new IllegalArgumentException("当前用户未绑定老师信息");
    }

    private List<ExtraScoreApplication> findExportApplications(RequestUserContext context, String academicYear, String className, String studentNo, String studentNos, List<Long> applicationIds) {
        List<ExtraScoreApplication> applications = context.isAdmin()
                ? applicationRepository.findAll()
                : applicationRepository.findByTeacherIdOrderByCreatedAtDesc(resolveTeacherId(context));
        Set<Long> idFilter = applicationIds == null || applicationIds.isEmpty() ? Set.of() : new HashSet<>(applicationIds);
        String normalizedAcademicYear = defaultString(academicYear);
        String normalizedClassName = defaultString(className);
        String normalizedStudentNo = defaultString(studentNo);
        Set<String> studentNoFilter = parseStudentNos(studentNos);
        return applications.stream()
                .filter(application -> idFilter.isEmpty() || idFilter.contains(application.getId()))
                .filter(application -> normalizedAcademicYear.isBlank() || normalizedAcademicYear.equals(resolveApplicationAcademicYear(application)))
                .filter(application -> normalizedClassName.isBlank() || "all".equalsIgnoreCase(normalizedClassName) || normalizedClassName.equals(defaultString(application.getClassName())))
                .filter(application -> normalizedStudentNo.isBlank() || normalizedStudentNo.equals(defaultString(application.getStudentNo())))
                .filter(application -> studentNoFilter.isEmpty() || studentNoFilter.contains(defaultString(application.getStudentNo())))
                .filter(application -> !parseProofImages(application).isEmpty())
                .sorted(Comparator.comparing(ExtraScoreApplication::getClassName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ExtraScoreApplication::getStudentName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ExtraScoreApplication::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
    }

    private List<ExtraScoreApplication> findMyExportApplications(Student student, String academicYear, String scoreType) {
        String normalizedAcademicYear = defaultString(academicYear);
        String normalizedScoreType = defaultString(scoreType).toUpperCase();
        return applicationRepository.findByStudentNoOrderByCreatedAtDesc(student.getStudentNo()).stream()
                .filter(application -> normalizedAcademicYear.isBlank() || normalizedAcademicYear.equals(resolveApplicationAcademicYear(application)))
                .filter(application -> normalizedScoreType.isBlank() || "ALL".equals(normalizedScoreType) || normalizedScoreType.equalsIgnoreCase(defaultString(application.getScoreType())))
                .sorted(Comparator.comparing(ExtraScoreApplication::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
    }

    private String resolveScoreTypeLabel(String scoreType) {
        return switch (defaultString(scoreType).toUpperCase()) {
            case "MORAL" -> "德育";
            case "INTELLECTUAL" -> "智育";
            case "PHYSICAL" -> "体育";
            default -> defaultString(scoreType);
        };
    }

    private Set<String> parseStudentNos(String studentNos) {
        String normalized = defaultString(studentNos);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(List.of(normalized.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList());
    }

    private List<String> parseProofImages(ExtraScoreApplication application) {
        if (application.getProofImages() == null || application.getProofImages().isBlank()) {
            return List.of();
        }
        return List.of(application.getProofImages().split("\\|\\|")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String ensureUniqueEntryName(Set<String> existing, String entryName) {
        String normalized = entryName.isBlank() ? "未命名.jpg" : entryName;
        if (existing.add(normalized)) {
            return normalized;
        }
        int dotIndex = normalized.lastIndexOf('.');
        String base = dotIndex > 0 ? normalized.substring(0, dotIndex) : normalized;
        String extension = dotIndex > 0 ? normalized.substring(dotIndex) : "";
        int index = 2;
        while (true) {
            String candidate = base + "+" + index + extension;
            if (existing.add(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private String sanitizeFilename(String value) {
        String normalized = value == null || value.isBlank() ? "未命名" : value.trim();
        return normalized.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
    }

    private String resolveExtension(String originalFilename, String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "video/x-m4v" -> ".m4v";
            case "video/x-msvideo" -> ".avi";
            default -> contentType != null && contentType.startsWith("video/") ? ".mp4" : ".jpg";
        };
    }

    private boolean isAllowedMediaType(String contentType) {
        return Set.of(
                "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
                "video/mp4", "video/webm", "video/quicktime", "video/x-m4v", "video/x-msvideo"
        ).contains(contentType);
    }

    private boolean hasValidMediaSignature(MultipartFile file, String contentType) throws IOException {
        if (contentType.startsWith("image/")) {
            try (InputStream inputStream = file.getInputStream()) {
                return ImageIO.read(inputStream) != null;
            }
        }

        byte[] header = new byte[16];
        try (BufferedInputStream inputStream = new BufferedInputStream(file.getInputStream())) {
            int read = inputStream.read(header);
            if (read < 12) {
                return false;
            }
            if ("video/webm".equals(contentType)) {
                return startsWith(header, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
            }
            if ("video/x-msvideo".equals(contentType)) {
                return startsWith(header, new byte[]{'R', 'I', 'F', 'F'})
                        && header[8] == 'A' && header[9] == 'V' && header[10] == 'I' && header[11] == ' ';
            }
            return header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String resolveExtensionFromPath(String path) {
        String text = defaultString(path);
        int queryIndex = text.indexOf('?');
        if (queryIndex >= 0) {
            text = text.substring(0, queryIndex);
        }
        int dotIndex = text.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == text.length() - 1) {
            return ".bin";
        }
        String extension = text.substring(dotIndex).toLowerCase();
        if (extension.length() > 8 || !extension.matches("\\.[a-z0-9]+")
                || !Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".mp4", ".webm", ".mov", ".m4v", ".avi").contains(extension)) {
            return ".bin";
        }
        return extension;
    }

    private String buildMissingFileLine(ExtraScoreApplication application, String imagePath) {
        return defaultString(application.getClassName()) + " / "
                + defaultString(application.getStudentName()) + " / "
                + defaultString(application.getItemName()) + " / "
                + resolveAwardName(application) + "："
                + imagePath;
    }

    private String resolveAwardName(ExtraScoreApplication application) {
        String authorLabel = defaultString(application.getAuthorLabel());
        if (!authorLabel.isBlank()) {
            return authorLabel;
        }
        String levelName = defaultString(application.getLevelName());
        return levelName.isBlank() ? "未选择奖项" : levelName;
    }

    private boolean isPackageReturned(ExtraScoreApplication application) {
        return "returned".equals(application.getStatus())
                && "returned".equals(application.getReviewStage())
                && "approved".equals(resolveRecordReviewResult(application));
    }

    private String resolveRecordReviewResult(ExtraScoreApplication application) {
        String teacherStatus = defaultString(application.getTeacherStatus());
        if ("approved".equals(teacherStatus)) {
            return "approved";
        }
        if ("returned".equals(teacherStatus)) {
            return "rejected";
        }
        String committeeStatus = defaultString(application.getCommitteeStatus());
        if ("returned".equals(committeeStatus)) {
            return "rejected";
        }
        if ("approved".equals(committeeStatus)) {
            return "committee_approved";
        }
        return defaultString(application.getStatus());
    }

    private String resolveRecordReviewResultLabel(ExtraScoreApplication application) {
        return switch (resolveRecordReviewResult(application)) {
            case "approved" -> "单条已通过";
            case "committee_approved" -> "班委已通过";
            case "rejected" -> "单条有问题";
            case "pending" -> "待审核";
            case "draft" -> "草稿";
            default -> "-";
        };
    }

    private String buildExportNote(int exportedCount, List<String> skipped) {
        StringBuilder builder = new StringBuilder();
        builder.append("已导出图片数量：").append(exportedCount).append("\n");
        if (!skipped.isEmpty()) {
            builder.append("以下记录未找到对应图片文件，通常是历史数据仅保存了文件名：\n");
            skipped.forEach(item -> builder.append("- ").append(item).append("\n"));
        }
        if (exportedCount == 0 && skipped.isEmpty()) {
            builder.append("当前筛选范围内没有可导出的凭证图片。\n");
        }
        return builder.toString();
    }

    private String buildCertificateExportNote(int exportedCount, List<String> skipped) {
        StringBuilder builder = new StringBuilder();
        builder.append("已导出证书数量：").append(exportedCount).append("\n");
        if (!skipped.isEmpty()) {
            builder.append("以下记录未找到对应证书文件，通常是历史数据仅保存了文件名：\n");
            skipped.forEach(item -> builder.append("- ").append(item).append("\n"));
        }
        if (exportedCount == 0 && skipped.isEmpty()) {
            builder.append("当前筛选范围内没有可导出的证书文件。\n");
        }
        return builder.toString();
    }

    private String normalizeReviewAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("审核动作不能为空");
        }
        String normalized = action.trim().toLowerCase();
        if (!List.of("approve", "return").contains(normalized)) {
            throw new IllegalArgumentException("不支持的审核动作");
        }
        return normalized;
    }

    private void applyCommitteeReviewResult(ExtraScoreApplication application, RequestUserContext context, String action, String comment) {
        application.setCommitteeReviewerId(context.getUserId());
        application.setCommitteeReviewerName(resolveReviewerName(context));
        application.setCommitteeReviewedAt(LocalDateTime.now());
        application.setCommitteeComment(comment);
        if ("approve".equals(action)) {
            application.setCommitteeStatus("approved");
            application.setReviewStage("teacher_pending");
            application.setStatus("pending");
            application.setReturnReason(null);
            return;
        }
        if (comment.isBlank()) {
            comment = "班委驳回，请检查该条附加分材料";
        }
        application.setCommitteeStatus("returned");
        application.setReviewStage("returned");
        application.setStatus("rejected");
        application.setReturnReason(comment);
    }

    private void applyTeacherReviewResult(ExtraScoreApplication application, RequestUserContext context, String action, String comment) {
        application.setTeacherReviewerId(context.getUserId());
        application.setTeacherReviewerName(resolveReviewerName(context));
        application.setTeacherReviewedAt(LocalDateTime.now());
        application.setTeacherComment(comment);
        if ("approve".equals(action)) {
            validateGroupScoreCapOnApprove(application);
            application.setTeacherStatus("approved");
            application.setReviewStage("approved");
            application.setStatus("approved");
            application.setReturnReason(null);
            return;
        }
        if (comment.isBlank()) {
            comment = "老师驳回，请检查该条附加分材料";
        }
        application.setTeacherStatus("returned");
        application.setReviewStage("returned");
        application.setStatus("rejected");
        application.setReturnReason(comment);
    }

    private void validateGroupScoreCapOnApprove(ExtraScoreApplication application) {
        String groupName = defaultString(application.getGroupName());
        if (groupName.isBlank()) {
            return;
        }

        ExtraScoreTemplateGroup group = groupRepository.findByTemplateIdOrderBySortOrderAscIdAsc(application.getTemplateId()).stream()
                .filter(item -> groupName.equals(defaultString(item.getGroupName())))
                .sorted(Comparator.comparing(ExtraScoreTemplateGroup::getId))
                .findFirst()
                .orElse(null);
        if (group == null || group.getMaxScore() == null || group.getMaxScore() <= 0) {
            return;
        }

        List<ExtraScoreApplication> currentApprovedApplications = applicationRepository
                .findByStudentNoAndScoreTypeAndGroupNameAndStatus(
                        application.getStudentNo(),
                        application.getScoreType(),
                        groupName,
                        "approved"
                )
                .stream()
                .filter(existing -> !existing.getId().equals(application.getId()))
                .toList();

        List<ExtraScoreApplication> candidateApplications = new ArrayList<>(currentApprovedApplications);
        candidateApplications.add(application);
        double currentApprovedTotal = effectiveScoreService.sumEffectiveSignedScores(currentApprovedApplications);
        double candidateTotal = effectiveScoreService.sumEffectiveSignedScores(candidateApplications);
        double allowedLimit = normalizeGroupCap(group.getMaxScore(), application.getScoreDirection());

        if ("deduct".equals(normalizeScoreDirection(application.getScoreDirection()))) {
            if (candidateTotal < allowedLimit) {
                throw new IllegalArgumentException(String.format(
                        "大类“%s”最高扣分为 %.2f 分，当前已累计 %.2f 分，本次审核后将达到 %.2f 分",
                        groupName,
                        Math.abs(allowedLimit),
                        Math.abs(currentApprovedTotal),
                        Math.abs(candidateTotal)
                ));
            }
            return;
        }

        if (candidateTotal > allowedLimit) {
            throw new IllegalArgumentException(String.format(
                    "大类“%s”最高加分为 %.2f 分，当前已累计 %.2f 分，本次审核后将达到 %.2f 分",
                    groupName,
                    allowedLimit,
                    currentApprovedTotal,
                    candidateTotal
            ));
        }
    }

    private double resolveSignedExpectedScore(ExtraScoreApplication application) {
        return effectiveScoreService.resolveSignedScore(application);
    }

    private double normalizeGroupCap(Double maxScore, String scoreDirection) {
        double value = maxScore == null ? 0D : maxScore;
        return "deduct".equals(normalizeScoreDirection(scoreDirection)) ? -Math.abs(value) : Math.abs(value);
    }

    private String resolveReviewerName(RequestUserContext context) {
        if (context.getUserId() != null) {
            return userRepository.findById(context.getUserId())
                    .map(user -> {
                        String nickname = defaultString(user.getNickname());
                        return nickname.isBlank() ? defaultString(user.getUsername()) : nickname;
                    })
                    .orElse(context.getRole());
        }
        return context.getRole();
    }

    private String resolveReviewStageLabel(String reviewStage) {
        return switch (defaultString(reviewStage)) {
            case "committee_pending" -> "待班委初审";
            case "teacher_pending" -> "待老师审核";
            case "approved" -> "审核通过";
            case "returned" -> "已打回";
            case "draft" -> "草稿";
            default -> "-";
        };
    }

    private String resolveReviewStatusLabel(String reviewStatus) {
        return switch (defaultString(reviewStatus)) {
            case "pending" -> "待处理";
            case "approved" -> "已通过";
            case "returned" -> "已打回";
            case "skipped" -> "已跳过";
            case "forced" -> "强制进入老师审核";
            case "draft" -> "草稿";
            default -> "-";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castTemplateMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::castTemplateMap)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean containsIgnoreCase(Object value, String normalizedKeyword) {
        if (normalizedKeyword.isBlank()) {
            return true;
        }
        return stringValue(value).toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long parseLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalArgumentException("模板数据异常");
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String resolveGroupName(ExtraScoreTemplate template, Long groupId) {
        ExtraScoreTemplateGroup group = groupRepository.findById(groupId).orElse(null);
        if (group == null || !template.getId().equals(group.getTemplateId())) {
            return "";
        }
        return group.getGroupName() == null ? "" : group.getGroupName();
    }

    private String resolveApplicationAcademicYear(ExtraScoreApplication application) {
        String explicitAcademicYear = defaultString(application.getAcademicYear()).trim();
        if (!explicitAcademicYear.isBlank()) {
            return explicitAcademicYear;
        }

        LocalDate awardDate = application.getAwardTime();
        if (awardDate != null) {
            int year = awardDate.getYear();
            int month = awardDate.getMonthValue();
            int startYear = month >= 9 ? year : year - 1;
            return startYear + "-" + (startYear + 1);
        }

        LocalDateTime createdAt = application.getCreatedAt();
        if (createdAt != null) {
            int year = createdAt.getYear();
            int month = createdAt.getMonthValue();
            int startYear = month >= 9 ? year : year - 1;
            return startYear + "-" + (startYear + 1);
        }

        return "";
    }
}
