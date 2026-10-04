package com.markingsystem.controller.admin;

import com.markingsystem.dto.CompositeSubjectConfigRequest;
import com.markingsystem.service.CompositeSubjectConfigService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/composite-subject-configs")
public class AdminCompositeSubjectConfigController {

    @Autowired
    private CompositeSubjectConfigService compositeSubjectConfigService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam String academicYear,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String className,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String headerClassName,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, headerClassName, studentNo);
        Long resolvedTeacherId = resolveTeacherScopeId(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", compositeSubjectConfigService.listByScope(academicYear, majorId, grade, className, resolvedTeacherId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestParam String academicYear,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String headerClassName,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, headerClassName, studentNo);
        Long resolvedTeacherId = resolveTeacherScopeId(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", compositeSubjectConfigService.listClassConfigStatus(academicYear, resolvedTeacherId));
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> save(
            @RequestBody CompositeSubjectConfigRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        Long resolvedTeacherId = resolveTeacherScopeId(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", compositeSubjectConfigService.save(
                request.getAcademicYear(),
                request.getMajorId(),
                request.getGrade(),
                request.getClassName(),
                request.getItems(),
                resolvedTeacherId
        ));
        return ResponseEntity.ok(result);
    }

    private Long resolveTeacherScopeId(RequestUserContext context) {
        if (context == null || !context.isTeacher()) {
            return null;
        }
        return context.getTeacherId() != null ? context.getTeacherId() : context.getUserId();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
