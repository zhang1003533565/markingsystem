package com.markingsystem.controller.admin;

import com.markingsystem.dto.AdminAnalysisOverviewDTO;
import com.markingsystem.dto.BatchAnalysisRequestDTO;
import com.markingsystem.dto.BatchAnalysisTaskDTO;
import com.markingsystem.dto.StudentAnalysisResultDTO;
import com.markingsystem.service.StudentAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/analysis")
public class AdminAnalysisController {

    private final StudentAnalysisService studentAnalysisService;

    public AdminAnalysisController(StudentAnalysisService studentAnalysisService) {
        this.studentAnalysisService = studentAnalysisService;
    }

    @GetMapping("/student-profile")
    public ResponseEntity<Map<String, Object>> analyzeStudent(
            @RequestParam String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        if (!hasStudentAccess(studentNo, userId, role, teacherId, className, headerStudentNo)) {
            return notFound();
        }
        return studentAnalysisService.getLatestAnalysis(studentNo, null)
                .<ResponseEntity<Map<String, Object>>>map(result -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", result
                )))
                .orElseGet(this::notFound);
    }

    @GetMapping("/student-profile/history")
    public ResponseEntity<Map<String, Object>> analysisHistory(
            @RequestParam String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        if (!hasStudentAccess(studentNo, userId, role, teacherId, className, headerStudentNo)) {
            return notFound();
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", studentAnalysisService.getAnalysisHistory(studentNo)
        ));
    }

    @PostMapping("/student-profile/refresh")
    public ResponseEntity<Map<String, Object>> refreshStudent(
            @RequestParam String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        if (!hasStudentAccess(studentNo, userId, role, teacherId, className, headerStudentNo)) {
            return notFound();
        }
        return studentAnalysisService.analyzeAndSaveStudent(studentNo, null)
                .<ResponseEntity<Map<String, Object>>>map(result -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", result
                )))
                .orElseGet(this::notFound);
    }

    /**
     * 教工端单学生画像访问权限校验：仅管理员或该学生所属教师可访问。
     * 无权访问与「学生不存在」统一返回 404，避免泄露学生是否存在。
     */
    private boolean hasStudentAccess(String studentNo, String userId, String role, String teacherId,
                                     String className, String headerStudentNo) {
        AdminAccessSupport.requireAdminOrTeacher(userId, role, teacherId, className, headerStudentNo);
        Long parsedTeacherId = teacherId == null || teacherId.isBlank() ? null : Long.parseLong(teacherId);
        return studentAnalysisService.resolveStudentForStaff(studentNo, role, parsedTeacherId).isPresent();
    }

    private ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.badRequest().body(Map.of(
                "code", 404,
                "message", "未找到学生分析结果"
        ));
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String className,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId) {
        AdminAnalysisOverviewDTO overview = studentAnalysisService.buildOverview(
                keyword,
                className,
                role,
                teacherId == null || teacherId.isBlank() ? null : Long.parseLong(teacherId)
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", overview
        ));
    }

    @PostMapping("/overview/refresh")
    public ResponseEntity<Map<String, Object>> batchRefresh(
            @RequestBody(required = false) BatchAnalysisRequestDTO request,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId) {
        BatchAnalysisTaskDTO task = studentAnalysisService.startBatchAnalyzeAndSave(
                request != null ? request.getKeyword() : null,
                request != null ? request.getClassName() : null,
                role,
                teacherId == null || teacherId.isBlank() ? null : Long.parseLong(teacherId),
                request != null ? request.getStudentNos() : null
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", task,
                "message", "批量分析任务已启动"
        ));
    }

    @GetMapping("/overview/refresh-task/{taskId}")
    public ResponseEntity<Map<String, Object>> taskStatus(@PathVariable String taskId) {
        return studentAnalysisService.getBatchTask(taskId)
                .<ResponseEntity<Map<String, Object>>>map(task -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", task
                )))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "任务不存在"
                )));
    }

    @PostMapping("/overview/refresh-task/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        return studentAnalysisService.cancelBatchTask(taskId)
                .<ResponseEntity<Map<String, Object>>>map(task -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", task,
                        "message", "终止指令已发送"
                )))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "任务不存在"
                )));
    }
}
