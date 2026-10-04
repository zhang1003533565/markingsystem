package com.markingsystem.repository;

import com.markingsystem.entity.IntellectualEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntellectualEducationRepository extends JpaRepository<IntellectualEducation, Long> {
    
    List<IntellectualEducation> findByStudentId(Long studentId);

    List<IntellectualEducation> findByStudentIdIn(List<Long> studentIds);

    void deleteByStudentIdIn(List<Long> studentIds);
    
    Optional<IntellectualEducation> findByStudentIdAndSemester(Long studentId, String semester);
    
    boolean existsByStudentIdAndSemester(Long studentId, String semester);
}
