package com.markingsystem.controller.teacher;

import com.markingsystem.entity.ClassInfo;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.service.ClassAnswerSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 班级答疑总结接口（教师端）。
 *
 * <p>教师选择自己带教的班级与时间段，让 AI 根据该班学生与 AI 的对话记录
 * 生成班情总结（疑惑 / 问题 / 学情 / 建议）。仅限教师身份，且只能针对自己带教的班级。
 * 每次成功生成会落库，可回看历史记录。
 */
@RestController
@RequestMapping("/api/teacher/class-answer-summary")
public class ClassAnswerSummaryController {

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private ClassAnswerSummaryService summaryService;

    /**
     * 教师可总结的班级列表（仅返回自己带教的班级）。
     */
    @GetMapping("/classes")
    public ResponseEntity<Map<String, Object>> listClasses(
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        List<Map<String, Object>> classes = new ArrayList<>();
        List<ClassInfo> list = classInfoRepository.findByTeacherId(teacherId);
        for (ClassInfo c : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getName());
            classes.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", classes);
        return ResponseEntity.ok(result);
    }

    /**
     * 历史总结列表；可选按班级筛选。
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> listHistory(
            @RequestParam(value = "className", required = false) String className,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", summaryService.listHistory(teacherId, className));
        return ResponseEntity.ok(result);
    }

    /**
     * 查看单条历史总结。
     */
    @GetMapping("/history/{id}")
    public ResponseEntity<Map<String, Object>> getHistory(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        Optional<Map<String, Object>> data = summaryService.getHistory(teacherId, id);
        if (data.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404,
                    "message", "记录不存在或无权查看"
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", data.get());
        return ResponseEntity.ok(result);
    }

    /**
     * 为指定班级生成答疑总结。
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Long teacherId = resolveTeacherId(teacherIdHeader, userIdHeader, userRole);
        if (teacherId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "无法确定老师身份"
            ));
        }

        String className = body == null ? null : body.get("className");
        if (className == null || className.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "请选择班级"
            ));
        }

        // 仅允许总结自己带教的班级
        boolean ownClass = classInfoRepository.findByTeacherId(teacherId).stream()
                .anyMatch(c -> className.equalsIgnoreCase(c.getName().trim()));
        if (!ownClass) {
            return ResponseEntity.status(403).body(Map.of(
                    "code", 403,
                    "message", "只能为本人带教的班级生成总结"
            ));
        }

        String startTime = body.get("startTime");
        String endTime = body.get("endTime");
        String start = isBlank(startTime) ? null : startTime;
        String end = isBlank(endTime) ? null : endTime;

        Map<String, Object> data = summaryService.generate(teacherId, className.trim(), start, end);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long resolveTeacherId(String teacherIdHeader, String userIdHeader, String userRole) {
        if ("TEACHER".equalsIgnoreCase(userRole)) {
            if (teacherIdHeader != null && !teacherIdHeader.isBlank()) {
                try {
                    return Long.parseLong(teacherIdHeader.trim());
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    return Long.parseLong(userIdHeader.trim());
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return null;
    }
}
