package com.markingsystem.controller.student;

import com.markingsystem.dto.CreateAnnouncementRequest;
import com.markingsystem.service.AnnouncementService;
import com.markingsystem.service.AnnouncementService.EmailDeliveryRequiredException;
import com.markingsystem.util.RequestUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementController.class);

    @Autowired
    private AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Map<String, Object> pageData = (context.isStudent() || context.isClassCommittee())
                ? announcementService.getInbox(context, page, size)
                : announcementService.getPublishedAnnouncements(context, page, size);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", pageData
        ));
    }

    @GetMapping("/published")
    public ResponseEntity<Map<String, Object>> getPublishedAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Map<String, Object> pageData = announcementService.getPublishedAnnouncements(context, page, size);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", pageData
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncementDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", announcementService.getAnnouncementDetail(id, context)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestBody CreateAnnouncementRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", announcementService.createAnnouncement(request, context)));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<Map<String, Object>> getAnnouncementRecipients(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", announcementService.getAnnouncementRecipients(id, context)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        announcementService.markAsRead(id, context);
        return ResponseEntity.ok(Map.of("code", 200, "message", "已确认阅读"));
    }

    @PostMapping("/{id}/remind")
    public ResponseEntity<Map<String, Object>> remindUnread(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "TEACHER") String senderType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", announcementService.remindUnread(id, context, senderType)));
    }

    @GetMapping("/email-tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getEmailTaskProgress(@PathVariable String taskId) {
        return announcementService.getEmailTaskProgress(taskId)
                .<ResponseEntity<Map<String, Object>>>map(progress -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", progress
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", 404,
                        "message", "邮件任务不存在或已过期"
                )));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(EmailDeliveryRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleEmailDeliveryRequired(EmailDeliveryRequiredException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        result.put("data", Map.of("emailResult", exception.getEmailResult()));
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception exception) {
        log.error("公告操作失败", exception);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", exception.getMessage() == null || exception.getMessage().isBlank()
                ? "公告操作失败，请稍后重试"
                : exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
