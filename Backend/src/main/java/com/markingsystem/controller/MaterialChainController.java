package com.markingsystem.controller;

import com.markingsystem.dto.MaterialChainUploadRequest;
import com.markingsystem.service.MaterialChainService;
import com.markingsystem.util.RequestUserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/material-chain")
public class MaterialChainController {

    private final MaterialChainService materialChainService;

    public MaterialChainController(MaterialChainService materialChainService) {
        this.materialChainService = materialChainService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "applicationId", required = false) Long applicationId,
            @RequestParam(value = "certNo", required = false) String certNo,
            @RequestParam(value = "certName", required = false) String certName,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        MaterialChainUploadRequest req = new MaterialChainUploadRequest();
        req.setApplicationId(applicationId);
        req.setCertNo(certNo);
        req.setCertName(certName);
        try {
            Map<String, Object> result = materialChainService.upload(ctx, file, req);
            return ResponseEntity.ok(Map.of("code", 200, "data", result));
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", 400);
            err.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @PostMapping("/modify/{id}")
    public ResponseEntity<Map<String, Object>> modify(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "applicationId", required = false) Long applicationId,
            @RequestParam(value = "certNo", required = false) String certNo,
            @RequestParam(value = "certName", required = false) String certName,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        MaterialChainUploadRequest req = new MaterialChainUploadRequest();
        req.setApplicationId(applicationId);
        req.setCertNo(certNo);
        req.setCertName(certName);
        try {
            Map<String, Object> result = materialChainService.modify(id, ctx, file, req);
            return ResponseEntity.ok(Map.of("code", 200, "data", result));
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", 400);
            err.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @PostMapping("/review/{id}")
    public ResponseEntity<Map<String, Object>> review(
            @PathVariable Long id,
            @RequestParam("stage") String stage,
            @RequestParam("pass") String pass,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = materialChainService.review(id, stage, pass, comment, ctx);
        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    @GetMapping("/trace/{chainCode}")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable String chainCode) {
        List<Map<String, Object>> trace = materialChainService.traceByChainCode(chainCode);
        return ResponseEntity.ok(Map.of("code", 200, "data", trace));
    }

    @GetMapping("/trace-by-file/{id}")
    public ResponseEntity<Map<String, Object>> traceByFile(@PathVariable Long id) {
        List<Map<String, Object>> trace = materialChainService.traceByFileId(id);
        return ResponseEntity.ok(Map.of("code", 200, "data", trace));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "data", materialChainService.getFileDetail(id)));
    }

    @GetMapping("/by-code/{chainCode}")
    public ResponseEntity<Map<String, Object>> getDetailByCode(@PathVariable String chainCode) {
        return ResponseEntity.ok(Map.of("code", 200, "data", materialChainService.getFileDetailByChainCode(chainCode)));
    }

    @GetMapping("/list/student")
    public ResponseEntity<Map<String, Object>> listByStudent(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        RequestUserContext ctx = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        return ResponseEntity.ok(Map.of("code", 200, "data", materialChainService.listByStudent(ctx.getStudentNo())));
    }

    @GetMapping("/list/application/{applicationId}")
    public ResponseEntity<Map<String, Object>> listByApplication(@PathVariable Long applicationId) {
        return ResponseEntity.ok(Map.of("code", 200, "data", materialChainService.listByApplication(applicationId)));
    }

    @GetMapping("/check-cert-duplicate")
    public ResponseEntity<Map<String, Object>> checkCertDuplicate(
            @RequestParam String certNo,
            @RequestParam(value = "excludeStudentNo", required = false) String excludeStudentNo) {
        return ResponseEntity.ok(Map.of("code", 200, "data", materialChainService.checkCertDuplicate(certNo, excludeStudentNo)));
    }

    @PostMapping("/detect-tamper")
    public ResponseEntity<Map<String, Object>> detectTamper(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(Map.of("code", 200, "data", materialChainService.detectImageTamperQuick(file)));
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", 400);
            err.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", 400);
        err.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(err);
    }
}
