package com.markingsystem.controller.admin;

import com.markingsystem.entity.College;
import com.markingsystem.service.CollegeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin 学院管理接口
 * 提供学院信息的增删改查功能
 */
@RestController
@RequestMapping("/api/admin/colleges")
public class AdminCollegeController {
    
    @Autowired
    private CollegeService collegeService;
    
    /**
     * 获取所有学院
     */
    @GetMapping
    public ResponseEntity<List<College>> getAllColleges(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(collegeService.findAll());
    }
    
    /**
     * 根据ID查询学院
     */
    @GetMapping("/{id}")
    public ResponseEntity<College> getCollegeById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return collegeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<Map<String, Object>> getDeleteImpact(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", collegeService.getDeleteImpact(id)
        ));
    }
    
    /**
     * 搜索学院
     */
    @GetMapping("/search")
    public ResponseEntity<List<College>> searchColleges(
            @RequestParam String keyword,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(collegeService.search(keyword));
    }
    
    /**
     * 新增学院
     */
    @PostMapping
    public ResponseEntity<College> createCollege(
            @RequestBody College college,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(collegeService.save(college));
    }
    
    /**
     * 更新学院信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<College> updateCollege(
            @PathVariable Long id,
            @RequestBody College college,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return collegeService.update(id, college)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 删除学院
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCollege(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean deleteUsers,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        collegeService.deleteById(id, force, deleteUsers);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "删除成功"
        ));
    }
    
    /**
     * 切换学院状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<College> toggleStatus(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return collegeService.toggleStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 409);
        result.put("message", exception.getMessage());
        return ResponseEntity.status(409).body(result);
    }
}
