package com.markingsystem.controller.user;

import com.markingsystem.service.StudentEmailBindingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user/email")
public class UserEmailController {

    private final StudentEmailBindingService emailBindingService;

    public UserEmailController(StudentEmailBindingService emailBindingService) {
        this.emailBindingService = emailBindingService;
    }

    @PostMapping("/verification-code")
    public ResponseEntity<Map<String, Object>> sendVerificationCode(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> data = emailBindingService.sendVerificationCode(
                    parseUserId(userId),
                    stringValue(request.get("email"))
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "验证码已发送", "data", data));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "验证码发送失败：" + exception.getMessage()));
        }
    }

    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bindEmail(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> data = emailBindingService.bindEmail(
                    parseUserId(userId),
                    stringValue(request.get("email")),
                    stringValue(request.get("code"))
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "邮箱绑定成功", "data", data));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "邮箱绑定失败：" + exception.getMessage()));
        }
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
