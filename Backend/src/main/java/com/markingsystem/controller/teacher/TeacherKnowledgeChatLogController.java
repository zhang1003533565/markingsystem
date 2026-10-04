package com.markingsystem.controller.teacher;

import com.markingsystem.entity.ClassInfo;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.service.KnowledgeChatLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教师端 AI 对话日志接口。
 *
 * <p>仅限教师身份；只能查看 / 反馈 / 删除本人带教班级的学生对话记录。
 */
@RestController
@RequestMapping("/api/teacher/knowledge-chat-logs")
public class TeacherKnowledgeChatLogController {

    @Autowired
    private KnowledgeChatLogService chatLogService;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "feedback", required = false) String feedback,
            @RequestParam(value = "className", required = false) String className,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        List<String> allowedClasses = resolveTeacherClassNames(teacherId);
        if (className != null && !className.isBlank()) {
            boolean ownClass = allowedClasses.stream()
                    .anyMatch(name -> className.trim().equalsIgnoreCase(name));
            if (!ownClass) {
                return ResponseEntity.status(403).body(Map.of(
                        "code", 403,
                        "message", "只能查看本人带教班级的对话日志"
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatLogService.list(keyword, feedback, page, size, allowedClasses, className));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/feedback")
    public ResponseEntity<Map<String, Object>> setFeedback(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        List<String> allowedClasses = resolveTeacherClassNames(teacherId);
        String feedback = body.get("feedback") == null ? null : String.valueOf(body.get("feedback"));
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatLogService.setFeedback(id, feedback, allowedClasses).orElse(null));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        List<String> allowedClasses = resolveTeacherClassNames(teacherId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", chatLogService.delete(id, allowedClasses));
        return ResponseEntity.ok(result);
    }

    private List<String> resolveTeacherClassNames(Long teacherId) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        return classInfoRepository.findByTeacherId(teacherId).stream()
                .map(ClassInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private Long resolveTeacherId(String teacherIdHeader, String userIdHeader, String userRole) {
        if (!"TEACHER".equalsIgnoreCase(userRole)) {
            return null;
        }
        if (teacherIdHeader != null && !teacherIdHeader.isBlank()) {
            try {
                return Long.parseLong(teacherIdHeader.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return Long.parseLong(userIdHeader.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
