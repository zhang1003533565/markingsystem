package com.markingsystem.service;

import com.markingsystem.dto.SecondClassScoreImportResponseDTO;
import com.markingsystem.dto.SecondClassScorePreviewResponseDTO;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.SecondClassScoreRule;
import com.markingsystem.entity.Student;
import com.markingsystem.exception.SubjectRAINException;
import com.markingsystem.util.FileParseHelper;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SecondClassScoreImportService {

    @Autowired
    private FileParseHelper fileParseHelper;

    @Autowired
    private SecondClassScoreRuleService secondClassScoreRuleService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private MoralEducationService moralEducationService;

    public SecondClassScorePreviewResponseDTO preview(MultipartFile file) throws IOException {
        FileParseHelper.ParsedFileData fileData = fileParseHelper.parse(file);

        List<Map<String, Object>> previewRows = new ArrayList<>();
        for (List<Object> row : fileData.getRows()) {
            if (previewRows.size() >= 8) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            for (int index = 0; index < fileData.getHeaders().size(); index++) {
                String header = fileData.getHeaders().get(index);
                Object rawValue = row.size() > index ? row.get(index) : null;
                item.put(header, rawValue == null ? "" : FileParseHelper.getStringValue(rawValue));
            }
            previewRows.add(item);
        }

        SecondClassScorePreviewResponseDTO response = new SecondClassScorePreviewResponseDTO();
        response.setHeaders(fileData.getHeaders());
        response.setRowCount(fileData.getRows().size());
        response.setPreviewRows(previewRows);
        response.setDefaultColumns(Map.of(
                "studentNoColumn", resolveDefaultHeader(fileData, List.of("学号", "账号", "学生账号")),
                "nameColumn", resolveDefaultHeader(fileData, List.of("姓名", "学生姓名")),
                "classNameColumn", resolveDefaultHeader(fileData, List.of("班级", "行政班")),
                "scoreColumn", resolveDefaultHeader(fileData, List.of("积分总和", "第二课堂学分-积分", "二课分"))
        ));
        return response;
    }

    public Map<String, Object> listImportedScores(String academicYear, String keyword, RequestUserContext context, int page, int size) {
        if (academicYear == null || academicYear.isBlank()) {
            return buildPage(List.of(), page, size);
        }

        List<MoralEducation> records = context.isTeacher()
                ? moralEducationService.findSecondClassRecordsByAcademicYearAndTeacherId(academicYear.trim(), context.getTeacherId())
                : moralEducationService.findSecondClassRecordsByAcademicYear(academicYear.trim());
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> result = new ArrayList<>();
        for (MoralEducation record : records) {
            Student student = record.getStudent();
            if (normalizedKeyword.isEmpty() || matchesKeyword(student, normalizedKeyword)) {
                result.add(buildImportedScoreItem(record));
            }
        }
        return buildPage(result, page, size);
    }

    @Transactional
    public Map<String, Object> updateImportedScore(Long recordId, Double secondClassScore, RequestUserContext context) {
        if (recordId == null) {
            throw new IllegalArgumentException("成绩记录不能为空");
        }
        if (secondClassScore == null || !Double.isFinite(secondClassScore) || secondClassScore < 0) {
            throw new IllegalArgumentException("二课分必须是大于等于 0 的有效数字");
        }

        MoralEducation record = moralEducationService.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("二课成绩记录不存在"));
        Student student = record.getStudent();
        if (student == null || record.getAcademicYear() == null || record.getAcademicYear().isBlank()) {
            throw new IllegalArgumentException("二课成绩记录数据不完整");
        }
        if (context.isTeacher()
                && (context.getTeacherId() == null || !Objects.equals(student.getTeacherId(), context.getTeacherId()))) {
            throw new IllegalArgumentException("无权修改其他教师管理的学生成绩");
        }

        Double moralScore = resolveMoralScore(secondClassScore, secondClassScoreRuleService.findAll());
        if (moralScore == null) {
            throw new IllegalArgumentException("当前二课分未匹配到换算规则");
        }

        record.setSecondClassScore(secondClassScore);
        record.setActivityScore(moralScore);
        record.setUpdatedAt(LocalDateTime.now());
        MoralEducation saved = moralEducationService.save(record);
        return buildImportedScoreItem(saved);
    }

    private boolean matchesKeyword(Student student, String normalizedKeyword) {
        if (student == null) {
            return false;
        }
        return containsIgnoreCase(student.getStudentNo(), normalizedKeyword)
                || containsIgnoreCase(student.getName(), normalizedKeyword)
                || containsIgnoreCase(student.getClassName(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Map<String, Object> buildImportedScoreItem(MoralEducation record) {
        Student student = record.getStudent();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.getId());
        item.put("studentNo", student == null ? "" : student.getStudentNo());
        item.put("studentName", student == null ? "" : student.getName());
        item.put("className", student == null ? "" : student.getClassName());
        item.put("academicYear", record.getAcademicYear());
        item.put("secondClassScore", record.getSecondClassScore());
        item.put("moralScore", record.getActivityScore());
        item.put("bonusScore", record.getBonusScore());
        item.put("totalScore", record.getTotalScore());
        item.put("updatedAt", record.getUpdatedAt());
        return item;
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows.subList(fromIndex, toIndex));
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Transactional
    public SecondClassScoreImportResponseDTO importScores(
            MultipartFile file,
            String academicYear,
            String studentNoColumn,
            String nameColumn,
            String classNameColumn,
            String scoreColumn,
            RequestUserContext context
    ) throws IOException {
        if (academicYear == null || academicYear.isBlank()) {
            throw new SubjectRAINException("学年不能为空");
        }
        FileParseHelper.ParsedFileData fileData = fileParseHelper.parse(file);
        validateRequiredHeader(fileData, studentNoColumn, "学号字段");
        validateRequiredHeader(fileData, scoreColumn, "二课分数字段");

        int studentNoIndex = fileData.getHeaderIndex(studentNoColumn);
        int scoreIndex = fileData.getHeaderIndex(scoreColumn);

        List<SecondClassScoreRule> rules = secondClassScoreRuleService.findAll();
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> importDetails = new ArrayList<>();
        int successRowCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (List<Object> row : fileData.getRows()) {
            String studentNo = FileParseHelper.getStringValue(row.get(studentNoIndex));
            String studentName = "";
            if (nameColumn != null && !nameColumn.isBlank() && fileData.hasHeader(nameColumn)) {
                int nameIndex = fileData.getHeaderIndex(nameColumn);
                studentName = FileParseHelper.getStringValue(row.get(nameIndex));
            }
            Double secondClassScore = FileParseHelper.parseScore(row.get(scoreIndex));

            if (studentNo.isBlank()) {
                skippedCount++;
                importDetails.add(buildImportDetail(studentNo, studentName, secondClassScore, null, "skipped", "学号为空，已跳过"));
                continue;
            }
            Student student = studentService.findByStudentNo(studentNo).orElse(null);
            if (student == null) {
                errors.add(studentNo + " 账号不存在");
                importDetails.add(buildImportDetail(studentNo, studentName, secondClassScore, null, "failed", "账号不存在"));
                continue;
            }
            if (secondClassScore == null) {
                errors.add(studentNo + " 二课分为空或格式错误");
                importDetails.add(buildImportDetail(studentNo, defaultName(studentName, student.getName()), null, null, "failed", "二课分为空或格式错误"));
                continue;
            }
            if (context.isTeacher()) {
                boolean sameTeacher = Objects.equals(student.getTeacherId(), context.getTeacherId());
                if (!sameTeacher) {
                    errors.add(studentNo + " 不属于当前老师管理学生，已跳过");
                    importDetails.add(buildImportDetail(studentNo, defaultName(studentName, student.getName()), secondClassScore, null, "failed", "不属于当前老师管理学生"));
                    continue;
                }
            }

            Double moralScore = resolveMoralScore(secondClassScore, rules);
            if (moralScore == null) {
                errors.add(studentNo + " 未匹配到换算规则: " + secondClassScore);
                importDetails.add(buildImportDetail(studentNo, defaultName(studentName, student.getName()), secondClassScore, null, "failed", "未匹配到换算规则"));
                continue;
            }

            boolean exists = moralEducationService.existsByStudentIdAndAcademicYear(student.getId(), academicYear.trim());
            moralEducationService.createOrUpdateScoreByAcademicYear(
                    student.getId(),
                    academicYear.trim(),
                    moralScore,
                    0D,
                    secondClassScore
            );
            if (exists) {
                updatedCount++;
                importDetails.add(buildImportDetail(studentNo, defaultName(studentName, student.getName()), secondClassScore, moralScore, "updated", "已更新德育基础分"));
            } else {
                createdCount++;
                importDetails.add(buildImportDetail(studentNo, defaultName(studentName, student.getName()), secondClassScore, moralScore, "created", "已新增德育基础分"));
            }
            successRowCount++;
        }

        SecondClassScoreImportResponseDTO response = new SecondClassScoreImportResponseDTO();
        response.setTotalRowCount(fileData.getRows().size());
        response.setSuccessRowCount(successRowCount);
        response.setCreatedCount(createdCount);
        response.setUpdatedCount(updatedCount);
        response.setSkippedCount(skippedCount);
        response.setErrors(errors);
        response.setErrorSummaries(buildErrorSummaries(errors));
        response.setImportDetails(importDetails);
        return response;
    }

    private String resolveDefaultHeader(FileParseHelper.ParsedFileData fileData, List<String> candidates) {
        for (String candidate : candidates) {
            if (fileData.hasHeader(candidate)) {
                return candidate;
            }
        }
        return fileData.getHeaders().isEmpty() ? "" : fileData.getHeaders().get(0);
    }

    private List<Map<String, Object>> buildErrorSummaries(List<String> errors) {
        Map<String, Integer> reasonCounter = new LinkedHashMap<>();
        for (String error : errors) {
            String reason = normalizeErrorReason(error);
            reasonCounter.merge(reason, 1, Integer::sum);
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        reasonCounter.forEach((reason, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reason", reason);
            item.put("count", count);
            summaries.add(item);
        });
        return summaries;
    }

    private String normalizeErrorReason(String error) {
        if (error == null || error.isBlank()) {
            return "未知原因";
        }
        if (error.contains("账号不存在")) {
            return "账号不存在";
        }
        if (error.contains("不属于当前老师管理学生")) {
            return "非当前老师学生";
        }
        if (error.contains("二课分为空或格式错误")) {
            return "分数为空或格式错误";
        }
        if (error.contains("未匹配到换算规则")) {
            return "未匹配换算规则";
        }
        int spaceIndex = error.indexOf(' ');
        return spaceIndex > 0 && spaceIndex + 1 < error.length()
                ? error.substring(spaceIndex + 1).trim()
                : error.trim();
    }

    private Double resolveMoralScore(Double secondClassScore, List<SecondClassScoreRule> rules) {
        if (secondClassScore == null) {
            return null;
        }
        for (SecondClassScoreRule rule : rules) {
            if (rule.getMinScore() == null || rule.getMoralScore() == null) {
                continue;
            }
            double min = rule.getMinScore();
            Double max = rule.getMaxScore();
            if (max == null) {
                if (secondClassScore >= min) {
                    return rule.getMoralScore();
                }
            } else if (secondClassScore >= min && secondClassScore <= max) {
                return rule.getMoralScore();
            }
        }
        return null;
    }

    private Map<String, Object> buildImportDetail(String studentNo, String studentName, Double secondClassScore,
                                                  Double moralScore, String status, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("studentNo", studentNo == null ? "" : studentNo);
        item.put("studentName", studentName == null ? "" : studentName);
        item.put("secondClassScore", secondClassScore);
        item.put("moralScore", moralScore);
        item.put("status", status);
        item.put("message", message);
        return item;
    }

    private String defaultName(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private void validateRequiredHeader(FileParseHelper.ParsedFileData fileData, String headerName, String label) {
        if (headerName == null || headerName.isBlank() || !fileData.hasHeader(headerName)) {
            throw new SubjectRAINException(label + "不存在: " + headerName);
        }
    }
}
