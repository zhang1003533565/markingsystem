package com.markingsystem.controller.user;

import com.markingsystem.entity.Student;
import com.markingsystem.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User 学生查询接口
 * 提供学生信息的查询功能（只读）
 */
@RestController
@RequestMapping("/api/user/students")
public class UserStudentController {
    
    @Autowired
    private StudentService studentService;
    
    /**
     * 分页查询学生列表
     */
    @GetMapping
    public ResponseEntity<Page<Student>> getStudents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Student> students = studentService.findStudentsWithFilter(keyword, college, grade, null, pageable);
        return ResponseEntity.ok(students);
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
     * 获取老师的学生列表
     */
    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<Student>> getStudentsByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(studentService.findByTeacherId(teacherId));
    }
    
    /**
     * 获取班级的学生列表
     */
    @GetMapping("/class/{className}")
    public ResponseEntity<List<Student>> getStudentsByClass(@PathVariable String className) {
        return ResponseEntity.ok(studentService.findByClassName(className));
    }
}
