package com.markingsystem.controller.student;

import com.markingsystem.dto.ExtraScoreApplicationRequest;
import com.markingsystem.service.ExtraScoreApplicationService;
import com.markingsystem.service.ExtraScoreSubmissionWindowService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/extra-score-applications")
public class ExtraScoreApplicationController {

    private final ExtraScoreApplicationService extraScoreApplicationService;
    private final ExtraScoreSubmissionWindowService extraScoreSubmissionWindowService;

    public ExtraScoreApplicationController(
            ExtraScoreApplicationService extraScoreApplicationService,
            ExtraScoreSubmissionWindowService extraScoreSubmissionWindowService
    ) {
        this.extraScoreApplicationService = extraScoreApplicationService;
        this.extraScoreSubmissionWindowService = extraScoreSubmissionWindowService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listApplications(
            @RequestParam String scoreType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listMyApplications(context, scoreType, page, size)));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> listApplicationHistory(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false, defaultValue = "all") String scoreType,
            @RequestParam(required = false, defaultValue = "all") String statusGroup,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listMyApplicationHistory(
                context,
                academicYear,
                scoreType,
                statusGroup,
                keyword,
                page,
                size
        )));
    }

    @GetMapping("/process")
    public ResponseEntity<Map<String, Object>> listApplicationProcess(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.listMyApplicationProcess(context)));
    }

    @GetMapping("/process/current")
    public ResponseEntity<Map<String, Object>> getCurrentApplicationProcess(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.getMyCurrentApplicationProcess(context)));
    }

    @GetMapping("/list-context")
    public ResponseEntity<Map<String, Object>> getApplicationListContext(
            @RequestParam String scoreType,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "upload-desc") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.getMyApplicationListContext(context, scoreType, status, keyword, sort, page, size)));
    }

    @GetMapping("/edit-context/{id}")
    public ResponseEntity<Map<String, Object>> getApplicationEditContext(
            @PathVariable Long id,
            @RequestParam(required = false) String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.getMyApplicationEditContext(context, id, scoreType)));
    }

    @GetMapping("/template-items/search")
    public ResponseEntity<Map<String, Object>> searchTemplateItems(
            @RequestParam String scoreType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "30") int limit,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", extraScoreApplicationService.searchMyTemplateItems(context, scoreType, keyword, limit)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getApplication(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.getMyApplication(context, id)));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<Resource> exportMyExcel(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Resource resource = extraScoreApplicationService.exportMyExcel(context, academicYear, scoreType);
        return download(resource, "附加分明细.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/export/proof-images")
    public ResponseEntity<Resource> exportMyProofImages(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Resource resource = extraScoreApplicationService.exportMyProofImages(context, academicYear, scoreType);
        return download(resource, "附加分凭证.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @GetMapping("/export/certificates")
    public ResponseEntity<Resource> exportMyCertificates(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Resource resource = extraScoreApplicationService.exportMyCertificates(context, academicYear, scoreType);
        return download(resource, "附加分证书.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createApplication(
            @RequestBody ExtraScoreApplicationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.createApplication(context, request)));
    }

    @PostMapping("/submit-current-window")
    public ResponseEntity<Map<String, Object>> submitCurrentWindow(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.submitCurrentWindowApplications(context)));
    }

    @PostMapping("/submit-selected")
    public ResponseEntity<Map<String, Object>> submitSelected(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Object rawIds = request == null ? null : request.get("applicationIds");
        List<Long> applicationIds = rawIds instanceof List<?> list
                ? list.stream().map(item -> Long.parseLong(String.valueOf(item))).toList()
                : List.of();
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.submitSelectedApplications(context, applicationIds)));
    }

    @PostMapping("/withdraw-current-window")
    public ResponseEntity<Map<String, Object>> withdrawCurrentWindow(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.withdrawCurrentWindowApplications(context)));
    }

    @PostMapping("/proof-images")
    public ResponseEntity<Map<String, Object>> uploadProofImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.uploadProofImages(context, files)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateApplication(
            @PathVariable Long id,
            @RequestBody ExtraScoreApplicationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreApplicationService.updateApplication(context, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteApplication(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        extraScoreApplicationService.deleteApplication(context, id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @GetMapping("/submission-window")
    public ResponseEntity<Map<String, Object>> getSubmissionWindow(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreSubmissionWindowService.getCurrentWindow(extraScoreApplicationService.resolveCurrentStudent(context))));
    }

    @GetMapping("/submission-window/history")
    public ResponseEntity<Map<String, Object>> getSubmissionWindowHistory(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreSubmissionWindowService.getStudentWindowHistory(extraScoreApplicationService.resolveCurrentStudent(context))));
    }

    private ResponseEntity<Resource> download(Resource resource, String filename, String contentType) {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
