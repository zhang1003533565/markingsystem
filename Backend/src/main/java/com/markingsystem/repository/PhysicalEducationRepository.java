package com.markingsystem.repository;

import com.markingsystem.entity.PhysicalEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhysicalEducationRepository extends JpaRepository<PhysicalEducation, Long> {
    
    List<PhysicalEducation> findByStudentId(Long studentId);

    List<PhysicalEducation> findByStudentIdIn(List<Long> studentIds);

    void deleteByStudentIdIn(List<Long> studentIds);
    
    Optional<PhysicalEducation> findByStudentIdAndSemester(Long studentId, String semester);
    
    boolean existsByStudentIdAndSemester(Long studentId, String semester);
}
