package com.markingsystem.controller;

import com.markingsystem.dto.SubjectRAINImportRequestDTO;
import com.markingsystem.dto.SubjectRAINImportResponseDTO;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.service.SubjectRAINService;
import com.markingsystem.service.SubjectScoreImportService;
import com.markingsystem.util.FileParseHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 科目管理控制器
 * 职责：处理科目相关的HTTP请求
 */
@RestController
@RequestMapping("/api/subjects/rain")
public class SubjectRAINController {

    @Autowired
    private SubjectRAINService subjectRAINService;

    @Autowired
    private SubjectScoreImportService subjectScoreImportService;

    @Autowired
    private FileParseHelper fileParseHelper;

    /**
     * 上传文件并预览科目
     * 除德智体之外的分数导入接口
     */
    @PostMapping("/preview")
    public ResponseEntity<SubjectRAINImportResponseDTO> previewSubjects(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType) {

        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("上传的文件为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 预览科目
            List<String> subjects = subjectRAINService.previewSubjects(file, fileType);

            // 检查是否没有识别到任何科目
            if (subjects.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("表格中没有识别到任何科目");
                return ResponseEntity.badRequest().body(response);
            }

            // 返回预览结果
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setPreviewSubjects(subjects);
            FileParseHelper.ParsedFileData parsedFileData = fileParseHelper.parse(file);
            response.setHeaders(parsedFileData.getHeaders());
            response.setClassValues(fileParseHelper.collectDistinctColumnValues(parsedFileData, parsedFileData.getHeaders()));
            response.setClassValuesByHeader(fileParseHelper.collectDistinctColumnValuesByHeaders(parsedFileData));
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setErrorMessage("文件读取失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setErrorMessage("处理失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 导入科目并自动导入成绩
     */
    @PostMapping("/import")
    public ResponseEntity<SubjectRAINImportResponseDTO> importSubjects(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType,
            @RequestParam("semester") String semester,
            @RequestParam("confirm") String confirm,
            @RequestParam("subjects") List<String> subjects,
            @RequestParam("accountColumn") String accountColumn,
            @RequestParam("passwordColumn") String passwordColumn,
            @RequestParam("nameColumn") String nameColumn,
            @RequestParam("classColumn") String classColumn,
            @RequestParam("classValues") List<String> classValues,
            @RequestParam("classNames") List<String> classNames,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("上传的文件为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查科目列表是否为空
            if (subjects == null || subjects.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("科目列表为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否确认导入
            boolean confirmBool = Boolean.parseBoolean(confirm);
            if (!confirmBool) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("请确认导入");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查学期是否为空
            if (semester == null || semester.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("学期不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            if (accountColumn == null || accountColumn.isBlank()
                    || passwordColumn == null || passwordColumn.isBlank()
                    || nameColumn == null || nameColumn.isBlank()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("账号字段、密码字段、姓名字段必须由用户明确选择");
                return ResponseEntity.badRequest().body(response);
            }
            if (classColumn == null || classColumn.isBlank()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("必须指定班级字段");
                return ResponseEntity.badRequest().body(response);
            }
            if (classValues == null || classValues.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("必须至少选择一个分类值");
                return ResponseEntity.badRequest().body(response);
            }
            if (classNames == null || classNames.isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("必须至少选择一个系统班级");
                return ResponseEntity.badRequest().body(response);
            }
            if (classValues.size() != classNames.size()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("分类值与系统班级数量不一致");
                return ResponseEntity.badRequest().body(response);
            }

            // 导入科目并自动导入成绩（转发给 SubjectScoreImportService）
            SubjectRAINImportResponseDTO response = subjectScoreImportService.importSubjectsAndScores(
                    file,
                    fileType,
                    semester,
                    subjects,
                    accountColumn,
                    passwordColumn,
                    nameColumn,
                    classColumn,
                    classValues,
                    classNames,
                    parseLong(userId),
                    userRole
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setErrorMessage("导入失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/import/batches")
    public ResponseEntity<Map<String, Object>> listImportBatches(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", subjectScoreImportService.listImportBatches(userRole, parseLong(userId))
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", 500,
                    "message", "获取导入历史失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/import/student-records")
    public ResponseEntity<Map<String, Object>> listImportStudentRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", subjectScoreImportService.listImportStudentRecordsPage(null, keyword, page, size, userRole, parseLong(userId))
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", 500,
                    "message", "获取学生导入历史失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/import/student-records/{batchId}")
    public ResponseEntity<Map<String, Object>> listImportStudentRecordsByBatch(
            @PathVariable String batchId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", subjectScoreImportService.listImportStudentRecordsPage(batchId, keyword, page, size, userRole, parseLong(userId))
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", 500,
                    "message", "获取学生导入明细失败: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/import/student-records/{batchId}/{studentId}")
    public ResponseEntity<SubjectRAINImportResponseDTO> deleteImportStudentRecord(
            @PathVariable String batchId,
            @PathVariable Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            return ResponseEntity.ok(subjectScoreImportService.deleteImportStudentRecord(batchId, studentId, userRole, parseLong(userId)));
        } catch (Exception e) {
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setErrorMessage("删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/import/undo/{batchId}")
    public ResponseEntity<SubjectRAINImportResponseDTO> undoImport(
            @PathVariable String batchId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            return ResponseEntity.ok(subjectScoreImportService.undoImportBatch(batchId, userRole, parseLong(userId)));
        } catch (Exception e) {
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setErrorMessage("撤销失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 导入科目（兼容旧接口）
     */
    @PostMapping("/import/legacy")
    public ResponseEntity<SubjectRAINImportResponseDTO> importSubjectsLegacy(
            @RequestBody SubjectRAINImportRequestDTO request) {

        try {
            // 检查科目列表是否为空
            if (request.getSubjects() == null || request.getSubjects().isEmpty()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("科目列表为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否确认导入
            if (!request.isConfirm()) {
                SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
                response.setErrorMessage("请确认导入");
                return ResponseEntity.badRequest().body(response);
            }

            // 导入科目
            SubjectRAINImportResponseDTO response = subjectRAINService.importSubjects(request.getSubjects());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
            response.setErrorMessage("导入失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 获取所有科目（只查询未删除的科目）
     */
    @GetMapping
    public ResponseEntity<List<SubjectRAIN>> getAllSubjects() {
        try {
            List<SubjectRAIN> subjects = subjectRAINService.getAllSubjects();
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取所有科目（包括已删除的科目）
     */
    @GetMapping("/all")
    public ResponseEntity<List<SubjectRAIN>> getAllSubjectsIncludingDeleted() {
        try {
            List<SubjectRAIN> subjects = subjectRAINService.getAllSubjectsIncludingDeleted();
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取所有已删除的科目（回收站）
     */
    @GetMapping("/deleted")
    public ResponseEntity<List<SubjectRAIN>> getAllDeletedSubjects() {
        try {
            List<SubjectRAIN> subjects = subjectRAINService.getAllDeletedSubjects();
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据科目名称查询科目（模糊查询）
     */
    @GetMapping("/search")
    public ResponseEntity<List<SubjectRAIN>> searchSubjects(
            @RequestParam(value = "name", required = false) String subjectName) {
        try {
            List<SubjectRAIN> subjects;
            if (subjectName != null && !subjectName.isEmpty()) {
                subjects = subjectRAINService.searchSubjectsByName(subjectName);
            } else {
                subjects = subjectRAINService.getAllSubjects();
            }
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 软删除科目（根据ID）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteSubject(@PathVariable Long id) {
        try {
            subjectRAINService.softDeleteSubject(id);
            return ResponseEntity.ok("科目已删除");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量软删除科目
     */
    @DeleteMapping("/batch")
    public ResponseEntity<String> batchDeleteSubjects(@RequestBody List<Long> ids) {
        try {
            subjectRAINService.batchSoftDeleteSubjects(ids);
            return ResponseEntity.ok("科目已批量删除");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 恢复已删除的科目
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<String> restoreSubject(@PathVariable Long id) {
        try {
            subjectRAINService.restoreSubject(id);
            return ResponseEntity.ok("科目已恢复");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("恢复失败: " + e.getMessage());
        }
    }

    /**
     * 批量恢复已删除的科目
     */
    @PutMapping("/batch/restore")
    public ResponseEntity<String> batchRestoreSubjects(@RequestBody List<Long> ids) {
        try {
            subjectRAINService.batchRestoreSubjects(ids);
            return ResponseEntity.ok("科目已批量恢复");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("批量恢复失败: " + e.getMessage());
        }
    }

    /**
     * 永久删除科目（物理删除，仅当没有关联成绩时允许）
     */
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<String> permanentDeleteSubject(@PathVariable Long id) {
        try {
            subjectRAINService.permanentDeleteSubject(id);
            return ResponseEntity.ok("科目已永久删除");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("永久删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量永久删除科目（物理删除，仅当没有关联成绩时允许）
     */
    @DeleteMapping("/batch/permanent")
    public ResponseEntity<String> batchPermanentDeleteSubjects(@RequestBody List<Long> ids) {
        try {
            subjectRAINService.batchPermanentDeleteSubjects(ids);
            return ResponseEntity.ok("科目已批量永久删除");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("批量永久删除失败: " + e.getMessage());
        }
    }

    /**
     * 按分类获取科目
     */
    @GetMapping("/by-category")
    public ResponseEntity<List<SubjectRAIN>> getSubjectsByCategory(
            @RequestParam(value = "category", required = false) String category) {
        try {
            List<SubjectRAIN> subjects;
            if (category != null && !category.isEmpty()) {
                try {
                    SubjectRAIN.Category categoryEnum = SubjectRAIN.Category.valueOf(category.toUpperCase());
                    subjects = subjectRAINService.getSubjectsByCategory(categoryEnum);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
            } else {
                subjects = subjectRAINService.getAllSubjects();
            }
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量更新科目分类
     */
    @PutMapping("/batch/category")
    public ResponseEntity<Void> batchUpdateCategory(
            @RequestParam("category") String category,
            @RequestBody List<Long> ids) {
        try {
            SubjectRAIN.Category categoryEnum = SubjectRAIN.Category.valueOf(category.toUpperCase());
            subjectRAINService.batchUpdateCategory(ids, categoryEnum);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
