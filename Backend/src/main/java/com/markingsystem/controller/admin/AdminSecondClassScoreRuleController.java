package com.markingsystem.controller.admin;

import com.markingsystem.entity.SecondClassScoreRule;
import com.markingsystem.service.SecondClassScoreRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/second-class-score-rules")
public class AdminSecondClassScoreRuleController {

    @Autowired
    private SecondClassScoreRuleService secondClassScoreRuleService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listRules(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", secondClassScoreRuleService.findAll());
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> replaceRules(
            @RequestBody List<SecondClassScoreRule> rules,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", secondClassScoreRuleService.replaceAll(rules));
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
