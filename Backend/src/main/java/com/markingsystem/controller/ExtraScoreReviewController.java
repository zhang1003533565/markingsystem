package com.markingsystem.controller;

import com.markingsystem.dto.ExtraScoreApplicationBatchReviewRequest;
import com.markingsystem.dto.ExtraScoreApplicationReviewRequest;
import com.markingsystem.service.ExtraScoreApplicationService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/extra-score-review")
public class ExtraScoreReviewController {

    private final ExtraScoreApplicationService extraScoreApplicationService;

    public ExtraScoreReviewController(ExtraScoreApplicationService extraScoreApplicationService) {
        this.extraScoreApplicationService = extraScoreApplicationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listReviewApplications(
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean currentPeriodOnly,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listReviewApplications(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly)));
    }

    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> listReviewStudents(
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean currentPeriodOnly,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listReviewStudentGroups(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly)));
    }

    @GetMapping("/classes")
    public ResponseEntity<Map<String, Object>> listReviewClasses(
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean currentPeriodOnly,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listReviewClassGroups(context, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly)));
    }

    @GetMapping("/workbench")
    public ResponseEntity<Map<String, Object>> getReviewWorkbench(
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false, defaultValue = "true") boolean currentPeriodOnly,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", extraScoreApplicationService.getReviewWorkbench(context, scoreType, currentPeriodOnly)
        ));
    }

    @GetMapping("/class-progress")
    public ResponseEntity<Map<String, Object>> listReviewClassProgress(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listReviewClassProgress(context)));
    }

    @GetMapping("/classes/{targetClassName}/students")
    public ResponseEntity<Map<String, Object>> listReviewClassStudents(
            @PathVariable String targetClassName,
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean currentPeriodOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listReviewClassStudents(context, targetClassName, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly, page, size)));
    }

    @GetMapping("/students/{targetStudentNo}/applications")
    public ResponseEntity<Map<String, Object>> listReviewStudentApplications(
            @PathVariable String targetStudentNo,
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean currentPeriodOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listReviewApplicationsByStudent(context, targetStudentNo, scoreType, status, classId, startDate, endDate, keyword, currentPeriodOnly, page, size)));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<Map<String, Object>> getReviewApplicationDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.getReviewApplicationDetail(context, id)));
    }

    @PostMapping("/{id}/committee")
    public ResponseEntity<Map<String, Object>> committeeReview(
            @PathVariable Long id,
            @RequestBody ExtraScoreApplicationReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.committeeReview(context, id, request)));
    }

    @PostMapping("/{id}/withdrawal")
    public ResponseEntity<Map<String, Object>> reviewWithdrawal(
            @PathVariable Long id,
            @RequestBody ExtraScoreApplicationReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.reviewWithdrawalRequest(context, id, request)));
    }

    @PostMapping("/{id}/teacher")
    public ResponseEntity<Map<String, Object>> teacherReview(
            @PathVariable Long id,
            @RequestBody ExtraScoreApplicationReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.teacherReview(context, id, request)));
    }

    @PostMapping("/batch/approve")
    public ResponseEntity<Map<String, Object>> batchApprove(
            @RequestBody ExtraScoreApplicationBatchReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.batchApproveReview(context, request)));
    }

    @PostMapping("/committee/force-teacher-stage")
    public ResponseEntity<Map<String, Object>> forceCurrentClassToTeacherStage(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.forceCurrentClassToTeacherStage(context)));
    }

    @GetMapping("/teacher-import/template")
    public ResponseEntity<Resource> downloadTeacherImportTemplate(
            @RequestParam String scoreType,
            @RequestParam(required = false) String className,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String requestClassName,
            @RequestHeader(value = "X-Student-No", required = false) String requestStudentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, requestClassName, requestStudentNo);
        Resource resource = extraScoreApplicationService.exportTeacherImportTemplate(context, scoreType, className);
        String filename = URLEncoder.encode("教师导入加分示例.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping(value = "/teacher-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importTeacherExtraScore(
            @RequestParam("file") MultipartFile file,
            @RequestParam String scoreType,
            @RequestParam(required = false) String className,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String requestClassName,
            @RequestHeader(value = "X-Student-No", required = false) String requestStudentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, requestClassName, requestStudentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.importTeacherExtraScore(context, scoreType, className, file)));
    }

    @DeleteMapping("/teacher-import/{id}")
    public ResponseEntity<Map<String, Object>> deleteTeacherImportedApplication(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String requestClassName,
            @RequestHeader(value = "X-Student-No", required = false) String requestStudentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, requestClassName, requestStudentNo);
        extraScoreApplicationService.deleteTeacherImportedApplication(context, id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @GetMapping("/proof-images/export")
    public ResponseEntity<Resource> exportProofImages(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String studentNos,
            @RequestParam(required = false) String applicationIds,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String requestClassName,
            @RequestHeader(value = "X-Student-No", required = false) String requestStudentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, requestClassName, requestStudentNo);
        Resource resource = extraScoreApplicationService.exportProofImages(context, academicYear, className, studentNo, studentNos, parseApplicationIds(applicationIds));
        String filename = URLEncoder.encode("综测凭证图片-" + (academicYear == null || academicYear.isBlank() ? "全部学年" : academicYear) + ".zip", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    private List<Long> parseApplicationIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(Long::parseLong)
                .toList();
    }
}
