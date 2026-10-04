package com.markingsystem.controller.admin;

import com.markingsystem.entity.SystemEmail;
import com.markingsystem.service.SystemEmailService;
import com.markingsystem.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system-emails")
public class AdminSystemEmailController {

    @Autowired
    private SystemEmailService systemEmailService;

    @Autowired
    private SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<?> list() {
        List<SystemEmail> emails = systemEmailService.list();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", emails.stream().map(e -> Map.of(
                        "id", e.getId(),
                        "email", e.getEmail(),
                        "isActive", e.getIsActive(),
                        "isDefault", e.getIsDefault(),
                        "createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
                )).toList()
        ));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String password = body.get("password");
            SystemEmail result = systemEmailService.add(email, password);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "添加成功",
                    "data", Map.of(
                            "id", result.getId(),
                            "email", result.getEmail(),
                            "isActive", result.getIsActive(),
                            "isDefault", result.getIsDefault()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id) {
        try {
            systemEmailService.remove(id);
            return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<?> setDefault(@PathVariable Long id) {
        try {
            SystemEmail result = systemEmailService.setDefault(id);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "设置默认成功",
                    "data", Map.of(
                            "id", result.getId(),
                            "email", result.getEmail(),
                            "isDefault", result.getIsDefault()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @GetMapping("/email-binding-policy")
    public ResponseEntity<?> getEmailBindingPolicy() {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "forceEmailBindingRequired", systemSettingService.isForceEmailBindingRequired()
                )
        ));
    }

    @PutMapping("/email-binding-policy")
    public ResponseEntity<?> updateEmailBindingPolicy(@RequestBody Map<String, Object> body) {
        try {
            boolean required = parseRequiredFlag(body.get("forceEmailBindingRequired"));
            boolean saved = systemSettingService.setForceEmailBindingRequired(required);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "保存成功",
                    "data", Map.of("forceEmailBindingRequired", saved)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    private boolean parseRequiredFlag(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase();
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        throw new IllegalArgumentException("请选择是否强制绑定邮箱");
    }
}
