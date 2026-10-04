package com.markingsystem.service;

import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.SecondClassScoreRule;
import com.markingsystem.entity.Student;
import com.markingsystem.util.RequestUserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecondClassScoreImportServiceTest {

    @Mock
    private SecondClassScoreRuleService secondClassScoreRuleService;

    @Mock
    private MoralEducationService moralEducationService;

    @InjectMocks
    private SecondClassScoreImportService secondClassScoreImportService;

    @Test
    void shouldUpdateSingleStudentScoreAndKeepBonusScore() {
        Student student = student(1L, 8L);
        MoralEducation record = record(student);
        SecondClassScoreRule rule = new SecondClassScoreRule();
        rule.setMinScore(0D);
        rule.setMaxScore(null);
        rule.setMoralScore(17D);

        when(moralEducationService.findById(10L)).thenReturn(Optional.of(record));
        when(secondClassScoreRuleService.findAll()).thenReturn(List.of(rule));
        when(moralEducationService.save(any(MoralEducation.class))).thenAnswer(invocation -> {
            MoralEducation saved = invocation.getArgument(0);
            saved.calculateTotalScore();
            return saved;
        });

        Map<String, Object> result = secondClassScoreImportService.updateImportedScore(
                10L,
                6.5D,
                new RequestUserContext(1L, "ADMIN", null, null, null)
        );

        assertEquals(6.5D, result.get("secondClassScore"));
        assertEquals(17D, result.get("moralScore"));
        assertEquals(3D, result.get("bonusScore"));
        assertEquals(20D, result.get("totalScore"));
    }

    @Test
    void shouldRejectTeacherUpdatingAnotherTeachersStudent() {
        MoralEducation record = record(student(1L, 8L));
        when(moralEducationService.findById(10L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class, () -> secondClassScoreImportService.updateImportedScore(
                10L,
                6.5D,
                new RequestUserContext(2L, "TEACHER", 9L, null, null)
        ));
        verifyNoInteractions(secondClassScoreRuleService);
    }

    private Student student(Long id, Long teacherId) {
        Student student = new Student();
        student.setId(id);
        student.setStudentNo("20233480102");
        student.setName("测试学生");
        student.setClassName("大数据231班");
        student.setTeacherId(teacherId);
        return student;
    }

    private MoralEducation record(Student student) {
        MoralEducation record = new MoralEducation();
        record.setId(10L);
        record.setStudentId(student.getId());
        record.setStudent(student);
        record.setAcademicYear("2024-2025");
        record.setSemester("2024-2025");
        record.setSecondClassScore(2D);
        record.setActivityScore(10D);
        record.setBonusScore(3D);
        record.calculateTotalScore();
        return record;
    }
}
