package com.markingsystem.controller;

import com.markingsystem.dto.ImportResultDTO;
import com.markingsystem.dto.ScoreResponseDTO;
import com.markingsystem.entity.IntellectualEducation;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.PhysicalEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private MoralEducationService moralEducationService;

    @Autowired
    private IntellectualEducationService intellectualEducationService;

    @Autowired
    private PhysicalEducationService physicalEducationService;

    @Autowired
    private ImportService importService;

    // 查询德育分
    @GetMapping("/moral/{studentId}")
    public ResponseEntity<List<MoralEducation>> getMoralScores(@PathVariable Long studentId) {
        return ResponseEntity.ok(moralEducationService.findByStudentId(studentId));
    }

    // 查询智育分
    @GetMapping("/intellectual/{studentId}")
    public ResponseEntity<List<IntellectualEducation>> getIntellectualScores(@PathVariable Long studentId) {
        return ResponseEntity.ok(intellectualEducationService.findByStudentId(studentId));
    }

    // 查询体育分
    @GetMapping("/physical/{studentId}")
    public ResponseEntity<List<PhysicalEducation>> getPhysicalScores(@PathVariable Long studentId) {
        return ResponseEntity.ok(physicalEducationService.findByStudentId(studentId));
    }

    // 查询学生所有分数
    @GetMapping("/student/{studentId}")
    public ResponseEntity<ScoreResponseDTO> getStudentAllScores(
            @PathVariable Long studentId,
            @RequestParam String semester) {

        Optional<Student> studentOpt = studentService.findById(studentId);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Student student = studentOpt.get();
        ScoreResponseDTO dto = new ScoreResponseDTO();
        dto.setStudentId(studentId);
        dto.setStudentNo(student.getStudentNo());
        dto.setName(student.getName());
        dto.setClassName(student.getClassName());
        dto.setSemester(semester);

        // 德育分数
        moralEducationService.findByStudentIdAndSemester(studentId, semester)
                .ifPresent(moral -> {
                    dto.setActivityScore(moral.getActivityScore());
                    dto.setMoralBonusScore(moral.getBonusScore());
                    dto.setMoralTotalScore(moral.getTotalScore());
                });

        // 智育分数
        intellectualEducationService.findByStudentIdAndSemester(studentId, semester)
                .ifPresent(intellectual -> {
                    dto.setCourseScore(intellectual.getCourseScore());
                    dto.setIntellectualBonusScore(intellectual.getBonusScore());
                    dto.setIntellectualTotalScore(intellectual.getTotalScore());
                });

        // 体育分数
        physicalEducationService.findByStudentIdAndSemester(studentId, semester)
                .ifPresent(physical -> {
                    dto.setPhysicalRawScore(physical.getRawScore());
                    dto.setPhysicalPercentage(physical.getPercentage());
                    dto.setPhysicalBonusScore(physical.getBonusScore());
                    dto.setPhysicalTotalScore(physical.getTotalScore());
                });

        return ResponseEntity.ok(dto);
    }

    // 批量查询所有学生分数
    @GetMapping("/all")
    public ResponseEntity<List<ScoreResponseDTO>> getAllStudentScores(@RequestParam String semester) {
        List<Student> students = studentService.findAll();
        List<ScoreResponseDTO> result = new ArrayList<>();
        List<Long> studentIds = students.stream().map(Student::getId).toList();
        Map<Long, MoralEducation> moralByStudent = moralEducationService.findByStudentIdIn(studentIds).stream()
                .filter(score -> semester.equals(score.getSemester()))
                .collect(Collectors.toMap(MoralEducation::getStudentId, Function.identity(), (left, right) -> right));
        Map<Long, IntellectualEducation> intellectualByStudent = intellectualEducationService.findByStudentIdIn(studentIds).stream()
                .filter(score -> semester.equals(score.getSemester()))
                .collect(Collectors.toMap(IntellectualEducation::getStudentId, Function.identity(), (left, right) -> right));
        Map<Long, PhysicalEducation> physicalByStudent = physicalEducationService.findByStudentIdIn(studentIds).stream()
                .filter(score -> semester.equals(score.getSemester()))
                .collect(Collectors.toMap(PhysicalEducation::getStudentId, Function.identity(), (left, right) -> right));

        for (Student student : students) {
            ScoreResponseDTO dto = new ScoreResponseDTO();
            dto.setStudentId(student.getId());
            dto.setStudentNo(student.getStudentNo());
            dto.setName(student.getName());
            dto.setClassName(student.getClassName());
            dto.setSemester(semester);

            // 德育分数
            Optional.ofNullable(moralByStudent.get(student.getId())).ifPresent(moral -> {
                        dto.setActivityScore(moral.getActivityScore());
                        dto.setMoralBonusScore(moral.getBonusScore());
                        dto.setMoralTotalScore(moral.getTotalScore());
                    });

            // 智育分数
            Optional.ofNullable(intellectualByStudent.get(student.getId())).ifPresent(intellectual -> {
                        dto.setCourseScore(intellectual.getCourseScore());
                        dto.setIntellectualBonusScore(intellectual.getBonusScore());
                        dto.setIntellectualTotalScore(intellectual.getTotalScore());
                    });

            // 体育分数
            Optional.ofNullable(physicalByStudent.get(student.getId())).ifPresent(physical -> {
                        dto.setPhysicalRawScore(physical.getRawScore());
                        dto.setPhysicalPercentage(physical.getPercentage());
                        dto.setPhysicalBonusScore(physical.getBonusScore());
                        dto.setPhysicalTotalScore(physical.getTotalScore());
                    });

            result.add(dto);
        }

        return ResponseEntity.ok(result);
    }

    // 导入分数数据
    @PostMapping("/import")
    public ResponseEntity<ImportResultDTO> importScores(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String fileType) {

        try {
            ImportResultDTO result;
            if ("excel".equalsIgnoreCase(fileType) || file.getOriginalFilename().endsWith(".xlsx")) {
                result = importService.importFromExcel(file);
            } else if ("csv".equalsIgnoreCase(fileType) || file.getOriginalFilename().endsWith(".csv")) {
                result = importService.importFromCsv(file);
            } else {
                return ResponseEntity.badRequest().body(
                        new ImportResultDTO(0, 0, List.of("不支持的文件类型，请上传 Excel 或 CSV 文件"))
                );
            }
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(
                    new ImportResultDTO(0, 0, List.of("文件读取失败: " + e.getMessage()))
            );
        }
    }
}
