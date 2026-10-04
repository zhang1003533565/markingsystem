package com.markingsystem.controller;

import com.markingsystem.dto.BatchEmailRequest;
import com.markingsystem.dto.BatchEmailResult;
import com.markingsystem.dto.EmailRequest;
import com.markingsystem.service.EmailNotificationService;
import com.markingsystem.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Autowired
    private EmailNotificationService emailNotificationService;

    @Autowired
    private UserService userService;

    @PostMapping("/send")
    public ResponseEntity<?> sendEmail(
            @RequestBody EmailRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (request.getToEmail() == null || request.getToEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "收件人邮箱不能为空"));
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "邮件标题不能为空"));
        }
        if (request.getLinkUrl() == null || request.getLinkUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "链接地址不能为空"));
        }
        if (!isValidEmail(request.getToEmail()) || !isSafeUrl(request.getLinkUrl())) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "收件人或链接地址不合法"));
        }

        String linkText = request.getLinkText() != null && !request.getLinkText().isBlank()
                ? request.getLinkText() : "点击查看";
        String senderEmail = resolveSenderEmail(request.getSenderEmail(), userId, role);

        BatchEmailResult result = emailNotificationService.enqueueBatchEmail(
                List.of(request.getToEmail()),
                request.getTitle(),
                null,
                linkText,
                request.getLinkUrl(),
                normalizeSenderType(request.getSenderType(), role),
                senderEmail
        );

        if (result.getTaskId() == null || result.getTaskId().isBlank()) {
            return ResponseEntity.ok(Map.of("code", 500, "message", result.getMessage()));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "邮件任务已提交", "data", result));
    }

    @PostMapping("/send/batch")
    public ResponseEntity<?> sendBatchEmail(
            @RequestBody BatchEmailRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (request.getToEmails() == null || request.getToEmails().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "收件人列表不能为空"));
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "邮件标题不能为空"));
        }
        if (request.getToEmails().size() > 100
                || request.getToEmails().stream().anyMatch(email -> !isValidEmail(email))) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "收件人列表包含无效邮箱或超过 100 个"));
        }

        String linkText = request.getLinkText() != null && !request.getLinkText().isBlank()
                ? request.getLinkText() : "点击查看";
        String linkUrl = request.getLinkUrl() != null ? request.getLinkUrl() : "";
        if (!linkUrl.isBlank() && !isSafeUrl(linkUrl)) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "链接地址不合法"));
        }

        BatchEmailResult result = emailNotificationService.enqueueBatchEmail(
                request.getToEmails(),
                request.getTitle(),
                request.getContent(),
                linkText,
                linkUrl,
                normalizeSenderType(request.getSenderType(), role),
                resolveSenderEmail(request.getSenderEmail(), userId, role)
        );

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", result.getTaskId() == null ? result.getMessage() : "邮件任务已提交",
                "data", result
        ));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getEmailTaskProgress(@PathVariable String taskId) {
        return emailNotificationService.getTaskProgress(taskId)
                .<ResponseEntity<?>>map(progress -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", progress
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "code", 404,
                        "message", "邮件任务不存在或已过期"
                )));
    }

    private String resolveSenderEmail(String requestedEmail, String userId, String role) {
        if ("TEACHER".equalsIgnoreCase(role)) {
            try {
                Long parsedUserId = Long.valueOf(userId);
                return userService.findById(parsedUserId)
                        .map(user -> user.getEmail())
                        .filter(email -> email != null && !email.isBlank())
                        .orElseThrow(() -> new IllegalArgumentException("当前教师未配置发件邮箱"));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("缺少当前用户信息");
            }
        }
        return requestedEmail;
    }

    private String normalizeSenderType(String senderType, String role) {
        if ("TEACHER".equalsIgnoreCase(role)) {
            return "TEACHER";
        }
        String normalized = senderType == null ? "" : senderType.trim().toUpperCase(Locale.ROOT);
        return "TEACHER".equals(normalized) || "SYSTEM".equals(normalized) ? normalized : "SYSTEM";
    }

    private boolean isValidEmail(String email) {
        return email != null && email.length() <= 254 && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private boolean isSafeUrl(String url) {
        String normalized = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }
}
