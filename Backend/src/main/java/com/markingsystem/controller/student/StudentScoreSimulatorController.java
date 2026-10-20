package com.markingsystem.controller.student;

import com.markingsystem.dto.ScoreReverseRequest;
import com.markingsystem.dto.ScoreSimulateRequest;
import com.markingsystem.entity.Student;
import com.markingsystem.service.ScoreSimulationService;
import com.markingsystem.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 学生端：综测成绩模拟器接口。
 * 只允许登录学生模拟本人成绩，纯只读，不修改任何数据。
 */
@RestController
@RequestMapping("/api/student/score-simulator")
public class StudentScoreSimulatorController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private ScoreSimulationService scoreSimulationService;

    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(
            @RequestBody ScoreSimulateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<Student> studentOpt = resolveStudent(userId, headerStudentNo);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code", 404, "message", "未找到学生信息"));
            }
            result.put("code", 200);
            result.put("data", scoreSimulationService.simulate(studentOpt.get().getStudentNo(), request));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception exception) {
            result.put("code", 500);
            result.put("message", "成绩模拟失败：" + exception.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/reverse")
    public ResponseEntity<Map<String, Object>> reverse(
            @RequestBody ScoreReverseRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<Student> studentOpt = resolveStudent(userId, headerStudentNo);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code", 404, "message", "未找到学生信息"));
            }
            result.put("code", 200);
            result.put("data", scoreSimulationService.reverse(studentOpt.get().getStudentNo(), request));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception exception) {
            result.put("code", 500);
            result.put("message", "反向计算失败：" + exception.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Optional<Student> resolveStudent(String userId, String headerStudentNo) {
        if (userId != null && !userId.isBlank()) {
            return studentService.findByUserId(Long.parseLong(userId.trim()));
        }
        if (headerStudentNo != null && !headerStudentNo.isBlank()) {
            return studentService.findByStudentNo(headerStudentNo.trim());
        }
        return Optional.empty();
    }
}
