package com.markingsystem.controller.user;

import com.markingsystem.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/password")
public class UserPasswordController {

    private final UserService userService;

    public UserPasswordController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/change")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId == null) {
            result.put("code", 400);
            result.put("message", "用户未登录");
            return ResponseEntity.badRequest().body(result);
        }

        String error = userService.changePassword(
                parsedUserId,
                request.get("oldPassword"),
                request.get("newPassword")
        );
        if (error != null) {
            result.put("code", 400);
            result.put("message", error);
            return ResponseEntity.badRequest().body(result);
        }

        result.put("code", 200);
        result.put("message", "密码修改成功");
        return ResponseEntity.ok(result);
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
}
