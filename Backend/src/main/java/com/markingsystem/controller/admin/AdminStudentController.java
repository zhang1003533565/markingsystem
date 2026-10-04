package com.markingsystem.controller.admin;

import com.markingsystem.entity.Student;
import com.markingsystem.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin 学生管理接口
 * 提供学生信息的增删改查功能
 */
@RestController
@RequestMapping("/api/admin/students")
public class AdminStudentController {
    
    @Autowired
    private StudentService studentService;
    
    /**
     * 分页查询学生列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStudents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String committeeStatus,
            @RequestParam(required = false, defaultValue = "false") boolean committeeOnly,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Long resolvedTeacherId = null;
        if ("TEACHER".equalsIgnoreCase(userRole)) {
            String teacherValue = (teacherId != null && !teacherId.isBlank()) ? teacherId : userId;
            if (teacherValue != null && !teacherValue.isBlank()) {
                resolvedTeacherId = Long.parseLong(teacherValue);
            }
        }
        String resolvedCommitteeStatus = committeeStatus;
        if ((resolvedCommitteeStatus == null || resolvedCommitteeStatus.isBlank()) && committeeOnly) {
            resolvedCommitteeStatus = "CLASS_COMMITTEE";
        }
        Page<Map<String, Object>> students = studentService.findStudentListRowsWithFilter(keyword, college, major, grade, className, resolvedCommitteeStatus, resolvedTeacherId, pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("content", students.getContent());
        result.put("totalElements", students.getTotalElements());
        result.put("totalPages", students.getTotalPages());
        return ResponseEntity.ok(result);
    }
    
    /**
     * 根据ID查询学生
     */
    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable Long id) {
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 根据学号查询学生
     */
    @GetMapping("/no/{studentNo}")
    public ResponseEntity<Student> getStudentByNo(@PathVariable String studentNo) {
        return studentService.findByStudentNo(studentNo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 新增学生
     */
    @PostMapping
    public ResponseEntity<Student> createStudent(@RequestBody Student student) {
        student.setId(null);
        return ResponseEntity.ok(studentService.save(student));
    }
    
    /**
     * 更新学生信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody Student student) {
        return studentService.updateStudent(id, student)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 删除学生
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Long resolvedTeacherId = null;
        if ("TEACHER".equalsIgnoreCase(userRole)) {
            String teacherValue = (teacherId != null && !teacherId.isBlank()) ? teacherId : userId;
            if (teacherValue == null || teacherValue.isBlank()) {
                throw new IllegalArgumentException("老师身份缺少 teacherId，无法删除学生");
            }
            resolvedTeacherId = Long.parseLong(teacherValue);
        } else if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new IllegalArgumentException("无权删除学生");
        }
        studentService.deleteById(id, resolvedTeacherId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetStudentPassword(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Long resolvedTeacherId = null;
        if ("TEACHER".equalsIgnoreCase(userRole)) {
            String teacherValue = (teacherId != null && !teacherId.isBlank()) ? teacherId : userId;
            if (teacherValue == null || teacherValue.isBlank()) {
                throw new IllegalArgumentException("老师身份缺少 teacherId，无法重置学生密码");
            }
            resolvedTeacherId = Long.parseLong(teacherValue);
        } else if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new IllegalArgumentException("无权重置学生密码");
        }

        String account = studentService.resetStudentPasswordToAccount(id, resolvedTeacherId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "密码已重置为学生账号");
        result.put("account", account);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 批量删除学生
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Void> batchDeleteStudents(
            @RequestBody List<Long> ids,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new IllegalArgumentException("仅管理员可批量删除学生");
        }
        studentService.batchDelete(ids);
        return ResponseEntity.ok().build();
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
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 409);
        result.put("message", "学生数据与现有记录冲突，请检查学号是否已经存在");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }
}
