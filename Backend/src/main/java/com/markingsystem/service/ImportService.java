package com.markingsystem.service;

import com.markingsystem.dto.ImportResultDTO;
import com.markingsystem.dto.ScoreImportDTO;
import com.markingsystem.entity.Student;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ImportService {
    
    @Autowired
    private StudentService studentService;
    
    @Autowired
    private MoralEducationService moralEducationService;
    
    @Autowired
    private IntellectualEducationService intellectualEducationService;
    
    @Autowired
    private PhysicalEducationService physicalEducationService;
    
    @Transactional
    public ImportResultDTO importFromExcel(MultipartFile file) throws IOException {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // 跳过表头
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    ScoreImportDTO dto = parseExcelRow(row);
                    importScoreData(dto);
                    successCount++;
                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行导入失败: " + e.getMessage());
                }
            }
        }
        
        int totalCount = successCount + errors.size();
        return new ImportResultDTO(totalCount, successCount, errors);
    }
    
    @Transactional
    public ImportResultDTO importFromCsv(MultipartFile file) throws IOException {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int rowNum = 1;
        
        try (CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8")))) {
            // 跳过表头
            csvReader.readNext();
            
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                rowNum++;
                try {
                    ScoreImportDTO dto = parseCsvRow(row);
                    importScoreData(dto);
                    successCount++;
                } catch (Exception e) {
                    errors.add("第" + rowNum + "行导入失败: " + e.getMessage());
                }
            }
        } catch (CsvValidationException e) {
            errors.add("CSV文件格式错误: " + e.getMessage());
        }
        
        int totalCount = successCount + errors.size();
        return new ImportResultDTO(totalCount, successCount, errors);
    }
    
    private ScoreImportDTO parseExcelRow(Row row) {
        ScoreImportDTO dto = new ScoreImportDTO();
        
        dto.setStudentNo(getCellValue(row.getCell(0)));
        dto.setName(getCellValue(row.getCell(1)));
        dto.setClassName(getCellValue(row.getCell(2)));
        dto.setActivityScore(parseDouble(getCellValue(row.getCell(3))));
        dto.setCourseScore(parseDouble(getCellValue(row.getCell(4))));
        dto.setPhysicalRawScore(parseDouble(getCellValue(row.getCell(5))));
        dto.setPhysicalPercentage(parseDouble(getCellValue(row.getCell(6))));
        dto.setMoralBonusScore(parseDouble(getCellValue(row.getCell(7))));
        dto.setIntellectualBonusScore(parseDouble(getCellValue(row.getCell(8))));
        dto.setPhysicalBonusScore(parseDouble(getCellValue(row.getCell(9))));
        dto.setSemester(getCellValue(row.getCell(10)));
        
        return dto;
    }
    
    private ScoreImportDTO parseCsvRow(String[] row) {
        ScoreImportDTO dto = new ScoreImportDTO();
        
        dto.setStudentNo(row[0].trim());
        dto.setName(row[1].trim());
        dto.setClassName(row[2].trim());
        dto.setActivityScore(parseDouble(row[3].trim()));
        dto.setCourseScore(parseDouble(row[4].trim()));
        dto.setPhysicalRawScore(parseDouble(row[5].trim()));
        dto.setPhysicalPercentage(parseDouble(row[6].trim()));
        dto.setMoralBonusScore(parseDouble(row[7].trim()));
        dto.setIntellectualBonusScore(parseDouble(row[8].trim()));
        dto.setPhysicalBonusScore(parseDouble(row[9].trim()));
        dto.setSemester(row[10].trim());
        
        return dto;
    }
    
    private void importScoreData(ScoreImportDTO dto) {
        // 验证必填字段
        if (dto.getStudentNo() == null || dto.getStudentNo().isEmpty()) {
            throw new IllegalArgumentException("学号不能为空");
        }
        if (dto.getSemester() == null || dto.getSemester().isEmpty()) {
            throw new IllegalArgumentException("学期不能为空");
        }
        
        // 查找或创建学生
        Student student = studentService.findByStudentNo(dto.getStudentNo())
                .orElseGet(() -> studentService.createStudent(
                        dto.getStudentNo(),
                        dto.getName(),
                        dto.getClassName()
                ));
        
        Long studentId = student.getId();
        String semester = dto.getSemester();
        
        // 导入德育分数
        if (dto.getActivityScore() != null || dto.getMoralBonusScore() != null) {
            moralEducationService.createOrUpdateScore(
                    studentId,
                    semester,
                    dto.getActivityScore(),
                    dto.getMoralBonusScore()
            );
        }
        
        // 导入智育分数
        if (dto.getCourseScore() != null || dto.getIntellectualBonusScore() != null) {
            intellectualEducationService.createOrUpdateScore(
                    studentId,
                    semester,
                    dto.getCourseScore(),
                    dto.getIntellectualBonusScore()
            );
        }
        
        // 导入体育分数
        if (dto.getPhysicalRawScore() != null || dto.getPhysicalBonusScore() != null) {
            physicalEducationService.createOrUpdateScore(
                    studentId,
                    semester,
                    dto.getPhysicalRawScore(),
                    dto.getPhysicalPercentage(),
                    dto.getPhysicalBonusScore()
            );
        }
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
