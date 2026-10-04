package com.markingsystem.controller.admin;

import com.markingsystem.dto.SecondClassScoreImportResponseDTO;
import com.markingsystem.dto.SecondClassScorePreviewResponseDTO;
import com.markingsystem.exception.SubjectRAINException;
import com.markingsystem.service.SecondClassScoreImportService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/second-class-scores")
public class AdminSecondClassScoreImportController {

    @Autowired
    private SecondClassScoreImportService secondClassScoreImportService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listImportedScores(
            @RequestParam("academicYear") String academicYear,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("无权查看二课分数据");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", secondClassScoreImportService.listImportedScores(academicYear, keyword, context, page, size));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateImportedScore(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("无权修改二课成绩");
        }
        Object rawScore = body == null ? null : body.get("secondClassScore");
        Double secondClassScore;
        try {
            secondClassScore = rawScore instanceof Number
                    ? ((Number) rawScore).doubleValue()
                    : Double.valueOf(String.valueOf(rawScore));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("二课分必须是有效数字");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", secondClassScoreImportService.updateImportedScore(id, secondClassScore, context));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/preview")
    public ResponseEntity<SecondClassScorePreviewResponseDTO> preview(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) throws IOException {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("无权预览二课分导入文件");
        }
        return ResponseEntity.ok(secondClassScoreImportService.preview(file));
    }

    @PostMapping("/import")
    public ResponseEntity<SecondClassScoreImportResponseDTO> importScores(
            @RequestParam("file") MultipartFile file,
            @RequestParam("academicYear") String academicYear,
            @RequestParam("studentNoColumn") String studentNoColumn,
            @RequestParam("nameColumn") String nameColumn,
            @RequestParam("classNameColumn") String classNameColumn,
            @RequestParam("scoreColumn") String scoreColumn,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) throws IOException {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("无权导入二课分数据");
        }
        return ResponseEntity.ok(secondClassScoreImportService.importScores(
                file,
                academicYear,
                studentNoColumn,
                nameColumn,
                classNameColumn,
                scoreColumn,
                context
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(SubjectRAINException.class)
    public ResponseEntity<Map<String, Object>> handleSubjectImport(SubjectRAINException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "文件处理失败: " + exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", exception.getMessage() == null ? "导入失败，请检查后端日志" : exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
