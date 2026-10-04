package com.markingsystem.controller.admin;

import com.markingsystem.entity.ClassInfo;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.service.KnowledgeChatLogService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin / Teacher AI 对话日志接口。
 *
 * <p>管理员可查看全部记录；教师可查看、反馈、删除本人带教班级的学生对话记录。
 */
@RestController
@RequestMapping("/api/admin/knowledge-chat-logs")
public class AdminKnowledgeChatLogController {

    @Autowired
    private KnowledgeChatLogService chatLogService;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    /**
     * 对话日志列表（缺省返回全部，含学生页面聊天）。
     * 教师仅返回本人带教班级记录，可用 className 再缩小范围。
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "feedback", required = false) String feedback,
            @RequestParam(value = "className", required = false) String className,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String headerClassName,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(
                userId, role, teacherId, headerClassName, studentNo);
        List<String> allowedClasses = resolveTeacherClassNames(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatLogService.list(keyword, feedback, page, size, allowedClasses, className));
        return ResponseEntity.ok(result);
    }

    /**
     * 设置反馈（GOOD / BAD / null 表示取消）。
     * 教师仅可反馈本人带教班级的记录。
     */
    @PutMapping("/{id}/feedback")
    public ResponseEntity<Map<String, Object>> setFeedback(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(
                userId, role, teacherId, className, studentNo);
        List<String> allowedClasses = resolveTeacherClassNames(context);
        String feedback = body.get("feedback") == null ? null : String.valueOf(body.get("feedback"));
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatLogService.setFeedback(id, feedback, allowedClasses).orElse(null));
        return ResponseEntity.ok(result);
    }

    /**
     * 删除单条对话日志。
     * 教师仅可删除本人带教班级的记录。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(
                userId, role, teacherId, className, studentNo);
        List<String> allowedClasses = resolveTeacherClassNames(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", chatLogService.delete(id, allowedClasses));
        return ResponseEntity.ok(result);
    }

    /**
     * 管理员返回 null（不过滤）；教师返回带教班级名列表（可为空）。
     */
    private List<String> resolveTeacherClassNames(RequestUserContext context) {
        if (context.isAdmin()) {
            return null;
        }
        Long tid = context.getTeacherId() != null ? context.getTeacherId() : context.getUserId();
        if (tid == null) {
            return Collections.emptyList();
        }
        return classInfoRepository.findByTeacherId(tid).stream()
                .map(ClassInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
