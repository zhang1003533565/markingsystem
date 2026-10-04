package com.markingsystem.repository;

import com.markingsystem.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    
    Optional<Subject> findBySubjectCode(String subjectCode);
    
    Optional<Subject> findBySubjectName(String subjectName);
    
    boolean existsBySubjectCode(String subjectCode);
    
    boolean existsBySubjectName(String subjectName);
    
    List<Subject> findBySubjectType(String subjectType);
    
    List<Subject> findByStatus(String status);
    
    List<Subject> findBySubjectTypeAndStatus(String subjectType, String status);
}