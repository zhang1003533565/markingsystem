package com.markingsystem.service;

import com.markingsystem.dto.SubjectRAINImportResponseDTO;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.exception.SubjectRAINException;
import com.markingsystem.repository.SubjectRAINRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 科目管理服务类
 * 职责：科目CRUD、软删除、分类管理
 * 注意：成绩导入逻辑已迁移到 SubjectScoreImportService
 */
@Service
public class SubjectRAINService {

    @Autowired
    private SubjectRAINRepository subjectRAINRepository;

    @Autowired
    private SubjectScoreRAINServices subjectScoreRAINServices;

    // 固定列名称
    private static final List<String> FIXED_COLUMNS = Arrays.asList("学号", "姓名", "班级", "性别");

    /**
     * 从文件中读取科目名称
     * @param file 上传的文件
     * @param fileType 文件类型
     * @return 科目名称列表
     * @throws IOException 文件读取异常
     */
    public List<String> extractSubjectsFromFile(MultipartFile file, String fileType) throws IOException {
        Set<String> subjects = new HashSet<>();

        if ("excel".equalsIgnoreCase(fileType) || file.getOriginalFilename().endsWith(".xlsx")) {
            // 处理Excel文件
            try (InputStream inputStream = file.getInputStream();
                 Workbook workbook = new XSSFWorkbook(inputStream)) {
                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);

                if (headerRow == null) {
                    throw new SubjectRAINException("Excel文件中没有表头行");
                }

                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String cellValue = getCellValue(cell).trim();
                        if (!cellValue.isEmpty()) {
                            subjects.add(cellValue);
                        }
                    }
                }
            }
        } else if ("csv".equalsIgnoreCase(fileType) || file.getOriginalFilename().endsWith(".csv")) {
            // 处理CSV文件
            try (InputStream inputStream = file.getInputStream()) {
                java.util.Scanner scanner = new java.util.Scanner(inputStream);
                if (scanner.hasNextLine()) {
                    String headerLine = scanner.nextLine();
                    String[] headers = headerLine.split(",");
                    for (String header : headers) {
                        String headerValue = header.trim();
                        if (!headerValue.isEmpty()) {
                            subjects.add(headerValue);
                        }
                    }
                } else {
                    throw new SubjectRAINException("CSV文件中没有表头行");
                }
                scanner.close();
            }
        } else {
            throw new SubjectRAINException("不支持的文件类型，只支持Excel和CSV文件");
        }

        if (subjects.isEmpty()) {
            throw new SubjectRAINException("表格中没有识别到任何科目");
        }

        return new ArrayList<>(subjects);
    }

    /**
     * 获取单元格的值
     * @param cell 单元格
     * @return 单元格的值
     */
    private String getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    /**
     * 导入科目
     * @param subjects 科目名称列表
     * @return 导入结果
     */
    public SubjectRAINImportResponseDTO importSubjects(List<String> subjects) {
        SubjectRAINImportResponseDTO response = new SubjectRAINImportResponseDTO();
        List<String> newSubjects = new ArrayList<>();
        List<String> failedSubjects = new ArrayList<>();

        for (String subjectName : subjects) {
            try {
                // 检查科目是否已存在
                if (!subjectRAINRepository.existsBySubjectName(subjectName)) {
                    // 新增科目，默认设置为SUBJECT分类
                    SubjectRAIN subject = new SubjectRAIN();
                    subject.setSubjectName(subjectName);
                    subject.setCategory(SubjectRAIN.Category.SUBJECT);
                    subjectRAINRepository.save(subject);
                    newSubjects.add(subjectName);
                }
            } catch (Exception e) {
                failedSubjects.add(subjectName);
            }
        }

        response.setNewSubjects(newSubjects);
        response.setFailedSubjects(failedSubjects);

        // 检查是否没有识别到任何科目
        if (subjects.isEmpty()) {
            response.setErrorMessage("表格中没有识别到任何科目");
        }

        return response;
    }

    /**
     * 批量更新科目分类
     * @param ids 科目ID列表
     * @param category 分类
     */
    public void batchUpdateCategory(List<Long> ids, SubjectRAIN.Category category) {
        List<SubjectRAIN> subjects = subjectRAINRepository.findAllById(ids);
        for (SubjectRAIN subject : subjects) {
            subject.setCategory(category);
        }
        subjectRAINRepository.saveAll(subjects);
    }

    /**
     * 按分类获取科目（只查询未删除的科目）
     * @param category 分类
     * @return 科目列表
     */
    public List<SubjectRAIN> getSubjectsByCategory(SubjectRAIN.Category category) {
        return subjectRAINRepository.findByCategoryAndDeletedFalse(category);
    }

    /**
     * 获取所有科目（只查询未删除的科目）
     * @return 科目列表
     */
    public List<SubjectRAIN> getAllSubjects() {
        return subjectRAINRepository.findAllActive();
    }

    /**
     * 获取所有科目（包括已删除的科目）
     * @return 科目列表
     */
    public List<SubjectRAIN> getAllSubjectsIncludingDeleted() {
        return subjectRAINRepository.findAllIncludingDeleted();
    }

    /**
     * 获取所有已删除的科目
     * @return 已删除的科目列表
     */
    public List<SubjectRAIN> getAllDeletedSubjects() {
        return subjectRAINRepository.findAllDeleted();
    }

    /**
     * 根据科目名称查询科目（模糊查询，只查询未删除的科目）
     * @param subjectName 科目名称
     * @return 科目列表
     */
    public List<SubjectRAIN> searchSubjectsByName(String subjectName) {
        List<SubjectRAIN> allSubjects = subjectRAINRepository.findAllActive();
        return allSubjects.stream()
                .filter(subject -> subject.getSubjectName().contains(subjectName))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 软删除科目（根据ID）
     * @param id 科目ID
     * @throws SubjectRAINException 科目不存在时抛出异常
     */
    @Transactional
    public void softDeleteSubject(Long id) {
        SubjectRAIN subject = subjectRAINRepository.findById(id)
                .orElseThrow(() -> new SubjectRAINException("科目不存在，ID: " + id));
        subject.setDeleted(true);
        subject.setDeletedAt(LocalDateTime.now());
        subjectRAINRepository.save(subject);
    }

    /**
     * 批量软删除科目
     * @param ids 科目ID列表
     */
    @Transactional
    public void batchSoftDeleteSubjects(List<Long> ids) {
        List<SubjectRAIN> subjects = subjectRAINRepository.findAllById(ids);
        LocalDateTime now = LocalDateTime.now();
        subjects.forEach(s -> {
            s.setDeleted(true);
            s.setDeletedAt(now);
        });
        subjectRAINRepository.saveAll(subjects);
    }

    /**
     * 恢复已删除的科目
     * @param id 科目ID
     * @throws SubjectRAINException 科目不存在时抛出异常
     */
    @Transactional
    public void restoreSubject(Long id) {
        SubjectRAIN subject = subjectRAINRepository.findById(id)
                .orElseThrow(() -> new SubjectRAINException("科目不存在，ID: " + id));
        subject.setDeleted(false);
        subject.setDeletedAt(null);
        subjectRAINRepository.save(subject);
    }

    /**
     * 批量恢复已删除的科目
     * @param ids 科目ID列表
     */
    @Transactional
    public void batchRestoreSubjects(List<Long> ids) {
        List<SubjectRAIN> subjects = subjectRAINRepository.findAllById(ids);
        subjects.forEach(s -> {
            s.setDeleted(false);
            s.setDeletedAt(null);
        });
        subjectRAINRepository.saveAll(subjects);
    }

    /**
     * 永久删除科目（物理删除，仅当没有关联成绩时允许）
     * @param id 科目ID
     * @throws SubjectRAINException 科目不存在或存在关联成绩时抛出异常
     */
    @Transactional
    public void permanentDeleteSubject(Long id) {
        SubjectRAIN subject = subjectRAINRepository.findById(id)
                .orElseThrow(() -> new SubjectRAINException("科目不存在，ID: " + id));
        
        // 检查是否有关联的成绩记录
        List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresBySubjectId(id);
        if (!scores.isEmpty()) {
            throw new SubjectRAINException("该科目存在 " + scores.size() + " 条历史成绩记录，无法永久删除。建议使用软删除。");
        }
        
        subjectRAINRepository.delete(subject);
    }

    /**
     * 批量永久删除科目（物理删除，仅当没有关联成绩时允许）
     * @param ids 科目ID列表
     * @throws SubjectRAINException 存在关联成绩时抛出异常
     */
    @Transactional
    public void batchPermanentDeleteSubjects(List<Long> ids) {
        List<SubjectRAIN> subjects = subjectRAINRepository.findAllById(ids);
        List<String> errors = new ArrayList<>();
        
        for (SubjectRAIN subject : subjects) {
            List<SubjectScoreRAIN> scores = subjectScoreRAINServices.getScoresBySubjectId(subject.getId());
            if (!scores.isEmpty()) {
                errors.add(subject.getSubjectName() + " (存在 " + scores.size() + " 条成绩记录)");
            }
        }
        
        if (!errors.isEmpty()) {
            throw new SubjectRAINException("以下科目存在历史成绩记录，无法永久删除：" + String.join(", ", errors));
        }
        
        subjectRAINRepository.deleteAll(subjects);
    }

    /**
     * 预览文件中的科目
     * @param file 上传的文件
     * @param fileType 文件类型
     * @return 科目名称列表
     * @throws IOException 文件读取异常
     */
    public List<String> previewSubjects(MultipartFile file, String fileType) throws IOException {
        return extractSubjectsFromFile(file, fileType);
    }

}
