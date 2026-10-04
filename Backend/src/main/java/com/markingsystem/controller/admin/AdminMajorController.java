package com.markingsystem.controller.admin;

import com.markingsystem.entity.Major;
import com.markingsystem.service.MajorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin 专业管理接口
 * 提供专业信息的增删改查功能
 */
@RestController
@RequestMapping("/api/admin/majors")
public class AdminMajorController {
    
    @Autowired
    private MajorService majorService;
    
    /**
     * 获取所有专业
     */
    @GetMapping
    public ResponseEntity<?> getAllMajors(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long collegeId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        if (page != null || size != null || collegeId != null) {
            return ResponseEntity.ok(majorService.findPage(page == null ? 0 : page, size == null ? 10 : size, collegeId));
        }
        return ResponseEntity.ok(majorService.findAll());
    }

    @GetMapping("/options")
    public ResponseEntity<List<Map<String, Object>>> getMajorOptions(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(majorService.findOptions());
    }
    
    /**
     * 根据ID查询专业
     */
    @GetMapping("/{id}")
    public ResponseEntity<Major> getMajorById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return majorService.findById(id)
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
                "data", majorService.getDeleteImpact(id)
        ));
    }
    
    /**
     * 根据学院ID查询专业
     */
    @GetMapping("/college/{collegeId}")
    public ResponseEntity<List<Major>> getMajorsByCollege(
            @PathVariable Long collegeId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(majorService.findByCollegeId(collegeId));
    }
    
    /**
     * 搜索专业
     */
    @GetMapping("/search")
    public ResponseEntity<List<Major>> searchMajors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long collegeId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(majorService.search(keyword, collegeId));
    }
    
    /**
     * 新增专业
     */
    @PostMapping
    public ResponseEntity<Major> createMajor(
            @RequestBody Major major,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(majorService.save(major));
    }
    
    /**
     * 更新专业信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Major> updateMajor(
            @PathVariable Long id,
            @RequestBody Major major,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return majorService.update(id, major)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 删除专业
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMajor(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean deleteUsers,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        majorService.deleteById(id, force, deleteUsers);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "删除成功"
        ));
    }
    
    /**
     * 切换专业状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Major> toggleStatus(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return majorService.toggleStatus(id)
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
