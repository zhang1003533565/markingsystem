package com.markingsystem.controller;

import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.service.SubjectScoreRAINServices;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/subject-scores/rain")
public class SubjectScoreRAINController {

    @Autowired
    private SubjectScoreRAINServices subjectScoreRAINServices;

    @GetMapping("/visible")
    public ResponseEntity<?> getVisibleScores(
            @RequestParam(defaultValue = "false") boolean aggregate,
            @RequestParam(required = false) String academicYear,
            @RequestParam(name = "className", required = false) String queryClassName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String headerClassName,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        try {
            RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, headerClassName, studentNo);
            if (aggregate) {
                return ResponseEntity.ok(subjectScoreRAINServices.getVisibleScoreSummary(
                        context,
                        academicYear,
                        queryClassName,
                        page,
                        size
                ));
            }
            return ResponseEntity.ok(subjectScoreRAINServices.getVisibleScores(context));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    /**
     * 根据学生ID查询成绩
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<SubjectScoreRAIN>> getScoresByStudentId(@PathVariable Long studentId) {
        try {
            List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresByStudentId(studentId);
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据科目ID查询成绩
     */
    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<SubjectScoreRAIN>> getScoresBySubjectId(@PathVariable Long subjectId) {
        try {
            List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresBySubjectId(subjectId);
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据学期查询成绩
     */
    @GetMapping("/semester/{semester}")
    public ResponseEntity<List<SubjectScoreRAIN>> getScoresBySemester(@PathVariable String semester) {
        try {
            List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresBySemester(semester);
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据学生ID和学期查询成绩
     */
    @GetMapping("/student/{studentId}/semester/{semester}")
    public ResponseEntity<List<SubjectScoreRAIN>> getScoresByStudentIdAndSemester(
            @PathVariable Long studentId, @PathVariable String semester) {
        try {
            List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresByStudentIdAndSemester(studentId, semester);
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据科目ID和学期查询成绩
     */
    @GetMapping("/subject/{subjectId}/semester/{semester}")
    public ResponseEntity<List<SubjectScoreRAIN>> getScoresBySubjectIdAndSemester(
            @PathVariable Long subjectId, @PathVariable String semester) {
        try {
            List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresBySubjectIdAndSemester(subjectId, semester);
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据学生ID、科目ID和学期查询成绩
     */
    @GetMapping("/student/{studentId}/subject/{subjectId}/semester/{semester}")
    public ResponseEntity<SubjectScoreRAIN> getScoreByStudentIdAndSubjectIdAndSemester(
            @PathVariable Long studentId, @PathVariable Long subjectId, @PathVariable String semester) {
        try {
            Optional<SubjectScoreRAIN> score = subjectScoreRAINServices.getScoreByStudentIdAndSubjectIdAndSemester(studentId, subjectId, semester);
            return score.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除指定学生的所有成绩
     */
    @DeleteMapping("/student/{studentId}")
    public ResponseEntity<Void> deleteScoresByStudentId(@PathVariable Long studentId) {
        try {
            subjectScoreRAINServices.deleteScoresByStudentId(studentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除指定科目的所有成绩
     */
    @DeleteMapping("/subject/{subjectId}")
    public ResponseEntity<Void> deleteScoresBySubjectId(@PathVariable Long subjectId) {
        try {
            subjectScoreRAINServices.deleteScoresBySubjectId(subjectId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除指定学期的所有成绩
     */
    @DeleteMapping("/semester/{semester}")
    public ResponseEntity<Void> deleteScoresBySemester(@PathVariable String semester) {
        try {
            subjectScoreRAINServices.deleteScoresBySemester(semester);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/cleanup/preview")
    public ResponseEntity<Map<String, Object>> previewCleanup(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> data = subjectScoreRAINServices.previewClassSemesterSubjectCleanup(
                    stringValue(request.get("className")),
                    stringValue(request.get("semester")),
                    subjectNames(request.get("subjectNames"))
            );
            return ResponseEntity.ok(Map.of("code", 200, "data", data));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "预览清理失败"));
        }
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupScores(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> data = subjectScoreRAINServices.deleteClassSemesterSubjectScores(
                    stringValue(request.get("className")),
                    stringValue(request.get("semester")),
                    subjectNames(request.get("subjectNames"))
            );
            return ResponseEntity.ok(Map.of("code", 200, "data", data));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "清理成绩失败"));
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> subjectNames(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::stringValue).toList();
        }
        String text = stringValue(value);
        if (text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[,，\\n\\r]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

}
