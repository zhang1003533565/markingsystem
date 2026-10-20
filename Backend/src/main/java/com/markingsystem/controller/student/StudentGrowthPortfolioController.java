package com.markingsystem.controller.student;

import com.markingsystem.entity.Student;
import com.markingsystem.service.StudentGrowthPortfolioService;
import com.markingsystem.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 学生电子成长档案接口（学生端，仅当前登录学生本人可见，接口全部只读）。
 */
@RestController
@RequestMapping("/api/student/growth-portfolio")
public class StudentGrowthPortfolioController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentGrowthPortfolioService growthPortfolioService;

    /** 成长档案概览：基本信息、统计、学年级综测快照、学期均分、二课序列 */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        Map<String, Object> result = new HashMap<>();
        Optional<Student> studentOpt = resolveCurrentStudent(userId, studentNo);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 404,
                    "message", "未找到学生信息"
            ));
        }
        try {
            result.put("code", 200);
            result.put("data", growthPortfolioService.buildSummary(studentOpt.get()));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询成长档案概览失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /** 成长时间轴：课程 / 竞赛 / 荣誉 / 志愿 / 实践 / 技能证书 / 第二课堂，按时间倒序 */
    @GetMapping("/timeline")
    public ResponseEntity<Map<String, Object>> getTimeline(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        Map<String, Object> result = new HashMap<>();
        Optional<Student> studentOpt = resolveCurrentStudent(userId, studentNo);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 404,
                    "message", "未找到学生信息"
            ));
        }
        try {
            List<Map<String, Object>> events = growthPortfolioService.buildTimeline(studentOpt.get());
            result.put("code", 200);
            result.put("data", events);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询成长时间轴失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /** 求职简历素材：教育经历 / 荣誉与竞赛 / 实践经历 / 技能证书 */
    @GetMapping("/resume-materials")
    public ResponseEntity<Map<String, Object>> getResumeMaterials(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        Map<String, Object> result = new HashMap<>();
        Optional<Student> studentOpt = resolveCurrentStudent(userId, studentNo);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 404,
                    "message", "未找到学生信息"
            ));
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sections", growthPortfolioService.buildResumeMaterials(studentOpt.get()));
            data.put("basic", growthPortfolioService.buildSummary(studentOpt.get()).get("basic"));
            result.put("code", 200);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "生成简历素材失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Optional<Student> resolveCurrentStudent(String userId, String studentNo) {
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId != null) {
            Optional<Student> studentOpt = studentService.findByUserId(parsedUserId);
            if (studentOpt.isPresent()) {
                return studentOpt;
            }
        }
        if (studentNo != null && !studentNo.isBlank()) {
            return studentService.findByStudentNo(studentNo);
        }
        return Optional.empty();
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
