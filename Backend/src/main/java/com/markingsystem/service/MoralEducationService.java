package com.markingsystem.service;

import com.markingsystem.entity.MoralEducation;
import com.markingsystem.repository.MoralEducationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MoralEducationService {
    
    @Autowired
    private MoralEducationRepository moralEducationRepository;
    
    public List<MoralEducation> findByStudentId(Long studentId) {
        return moralEducationRepository.findByStudentId(studentId);
    }

    public List<MoralEducation> findByStudentIdIn(List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return moralEducationRepository.findByStudentIdIn(studentIds);
    }
    
    public Optional<MoralEducation> findByStudentIdAndSemester(Long studentId, String semester) {
        return moralEducationRepository.findByStudentIdAndSemester(studentId, semester);
    }

    public Optional<MoralEducation> findByStudentIdAndAcademicYear(Long studentId, String academicYear) {
        return moralEducationRepository.findByStudentIdAndAcademicYear(studentId, academicYear);
    }

    public Optional<MoralEducation> findById(Long id) {
        return moralEducationRepository.findById(id);
    }

    public List<MoralEducation> findSecondClassRecordsByAcademicYear(String academicYear) {
        return moralEducationRepository.findSecondClassRecordsByAcademicYear(academicYear);
    }

    public List<MoralEducation> findSecondClassRecordsByAcademicYearAndTeacherId(String academicYear, Long teacherId) {
        return moralEducationRepository.findSecondClassRecordsByAcademicYearAndTeacherId(academicYear, teacherId);
    }
    
    @Transactional
    public MoralEducation save(MoralEducation moralEducation) {
        moralEducation.calculateTotalScore();
        return moralEducationRepository.save(moralEducation);
    }
    
    @Transactional
    public MoralEducation createOrUpdateScore(Long studentId, String semester, 
                                               Double activityScore, Double bonusScore) {
        Optional<MoralEducation> existing = moralEducationRepository
                .findByStudentIdAndSemester(studentId, semester);
        
        MoralEducation moralEducation;
        if (existing.isPresent()) {
            moralEducation = existing.get();
            moralEducation.setActivityScore(activityScore);
            moralEducation.setBonusScore(bonusScore);
        } else {
            moralEducation = new MoralEducation();
            moralEducation.setStudentId(studentId);
            moralEducation.setSemester(semester);
            moralEducation.setActivityScore(activityScore);
            moralEducation.setBonusScore(bonusScore);
        }
        
        moralEducation.calculateTotalScore();
        return moralEducationRepository.save(moralEducation);
    }
    
    @Transactional
    public void deleteById(Long id) {
        moralEducationRepository.deleteById(id);
    }
    
    public boolean existsByStudentIdAndSemester(Long studentId, String semester) {
        return moralEducationRepository.existsByStudentIdAndSemester(studentId, semester);
    }

    public boolean existsByStudentIdAndAcademicYear(Long studentId, String academicYear) {
        return moralEducationRepository.existsByStudentIdAndAcademicYear(studentId, academicYear);
    }

    @Transactional
    public MoralEducation createOrUpdateScoreByAcademicYear(Long studentId, String academicYear,
                                                            Double activityScore, Double bonusScore) {
        return createOrUpdateScoreByAcademicYear(studentId, academicYear, activityScore, bonusScore, null);
    }

    @Transactional
    public MoralEducation createOrUpdateScoreByAcademicYear(Long studentId, String academicYear,
                                                            Double activityScore, Double bonusScore,
                                                            Double secondClassScore) {
        Optional<MoralEducation> existing = moralEducationRepository
                .findByStudentIdAndAcademicYear(studentId, academicYear);

        MoralEducation moralEducation;
        if (existing.isPresent()) {
            moralEducation = existing.get();
            moralEducation.setActivityScore(activityScore);
            moralEducation.setBonusScore(bonusScore);
            if (secondClassScore != null) {
                moralEducation.setSecondClassScore(secondClassScore);
            }
        } else {
            moralEducation = new MoralEducation();
            moralEducation.setStudentId(studentId);
            moralEducation.setSemester(academicYear);
            moralEducation.setAcademicYear(academicYear);
            moralEducation.setActivityScore(activityScore);
            moralEducation.setBonusScore(bonusScore);
            moralEducation.setSecondClassScore(secondClassScore);
        }

        if (moralEducation.getAcademicYear() == null || moralEducation.getAcademicYear().isBlank()) {
            moralEducation.setAcademicYear(academicYear);
        }
        moralEducation.calculateTotalScore();
        return moralEducationRepository.save(moralEducation);
    }
}
