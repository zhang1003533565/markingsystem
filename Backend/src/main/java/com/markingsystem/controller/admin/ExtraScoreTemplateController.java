package com.markingsystem.controller.admin;

import com.markingsystem.dto.ExtraScoreTemplateRequest;
import com.markingsystem.service.ExtraScoreTemplateService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/extra-score-templates")
public class ExtraScoreTemplateController {

    private final ExtraScoreTemplateService extraScoreTemplateService;

    public ExtraScoreTemplateController(ExtraScoreTemplateService extraScoreTemplateService) {
        this.extraScoreTemplateService = extraScoreTemplateService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTemplates() {
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.listTemplates()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTemplateDetail(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.getTemplateDetail(id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(
            @RequestBody ExtraScoreTemplateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.createTemplate(request, context)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable Long id,
            @RequestBody ExtraScoreTemplateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.updateTemplate(id, request, context)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        extraScoreTemplateService.deleteTemplate(id, context);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleTemplate(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.toggleTemplateStatus(id, context)));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignTemplate(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.assignTemplateToTeacher(id, context)));
    }

    @GetMapping("/assigned")
    public ResponseEntity<Map<String, Object>> getAssignedTemplate(
            @RequestParam String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.getAssignedTemplate(context, scoreType)));
    }

    @GetMapping("/assigned/form-context")
    public ResponseEntity<Map<String, Object>> getAssignedTemplateFormContext(
            @RequestParam String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.getAssignedTemplateFormContext(context, scoreType)));
    }

    @GetMapping("/assigned/export")
    public ResponseEntity<Resource> exportAssignedTemplate(
            @RequestParam String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Resource resource = extraScoreTemplateService.exportAssignedTemplateWorkbook(context, scoreType);
        String filename = URLEncoder.encode("当前附加分模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importTemplateWorkbook(
            @RequestParam("file") MultipartFile file,
            @RequestParam String scoreType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", extraScoreTemplateService.importTemplateWorkbook(context, scoreType, file)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
