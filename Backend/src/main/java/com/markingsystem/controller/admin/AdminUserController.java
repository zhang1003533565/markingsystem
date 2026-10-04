package com.markingsystem.controller.admin;

import com.markingsystem.entity.User;
import com.markingsystem.service.ProofFileStorageService;
import com.markingsystem.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProofFileStorageService proofFileStorageService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        Long id = Long.parseLong(userId);
        return userService.findUserSummaryById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/email-config")
    public ResponseEntity<Map<String, Object>> updateCurrentUserEmailConfig(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        Long id = Long.parseLong(userId);
        userService.updateEmailConfig(id, body.get("email"), body.get("password"))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return userService.findUserSummaryById(id)
                .map(summary -> {
                    summary.put("message", "邮箱配置保存成功");
                    return ResponseEntity.ok(summary);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/phone")
    public ResponseEntity<Map<String, Object>> updateCurrentUserPhone(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        Long id = Long.parseLong(userId);
        String phone = body.get("phone");
        if (phone != null) {
            phone = phone.trim();
            if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请输入正确的手机号码"));
            }
        }
        Optional<User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 404, "message", "未找到用户"));
        }
        User user = userOpt.get();
        user.setPhone(phone == null || phone.isEmpty() ? null : phone);
        userService.save(user);
        return userService.findUserSummaryById(id)
                .map(summary -> {
                    summary.put("message", "手机号保存成功");
                    return ResponseEntity.ok(summary);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        Long id = Long.parseLong(userId);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请选择头像图片"));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp")) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "头像仅支持 JPG、PNG、WEBP 图片"));
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "头像图片不能超过 5MB"));
        }

        try {
            String extension = resolveImageExtension(file.getOriginalFilename(), contentType);
            String avatarUrl = proofFileStorageService.upload(file, "avatars", extension);
            Optional<User> userOpt = userService.findById(id);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code", 404, "message", "未找到用户"));
            }
            User user = userOpt.get();
            user.setAvatarUrl(avatarUrl);
            userService.save(user);
            return ResponseEntity.ok(Map.of("code", 200, "message", "头像上传成功", "data", Map.of("avatarUrl", avatarUrl)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "头像上传失败：" + e.getMessage()));
        }
    }

    private String resolveImageExtension(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return ".png";
        if (name.endsWith(".webp")) return ".webp";
        return ".jpg";
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String filterRole,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(userService.findUserSummariesPage(page, pageSize, keyword, filterRole));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "用户创建成功",
                "data", userService.createUser(body)
        ));
    }

    @GetMapping("/teachers")
    public ResponseEntity<List<User>> getTeachers(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(userService.findTeachers());
    }

    @GetMapping("/committees")
    public ResponseEntity<List<User>> getClassCommittees(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(userService.findClassCommittees());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(Exception exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage() == null ? "请求参数不正确" : exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
}
