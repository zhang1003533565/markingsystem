package com.markingsystem.service;

import com.markingsystem.dto.MaterialChainUploadRequest;
import com.markingsystem.entity.MaterialChainEvent;
import com.markingsystem.entity.MaterialChainFile;
import com.markingsystem.repository.MaterialChainEventRepository;
import com.markingsystem.repository.MaterialChainFileRepository;
import com.markingsystem.util.RequestUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MaterialChainService {

    private static final Logger log = LoggerFactory.getLogger(MaterialChainService.class);

    private final MaterialChainFileRepository chainFileRepository;
    private final MaterialChainEventRepository chainEventRepository;
    private final ProofFileStorageService proofFileStorageService;

    public MaterialChainService(
            MaterialChainFileRepository chainFileRepository,
            MaterialChainEventRepository chainEventRepository,
            ProofFileStorageService proofFileStorageService
    ) {
        this.chainFileRepository = chainFileRepository;
        this.chainEventRepository = chainEventRepository;
        this.proofFileStorageService = proofFileStorageService;
    }

    @Transactional
    public Map<String, Object> upload(RequestUserContext ctx, MultipartFile file, MaterialChainUploadRequest req) throws IOException {
        if (ctx == null || ctx.getStudentNo() == null) {
            throw new IllegalArgumentException("未识别到上传学生身份");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的证明材料文件");
        }

        String hash = sha256(file.getBytes());

        boolean duplicateByCert = false;
        String duplicateDetail = "";
        if (req != null && req.getCertNo() != null && !req.getCertNo().isBlank()) {
            List<String> conflictingStudents = chainFileRepository.findStudentNosByCertNo(req.getCertNo());
            Set<String> otherStudents = new LinkedHashSet<>();
            for (String s : conflictingStudents) {
                if (!ctx.getStudentNo().equals(s)) {
                    otherStudents.add(s);
                }
            }
            if (!otherStudents.isEmpty()) {
                duplicateByCert = true;
                duplicateDetail = "该证书编号已被其他学号提交: " + String.join(", ", otherStudents);
            }
        }

        String tamperRisk = "LOW";
        String tamperDetail = "";
        if (isImage(file.getContentType(), file.getOriginalFilename())) {
            TamperCheckResult check = detectImageTamper(file.getBytes());
            tamperRisk = check.risk;
            tamperDetail = check.detail;
        }

        String filename = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot) : ".bin";
        String url = proofFileStorageService.upload(file, "material-chain", ext);

        MaterialChainFile chainFile = new MaterialChainFile();
        chainFile.setApplicationId(req != null ? req.getApplicationId() : null);
        chainFile.setChainCode(generateChainCode(hash, ctx.getStudentNo()));
        chainFile.setFileName(filename);
        chainFile.setFileUrl(url);
        chainFile.setFileHash(hash);
        chainFile.setFileSize(file.getSize());
        chainFile.setFileType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        chainFile.setCertNo(req != null ? req.getCertNo() : null);
        chainFile.setCertName(req != null ? req.getCertName() : null);
        chainFile.setUploadStage("SUBMITTED");
        chainFile.setTamperRisk(tamperRisk);
        chainFile.setTamperDetail(tamperDetail);
        chainFile.setCertDuplicateRisk(duplicateByCert ? "DUPLICATE" : "CLEAN");
        chainFile.setCertDuplicateDetail(duplicateDetail);
        chainFile.setRevisionNo(1);
        chainFile.setStudentId(ctx.getUserId());
        chainFile.setStudentNo(ctx.getStudentNo());
        chainFile = chainFileRepository.save(chainFile);

        recordEvent(chainFile, "UPLOAD", null, "SUBMITTED", ctx, "学生初次提交证明材料", hash);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chainCode", chainFile.getChainCode());
        result.put("id", chainFile.getId());
        result.put("fileUrl", chainFile.getFileUrl());
        result.put("certNo", chainFile.getCertNo());
        result.put("tamperRisk", tamperRisk);
        result.put("tamperDetail", tamperDetail);
        result.put("certDuplicateRisk", chainFile.getCertDuplicateRisk());
        result.put("certDuplicateDetail", chainFile.getCertDuplicateDetail());
        result.put("revisionNo", 1);
        result.put("uploadStage", "SUBMITTED");
        return result;
    }

    @Transactional
    public Map<String, Object> modify(Long id, RequestUserContext ctx, MultipartFile file, MaterialChainUploadRequest req) throws IOException {
        MaterialChainFile chainFile = chainFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("材料链记录不存在: " + id));

        if (ctx != null && ctx.getStudentNo() != null && !ctx.getStudentNo().equals(chainFile.getStudentNo()) && !ctx.isAdmin()) {
            throw new IllegalArgumentException("只能修改自己上传的材料");
        }

        String hash = null;
        if (file != null && !file.isEmpty()) {
            hash = sha256(file.getBytes());
            chainFile.setFileHash(hash);
            chainFile.setFileSize(file.getSize());
            chainFile.setFileType(file.getContentType() == null ? chainFile.getFileType() : file.getContentType());
            chainFile.setFileName(file.getOriginalFilename() == null ? chainFile.getFileName() : file.getOriginalFilename());

            String filename = chainFile.getFileName();
            int dot = filename.lastIndexOf('.');
            String ext = dot >= 0 ? filename.substring(dot) : ".bin";
            String url = proofFileStorageService.upload(file, "material-chain", ext);
            chainFile.setFileUrl(url);

            if (isImage(file.getContentType(), file.getOriginalFilename())) {
                TamperCheckResult check = detectImageTamper(file.getBytes());
                chainFile.setTamperRisk(check.risk);
                chainFile.setTamperDetail(check.detail);
            }
        }

        String certNo = req != null ? req.getCertNo() : null;
        String certName = req != null ? req.getCertName() : null;
        if (certNo != null) {
            chainFile.setCertNo(certNo);
            List<String> conflicting = chainFileRepository.findStudentNosByCertNo(certNo);
            Set<String> otherStudents = new LinkedHashSet<>();
            for (String s : conflicting) {
                if (!chainFile.getStudentNo().equals(s)) {
                    otherStudents.add(s);
                }
            }
            if (!otherStudents.isEmpty()) {
                chainFile.setCertDuplicateRisk("DUPLICATE");
                chainFile.setCertDuplicateDetail("该证书编号已被其他学号提交: " + String.join(", ", otherStudents));
            } else {
                chainFile.setCertDuplicateRisk("CLEAN");
                chainFile.setCertDuplicateDetail(null);
            }
        }
        if (certName != null) {
            chainFile.setCertName(certName);
        }

        Integer prevRev = chainFile.getRevisionNo();
        int nextRev = (prevRev == null ? 1 : prevRev) + 1;
        chainFile.setRevisionNo(nextRev);
        String prevStage = chainFile.getUploadStage();
        chainFile.setUploadStage("MODIFIED");
        chainFile = chainFileRepository.save(chainFile);

        recordEvent(chainFile, "MODIFY", prevStage, "MODIFIED", ctx, "学生修改材料（第 " + nextRev + " 版）",
                hash != null ? hash : chainFile.getFileHash());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", chainFile.getId());
        result.put("revisionNo", nextRev);
        result.put("uploadStage", "MODIFIED");
        result.put("fileHash", chainFile.getFileHash());
        result.put("tamperRisk", chainFile.getTamperRisk());
        result.put("tamperDetail", chainFile.getTamperDetail());
        result.put("certDuplicateRisk", chainFile.getCertDuplicateRisk());
        result.put("certDuplicateDetail", chainFile.getCertDuplicateDetail());
        return result;
    }

    @Transactional
    public Map<String, Object> review(Long id, String stage, String pass, String comment, RequestUserContext ctx) {
        MaterialChainFile chainFile = chainFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("材料链记录不存在: " + id));

        String prevStage = chainFile.getUploadStage();
        String toStage;
        String eventType;
        boolean passBool = "true".equalsIgnoreCase(pass) || "PASS".equalsIgnoreCase(pass);

        switch (stage.toUpperCase()) {
            case "INITIAL" -> {
                toStage = passBool ? "INITIAL_REVIEW_PASS" : "INITIAL_REVIEW_REJECT";
                eventType = passBool ? "INITIAL_REVIEW_PASS" : "INITIAL_REVIEW_REJECT";
            }
            case "REEXAMINE" -> {
                toStage = passBool ? "REEXAMINE_PASS" : "REEXAMINE_REJECT";
                eventType = passBool ? "REEXAMINE_PASS" : "REEXAMINE_REJECT";
            }
            case "FINAL" -> {
                toStage = passBool ? "FINAL_REVIEW_PASS" : "FINAL_REVIEW_REJECT";
                eventType = passBool ? "FINAL_REVIEW_PASS" : "FINAL_REVIEW_REJECT";
            }
            default -> throw new IllegalArgumentException("未知审核阶段: " + stage);
        }

        chainFile.setUploadStage(toStage);
        chainFile = chainFileRepository.save(chainFile);

        recordEvent(chainFile, eventType, prevStage, toStage, ctx, comment == null ? "" : comment, chainFile.getFileHash());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", chainFile.getId());
        result.put("uploadStage", toStage);
        result.put("revisionNo", chainFile.getRevisionNo());
        return result;
    }

    public List<Map<String, Object>> traceByChainCode(String chainCode) {
        MaterialChainFile file = chainFileRepository.findByChainCode(chainCode)
                .orElseThrow(() -> new IllegalArgumentException("材料链不存在: " + chainCode));
        return buildTrace(file.getId());
    }

    public List<Map<String, Object>> traceByFileId(Long fileId) {
        return buildTrace(fileId);
    }

    public Map<String, Object> getFileDetail(Long id) {
        MaterialChainFile file = chainFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("材料链记录不存在: " + id));
        return toMap(file);
    }

    public Map<String, Object> getFileDetailByChainCode(String chainCode) {
        MaterialChainFile file = chainFileRepository.findByChainCode(chainCode)
                .orElseThrow(() -> new IllegalArgumentException("材料链不存在: " + chainCode));
        return toMap(file);
    }

    public List<Map<String, Object>> listByStudent(String studentNo) {
        List<MaterialChainFile> files = chainFileRepository.findByStudentNo(studentNo);
        return files.stream().map(this::toMap).toList();
    }

    public List<Map<String, Object>> listByApplication(Long applicationId) {
        List<MaterialChainFile> files = chainFileRepository.findByApplicationId(applicationId);
        return files.stream().map(this::toMap).toList();
    }

    public Map<String, Object> checkCertDuplicate(String certNo, String excludeStudentNo) {
        if (certNo == null || certNo.isBlank()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("risk", "CLEAN");
            r.put("detail", "证书编号为空，无需校验");
            r.put("duplicates", List.of());
            return r;
        }
        List<MaterialChainFile> matches = chainFileRepository.findByCertNo(certNo);
        List<Map<String, Object>> duplicates = new ArrayList<>();
        for (MaterialChainFile m : matches) {
            if (excludeStudentNo == null || !excludeStudentNo.equals(m.getStudentNo())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fileId", m.getId());
                row.put("studentNo", m.getStudentNo());
                row.put("certName", m.getCertName());
                row.put("uploadStage", m.getUploadStage());
                row.put("createdAt", m.getCreatedAt());
                duplicates.add(row);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("risk", duplicates.isEmpty() ? "CLEAN" : "DUPLICATE");
        result.put("detail", duplicates.isEmpty() ? "未发现多人共用该证书编号" : "发现 " + duplicates.size() + " 条其他学生的相同证书编号记录");
        result.put("duplicates", duplicates);
        return result;
    }

    public Map<String, Object> detectImageTamperQuick(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("risk", "LOW");
            r.put("detail", "无文件");
            return r;
        }
        if (!isImage(file.getContentType(), file.getOriginalFilename())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("risk", "LOW");
            r.put("detail", "非图片文件，跳过篡改检测");
            return r;
        }
        TamperCheckResult result = detectImageTamper(file.getBytes());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("risk", result.risk);
        r.put("detail", result.detail);
        return r;
    }

    private List<Map<String, Object>> buildTrace(Long fileId) {
        MaterialChainFile file = chainFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("材料链记录不存在: " + fileId));
        List<MaterialChainEvent> events = chainEventRepository.findByChainFileIdOrderByOccurredAtAsc(fileId);

        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(toMap(file));

        for (MaterialChainEvent ev : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", ev.getId());
            m.put("eventType", ev.getEventType());
            m.put("fromStage", ev.getFromStage());
            m.put("toStage", ev.getToStage());
            m.put("revisionNo", ev.getRevisionNo());
            m.put("operatorId", ev.getOperatorId());
            m.put("operatorName", ev.getOperatorName());
            m.put("operatorRole", ev.getOperatorRole());
            m.put("comment", ev.getComment());
            m.put("fileHashSnapshot", ev.getFileHashSnapshot());
            m.put("occurredAt", ev.getOccurredAt());
            trace.add(m);
        }
        return trace;
    }

    private void recordEvent(MaterialChainFile chainFile, String eventType, String fromStage, String toStage,
                             RequestUserContext ctx, String comment, String fileHashSnapshot) {
        MaterialChainEvent event = new MaterialChainEvent();
        event.setChainFileId(chainFile.getId());
        event.setRevisionNo(chainFile.getRevisionNo());
        event.setEventType(eventType);
        event.setFromStage(fromStage);
        event.setToStage(toStage);
        if (ctx != null) {
            event.setOperatorId(ctx.getUserId());
            event.setOperatorRole(ctx.getRole());
            if (ctx.getStudentNo() != null) {
                event.setOperatorName(ctx.getStudentNo());
            } else if (ctx.isAdmin()) {
                event.setOperatorName("ADMIN");
            } else if (ctx.isTeacher()) {
                event.setOperatorName("TEACHER");
            } else {
                event.setOperatorName(ctx.getRole());
            }
        }
        event.setComment(comment);
        event.setFileHashSnapshot(fileHashSnapshot);
        event.setOccurredAt(LocalDateTime.now());
        chainEventRepository.save(event);
    }

    private Map<String, Object> toMap(MaterialChainFile f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("chainCode", f.getChainCode());
        m.put("applicationId", f.getApplicationId());
        m.put("fileName", f.getFileName());
        m.put("fileUrl", f.getFileUrl());
        m.put("fileHash", f.getFileHash());
        m.put("fileSize", f.getFileSize());
        m.put("fileType", f.getFileType());
        m.put("certNo", f.getCertNo());
        m.put("certName", f.getCertName());
        m.put("uploadStage", f.getUploadStage());
        m.put("tamperRisk", f.getTamperRisk());
        m.put("tamperDetail", f.getTamperDetail());
        m.put("certDuplicateRisk", f.getCertDuplicateRisk());
        m.put("certDuplicateDetail", f.getCertDuplicateDetail());
        m.put("revisionNo", f.getRevisionNo());
        m.put("studentId", f.getStudentId());
        m.put("studentNo", f.getStudentNo());
        m.put("createdAt", f.getCreatedAt());
        m.put("updatedAt", f.getUpdatedAt());
        return m;
    }

    private boolean isImage(String contentType, String filename) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                    || lower.endsWith(".bmp") || lower.endsWith(".webp") || lower.endsWith(".gif");
        }
        return false;
    }

    private TamperCheckResult detectImageTamper(byte[] imageBytes) {
        TamperCheckResult result = new TamperCheckResult("LOW", "图片通过基础完整性检测");
        if (imageBytes == null || imageBytes.length == 0) {
            result.risk = "MEDIUM";
            result.detail = "图片数据为空";
            return result;
        }

        boolean hasIssue = false;
        StringBuilder detail = new StringBuilder();

        if (imageBytes.length > 20 * 1024 * 1024) {
            hasIssue = true;
            detail.append("图片体积异常偏大(>20MB)；");
        }

        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) {
                hasIssue = true;
                detail.append("图片无法被标准解码器识别(可能损坏或被二次封装)；");
            } else {
                int w = img.getWidth();
                int h = img.getHeight();
                if (w < 100 || h < 100) {
                    hasIssue = true;
                    detail.append("图片分辨率过低，疑似截图标注；");
                }
                if (w > 8000 || h > 8000) {
                    detail.append("图片分辨率异常偏大；");
                }

                int widthSegments = 8;
                int heightSegments = 8;
                int segW = Math.max(1, w / widthSegments);
                int segH = Math.max(1, h / heightSegments);
                double edgeConsistency = 0;
                int compared = 0;
                for (int y = 0; y < heightSegments - 1; y++) {
                    for (int x = 0; x < widthSegments - 1; x++) {
                        int x1 = x * segW + segW / 2;
                        int y1 = y * segH + segH / 2;
                        int x2 = x1 + segW;
                        int y2 = y1 + segH;
                        if (x2 < w && y2 < h) {
                            int rgb1 = img.getRGB(x1, y1);
                            int rgb2 = img.getRGB(x2, y2);
                            double d = colorDistance(rgb1, rgb2);
                            edgeConsistency += d;
                            compared++;
                        }
                    }
                }
                if (compared > 0) {
                    double avgDist = edgeConsistency / compared;
                    if (avgDist < 2.0) {
                        detail.append("图片过于平滑，可能存在大面积涂抹/遮罩；");
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("图片解码异常: {}", ex.getMessage());
            hasIssue = true;
            detail.append("图片解码抛出异常，可能格式被篡改；");
        }

        if (hasIssue) {
            result.risk = "MEDIUM";
            result.detail = detail.toString();
        } else {
            result.detail = detail.length() > 0 ? detail.toString() : result.detail;
        }

        String hexPrefix = bytesToHexPrefix(imageBytes, 8);
        if ("89504e47".equals(hexPrefix)) {
            result.detail = "[PNG] " + result.detail;
        } else if ("ffd8ffe0".equals(hexPrefix) || "ffd8ffe1".equals(hexPrefix)) {
            result.detail = "[JPEG] " + result.detail;
        } else if ("47494638".equals(hexPrefix)) {
            result.detail = "[GIF] " + result.detail;
        }

        return result;
    }

    private double colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xff;
        int g1 = (rgb1 >> 8) & 0xff;
        int b1 = rgb1 & 0xff;
        int r2 = (rgb2 >> 16) & 0xff;
        int g2 = (rgb2 >> 8) & 0xff;
        int b2 = rgb2 & 0xff;
        return Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));
    }

    private String bytesToHexPrefix(byte[] bytes, int len) {
        if (bytes == null || bytes.length < len) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 计算失败", ex);
        }
    }

    private String generateChainCode(String hash, String studentNo) {
        long ts = System.currentTimeMillis();
        String raw = (studentNo == null ? "?" : studentNo) + "|" + ts + "|" + (hash == null ? "" : hash.substring(0, Math.min(16, hash.length())));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes());
            StringBuilder sb = new StringBuilder("MC-");
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "MC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        }
    }

    private static class TamperCheckResult {
        String risk;
        String detail;
        TamperCheckResult(String risk, String detail) {
            this.risk = risk;
            this.detail = detail;
        }
    }
}
