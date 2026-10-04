package com.markingsystem.controller.admin;

import com.markingsystem.entity.AcademicYear;
import com.markingsystem.service.AcademicYearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/academic-years")
public class AdminAcademicYearController {

    @Autowired
    private AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<List<AcademicYear>> getAllAcademicYears(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(academicYearService.findAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<AcademicYear>> getActiveAcademicYears(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(academicYearService.findActive());
    }

    @GetMapping("/current")
    public ResponseEntity<AcademicYear> getCurrentAcademicYear(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, studentNo);
        return academicYearService.findCurrent()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createAcademicYear(
            @RequestBody AcademicYear academicYear,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        try {
            return ResponseEntity.ok(academicYearService.save(academicYear));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAcademicYear(
            @PathVariable Long id,
            @RequestBody AcademicYear academicYear,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        try {
            return academicYearService.update(id, academicYear)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAcademicYear(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        academicYearService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AcademicYear> toggleStatus(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return academicYearService.toggleStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/current")
    public ResponseEntity<AcademicYear> setCurrent(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        return academicYearService.setCurrent(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
