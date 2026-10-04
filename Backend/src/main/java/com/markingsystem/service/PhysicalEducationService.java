package com.markingsystem.service;

import com.markingsystem.entity.PhysicalEducation;
import com.markingsystem.repository.PhysicalEducationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PhysicalEducationService {

    @Autowired
    private PhysicalEducationRepository physicalEducationRepository;

    public List<PhysicalEducation> findByStudentId(Long studentId) {
        return physicalEducationRepository.findByStudentId(studentId);
    }

    public List<PhysicalEducation> findByStudentIdIn(List<Long> studentIds) {
        return studentIds == null || studentIds.isEmpty()
                ? List.of()
                : physicalEducationRepository.findByStudentIdIn(studentIds);
    }

    public Optional<PhysicalEducation> findByStudentIdAndSemester(Long studentId, String semester) {
        return physicalEducationRepository.findByStudentIdAndSemester(studentId, semester);
    }

    @Transactional
    public PhysicalEducation save(PhysicalEducation physicalEducation) {
        physicalEducation.calculateTotalScore();
        return physicalEducationRepository.save(physicalEducation);
    }

    @Transactional
    public PhysicalEducation createOrUpdateScore(Long studentId, String semester,
                                                  Double rawScore, Double percentage, Double bonusScore) {
        Optional<PhysicalEducation> existing = physicalEducationRepository
                .findByStudentIdAndSemester(studentId, semester);

        PhysicalEducation physicalEducation;
        if (existing.isPresent()) {
            physicalEducation = existing.get();
            physicalEducation.setRawScore(rawScore);
            physicalEducation.setPercentage(percentage);
            physicalEducation.setBonusScore(bonusScore);
        } else {
            physicalEducation = new PhysicalEducation();
            physicalEducation.setStudentId(studentId);
            physicalEducation.setSemester(semester);
            physicalEducation.setRawScore(rawScore);
            physicalEducation.setPercentage(percentage);
            physicalEducation.setBonusScore(bonusScore);
        }

        physicalEducation.calculateTotalScore();
        return physicalEducationRepository.save(physicalEducation);
    }

    @Transactional
    public void deleteById(Long id) {
        physicalEducationRepository.deleteById(id);
    }

    public boolean existsByStudentIdAndSemester(Long studentId, String semester) {
        return physicalEducationRepository.existsByStudentIdAndSemester(studentId, semester);
    }
}
