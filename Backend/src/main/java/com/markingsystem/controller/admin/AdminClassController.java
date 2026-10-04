package com.markingsystem.controller.admin;

import com.markingsystem.entity.ClassInfo;
import com.markingsystem.service.ClassInfoService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin 班级管理接口
 * 提供班级信息的增删改查功能
 */
@RestController
@RequestMapping("/api/admin/classes")
public class AdminClassController {
    
    @Autowired
    private ClassInfoService classInfoService;
    
    /**
     * 获取所有班级
     */
    @GetMapping
    public ResponseEntity<List<ClassInfo>> getAllClasses(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (context.isAdmin()) {
            return ResponseEntity.ok(classInfoService.findAll());
        }
        if (context.isTeacher()) {
            return ResponseEntity.ok(classInfoService.findManagedByTeacherId(context.getTeacherId()));
        }
        throw new IllegalArgumentException("无权访问班级列表");
    }

    @GetMapping("/options")
    public ResponseEntity<List<Map<String, Object>>> getClassOptions(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        List<ClassInfo> classes;
        if (context.isAdmin()) {
            classes = classInfoService.findAll();
        } else if (context.isTeacher()) {
            classes = classInfoService.findManagedByTeacherId(context.getTeacherId());
        } else {
            throw new IllegalArgumentException("无权访问班级列表");
        }
        return ResponseEntity.ok(classes.stream().map(this::toClassOption).toList());
    }
    
    /**
     * 根据ID查询班级
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClassInfo> getClassById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin()) {
            throw new IllegalArgumentException("仅管理员可查看班级详情");
        }
        return classInfoService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 根据学院ID查询班级
     */
    @GetMapping("/college/{collegeId}")
    public ResponseEntity<List<ClassInfo>> getClassesByCollege(
            @PathVariable Long collegeId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin()) {
            throw new IllegalArgumentException("仅管理员可按学院查询班级");
        }
        return ResponseEntity.ok(classInfoService.findByCollegeId(collegeId));
    }
    
    /**
     * 根据专业ID查询班级
     */
    @GetMapping("/major/{majorId}")
    public ResponseEntity<List<ClassInfo>> getClassesByMajor(
            @PathVariable Long majorId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin()) {
            throw new IllegalArgumentException("仅管理员可按专业查询班级");
        }
        return ResponseEntity.ok(classInfoService.findByMajorId(majorId));
    }
    
    /**
     * 搜索班级
     */
    @GetMapping("/search")
    public ResponseEntity<List<ClassInfo>> searchClasses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) String grade,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin()) {
            throw new IllegalArgumentException("仅管理员可搜索班级");
        }
        return ResponseEntity.ok(classInfoService.search(keyword, collegeId, majorId, grade));
    }
    
    /**
     * 新增班级
     */
    @PostMapping
    public ResponseEntity<ClassInfo> createClass(
            @RequestBody ClassInfo classInfo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        if (context.isTeacher()) {
            Long currentTeacherId = context.getTeacherId() != null ? context.getTeacherId() : context.getUserId();
            if (currentTeacherId == null) {
                throw new IllegalArgumentException("老师身份缺少 teacherId，无法创建班级");
            }
            // 老师创建班级时，强制归属到当前老师，避免越权指定其他老师。
            classInfo.setTeacherId(currentTeacherId);
        }
        return ResponseEntity.ok(classInfoService.save(classInfo));
    }
    
    /**
     * 更新班级信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClassInfo> updateClass(
            @PathVariable Long id,
            @RequestBody ClassInfo classInfo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        if (context.isTeacher()) {
            Long currentTeacherId = resolveCurrentTeacherId(context);
            assertTeacherOwnsClass(id, currentTeacherId);
            // 老师编辑时固定归属到自己，防止改绑到其他老师。
            classInfo.setTeacherId(currentTeacherId);
        }
        return classInfoService.update(id, classInfo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 删除班级
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClass(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        classInfoService.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 切换班级状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ClassInfo> toggleStatus(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        if (context.isTeacher()) {
            assertTeacherOwnsClass(id, resolveCurrentTeacherId(context));
        }
        return classInfoService.toggleStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Long resolveCurrentTeacherId(RequestUserContext context) {
        Long currentTeacherId = context.getTeacherId() != null ? context.getTeacherId() : context.getUserId();
        if (currentTeacherId == null) {
            throw new IllegalArgumentException("老师身份缺少 teacherId，无法执行该操作");
        }
        return currentTeacherId;
    }

    private void assertTeacherOwnsClass(Long classId, Long teacherId) {
        ClassInfo target = classInfoService.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("班级不存在"));
        if (target.getTeacherId() == null || !teacherId.equals(target.getTeacherId())) {
            throw new IllegalArgumentException("仅可操作自己负责的班级");
        }
    }

    private Map<String, Object> toClassOption(ClassInfo classInfo) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", classInfo.getId());
        item.put("name", classInfo.getName());
        item.put("grade", classInfo.getGrade());
        item.put("status", classInfo.getStatus());
        item.put("majorId", classInfo.getMajorId());
        item.put("collegeId", classInfo.getCollegeId());
        item.put("teacherId", classInfo.getTeacherId());
        return item;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
