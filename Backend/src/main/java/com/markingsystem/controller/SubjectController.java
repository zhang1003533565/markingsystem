package com.markingsystem.controller;

import com.markingsystem.entity.Subject;
import com.markingsystem.service.SubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {
    
    @Autowired
    private SubjectService subjectService;
    
    @GetMapping
    public ResponseEntity<List<Subject>> getAllSubjects() {
        return ResponseEntity.ok(subjectService.findAll());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Subject> getSubjectById(@PathVariable Long id) {
        return subjectService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/code/{subjectCode}")
    public ResponseEntity<Subject> getSubjectByCode(@PathVariable String subjectCode) {
        return subjectService.findBySubjectCode(subjectCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/name/{subjectName}")
    public ResponseEntity<Subject> getSubjectByName(@PathVariable String subjectName) {
        return subjectService.findBySubjectName(subjectName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/type/{subjectType}")
    public ResponseEntity<List<Subject>> getSubjectsByType(@PathVariable String subjectType) {
        return ResponseEntity.ok(subjectService.findBySubjectType(subjectType));
    }
    
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Subject>> getSubjectsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(subjectService.findByStatus(status));
    }
    
    @PostMapping
    public ResponseEntity<Subject> createSubject(@RequestBody Subject subject) {
        // 检查科目代码是否已存在
        if (subjectService.existsBySubjectCode(subject.getSubjectCode())) {
            return ResponseEntity.badRequest().build();
        }
        
        // 检查科目名称是否已存在
        if (subjectService.existsBySubjectName(subject.getSubjectName())) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(subjectService.save(subject));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Subject> updateSubject(@PathVariable Long id, @RequestBody Subject subject) {
        return subjectService.updateSubject(id, subject)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        subjectService.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/batch-delete")
    public ResponseEntity<Void> batchDeleteSubjects(@RequestBody List<Long> ids) {
        subjectService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<Subject> updateSubjectStatus(@PathVariable Long id, @RequestBody Map<String, String> statusMap) {
        String status = statusMap.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return subjectService.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}