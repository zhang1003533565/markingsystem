package com.markingsystem.controller.admin;

import com.markingsystem.dto.ExtraScoreSubmissionWindowRequest;
import com.markingsystem.service.ExtraScoreSubmissionWindowService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/extra-score-submission-windows")
public class AdminExtraScoreSubmissionWindowController {

    private final ExtraScoreSubmissionWindowService service;

    public AdminExtraScoreSubmissionWindowController(ExtraScoreSubmissionWindowService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String academicYear,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo
    ) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", service.listReviewPeriods(context, academicYear)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> save(
            @RequestBody ExtraScoreSubmissionWindowRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo
    ) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        if (!context.isTeacher() || context.getTeacherId() == null) {
            throw new IllegalArgumentException("当前仅支持老师按自己负责班级配置提交周期");
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", service.save(context.getTeacherId(), request)));
    }

    @PostMapping("/{windowId}/close")
    public ResponseEntity<Map<String, Object>> close(
            @PathVariable Long windowId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo
    ) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", service.closeReviewPeriod(context, windowId)));
    }

    @PostMapping("/{windowId}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @PathVariable Long windowId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo
    ) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", service.completeReviewPeriod(context, windowId)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
