package com.markingsystem.controller.admin;

import com.markingsystem.dto.LoginResponse;
import com.markingsystem.service.UserService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserPasswordController {

    private final UserService userService;

    public AdminUserPasswordController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{id}/reset-password")
    public LoginResponse resetPassword(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return userService.resetPasswordToUsername(id)
                .map(user -> LoginResponse.success(null))
                .orElse(LoginResponse.error("重置密码失败"));
    }
}
