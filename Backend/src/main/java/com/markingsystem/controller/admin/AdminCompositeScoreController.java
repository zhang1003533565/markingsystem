package com.markingsystem.controller.admin;

import com.markingsystem.entity.CompositeScoreWeight;
import com.markingsystem.service.AdminCompositeScoreService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/composite-scores")
public class AdminCompositeScoreController {

    @Autowired
    private AdminCompositeScoreService adminCompositeScoreService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listCompositeScores(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, userRole, teacherId, null, null);
        Long resolvedTeacherId = resolveTeacherScopeId(context);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.listCompositeScores(academicYear, className, keyword, resolvedTeacherId, page, size));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshCompositeScores(
            @RequestParam String academicYear,
            @RequestParam(required = false) String className,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, userRole, teacherId, null, null);
        Long resolvedTeacherId = resolveTeacherScopeId(context);
        Long resolvedUserId = context.getUserId();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.startRefreshCompositeScores(academicYear, className, resolvedTeacherId, resolvedUserId, userName, userRole));
        result.put("message", "综测刷新任务已启动");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/refresh-task/{taskId}")
    public ResponseEntity<Map<String, Object>> getRefreshTask(@PathVariable String taskId) {
        return adminCompositeScoreService.getRefreshTask(taskId)
                .<ResponseEntity<Map<String, Object>>>map(task -> ResponseEntity.ok(Map.of(
                        "code", 200,
                        "data", task
                )))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "任务不存在"
                )));
    }

    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> getCompositeScoreMeta(
            @RequestParam String academicYear,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, userRole, teacherId, null, null);
        Long resolvedTeacherId = resolveTeacherScopeId(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.getSnapshotMeta(academicYear, resolvedTeacherId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getCompositeScoreHistory(
            @RequestParam String academicYear,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, userRole, teacherId, null, null);
        Long resolvedTeacherId = resolveTeacherScopeId(context);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.listRefreshHistory(academicYear, resolvedTeacherId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{studentNo}/extra-details")
    public ResponseEntity<Map<String, Object>> listExtraScoreDetails(
            @PathVariable String studentNo,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String category,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, userRole, teacherId, null, null);
        Long resolvedTeacherId = resolveTeacherScopeId(context);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.listExtraScoreDetails(studentNo, academicYear, category, resolvedTeacherId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{studentNo}/base-details")
    public ResponseEntity<Map<String, Object>> listBaseScoreDetails(
            @PathVariable String studentNo,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String category,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, userRole, teacherId, null, null);
        Long resolvedTeacherId = resolveTeacherScopeId(context);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.listBaseScoreDetails(studentNo, academicYear, category, resolvedTeacherId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/weights")
    public ResponseEntity<Map<String, Object>> getWeightConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.getWeightConfig());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/weights")
    public ResponseEntity<Map<String, Object>> saveWeightConfig(@RequestBody CompositeScoreWeight weight) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", adminCompositeScoreService.saveWeightConfig(weight));
        return ResponseEntity.ok(result);
    }

    private Long resolveTeacherScopeId(RequestUserContext context) {
        if (context == null || !context.isTeacher()) {
            return null;
        }
        return context.getTeacherId() != null ? context.getTeacherId() : context.getUserId();
    }
}
