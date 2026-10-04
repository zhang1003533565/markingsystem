package com.markingsystem.service;

import com.markingsystem.entity.IntellectualEducation;
import com.markingsystem.repository.IntellectualEducationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class IntellectualEducationService {

    @Autowired
    private IntellectualEducationRepository intellectualEducationRepository;

    public List<IntellectualEducation> findByStudentId(Long studentId) {
        return intellectualEducationRepository.findByStudentId(studentId);
    }

    public List<IntellectualEducation> findByStudentIdIn(List<Long> studentIds) {
        return studentIds == null || studentIds.isEmpty()
                ? List.of()
                : intellectualEducationRepository.findByStudentIdIn(studentIds);
    }

    public Optional<IntellectualEducation> findByStudentIdAndSemester(Long studentId, String semester) {
        return intellectualEducationRepository.findByStudentIdAndSemester(studentId, semester);
    }

    @Transactional
    public IntellectualEducation save(IntellectualEducation intellectualEducation) {
        intellectualEducation.calculateTotalScore();
        return intellectualEducationRepository.save(intellectualEducation);
    }

    @Transactional
    public IntellectualEducation createOrUpdateScore(Long studentId, String semester,
                                                      Double courseScore, Double bonusScore) {
        Optional<IntellectualEducation> existing = intellectualEducationRepository
                .findByStudentIdAndSemester(studentId, semester);

        IntellectualEducation intellectualEducation;
        if (existing.isPresent()) {
            intellectualEducation = existing.get();
            intellectualEducation.setCourseScore(courseScore);
            intellectualEducation.setBonusScore(bonusScore);
        } else {
            intellectualEducation = new IntellectualEducation();
            intellectualEducation.setStudentId(studentId);
            intellectualEducation.setSemester(semester);
            intellectualEducation.setCourseScore(courseScore);
            intellectualEducation.setBonusScore(bonusScore);
        }

        intellectualEducation.calculateTotalScore();
        return intellectualEducationRepository.save(intellectualEducation);
    }

    @Transactional
    public void deleteById(Long id) {
        intellectualEducationRepository.deleteById(id);
    }

    public boolean existsByStudentIdAndSemester(Long studentId, String semester) {
        return intellectualEducationRepository.existsByStudentIdAndSemester(studentId, semester);
    }
}
