package com.markingsystem.controller;

import com.markingsystem.service.AssistantService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired
    private AssistantService assistantService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {

        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, userRole, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();

        try {
            if (ctx.isAdmin()) {
                result = assistantService.getAdminSummary(ctx);
            } else if (ctx.isTeacher()) {
                result = assistantService.getTeacherSummary(ctx);
            } else {
                result = assistantService.getStudentSummary(ctx);
            }
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
