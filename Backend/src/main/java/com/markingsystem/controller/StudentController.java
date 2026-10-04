package com.markingsystem.controller;

import com.markingsystem.entity.Student;
import com.markingsystem.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    
    @Autowired
    private StudentService studentService;
    
    @GetMapping
    public ResponseEntity<List<Student>> getAllStudents() {
        return ResponseEntity.ok(studentService.findAll());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable Long id) {
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/no/{studentNo}")
    public ResponseEntity<Student> getStudentByNo(@PathVariable String studentNo) {
        return studentService.findByStudentNo(studentNo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Student> createStudent(@RequestBody Student student) {
        return ResponseEntity.ok(studentService.save(student));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody Student student) {
        return studentService.updateStudent(id, student.getName(), student.getClassName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    // 获取老师的学生列表
    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<Student>> getStudentsByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(studentService.findByTeacherId(teacherId));
    }
    
    // 获取班级的学生列表
    @GetMapping("/class/{className}")
    public ResponseEntity<List<Student>> getStudentsByClass(@PathVariable String className) {
        return ResponseEntity.ok(studentService.findByClassName(className));
    }
    
    // 为学生分配老师
    @PostMapping("/{studentId}/assign-teacher/{teacherId}")
    public ResponseEntity<Student> assignTeacher(@PathVariable Long studentId, @PathVariable Long teacherId) {
        return studentService.assignTeacher(studentId, teacherId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
